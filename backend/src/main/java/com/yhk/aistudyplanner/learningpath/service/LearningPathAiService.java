package com.yhk.aistudyplanner.learningpath.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.PlanningGateway;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.common.exception.*;
import com.yhk.aistudyplanner.learningpath.dto.*;
import com.yhk.aistudyplanner.learningpath.vo.LearningPathDraftView;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LearningPathAiService {
    private static final Logger LOGGER=LoggerFactory.getLogger(LearningPathAiService.class);
    private final AiPlanningProperties properties; private final ObjectProvider<PlanningGateway> gateways;
    private final LearningPathPromptService prompts; private final ObjectMapper mapper;
    private final SubjectService subjects; private final com.yhk.aistudyplanner.auth.service.AuthSessionService session; private final Clock clock;
    public LearningPathAiService(AiPlanningProperties properties,ObjectProvider<PlanningGateway> gateways,
            LearningPathPromptService prompts,ObjectMapper mapper,SubjectService subjects,
            com.yhk.aistudyplanner.auth.service.AuthSessionService session,Clock clock){
        this.properties=properties;this.gateways=gateways;this.prompts=prompts;this.mapper=mapper;this.subjects=subjects;this.session=session;this.clock=clock;
    }
    public LearningPathDraftView generate(LearningPathGenerateRequest request){
        LocalDate today=LocalDate.now(clock); LocalDate start=request.startDate()==null?today:request.startDate();
        if(start.isBefore(today))throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
        if(request.targetDate()!=null&&start.isAfter(request.targetDate()))throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        validateDuration(start,request.targetDate());
        if(request.subjectId()!=null)subjects.requireOwned(request.subjectId(),session.currentUserId());
        PlanningGateway gateway=gateways.getIfAvailable(); if(!properties.configured()||gateway==null)throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        String userPrompt=prompts.user(request,today,clock.getZone().getId());
        try{return generateValidated(gateway,prompts.system(),userPrompt,request,start);}
        catch(BusinessException exception){
            if(exception.getErrorCode()!=ErrorCode.LEARNING_PATH_INVALID&&exception.getErrorCode()!=ErrorCode.AI_RESPONSE_INVALID)throw exception;
            return generateValidated(gateway,prompts.retrySystem(),userPrompt,request,start);
        }
    }
    private LearningPathDraftView generateValidated(PlanningGateway gateway,String systemPrompt,String userPrompt,
            LearningPathGenerateRequest request,LocalDate start){
        String raw;
        try{raw=gateway.generate(systemPrompt,userPrompt);}
        catch(AiProviderException exception){throw new BusinessException(exception.code());}
        return validate(request,start,parse(raw));
    }
    LearningPathModelResponse parse(String raw){
        if(!StringUtils.hasText(raw))throw new BusinessException(ErrorCode.AI_RESPONSE_EMPTY);
        String json=raw.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","").trim();
        int start=json.indexOf('{'),end=json.lastIndexOf('}');
        if(start<0){logParseFailure(raw,json,start,end,new IllegalArgumentException("JSON object start not found"));throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);}
        String candidate=end>=start?json.substring(start,end+1):json.substring(start);
        try{return mapper.readValue(candidate,LearningPathModelResponse.class);}
        catch(Exception exception){logParseFailure(raw,json,start,end,exception);throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);}
    }
    private void logParseFailure(String raw,String normalized,int firstBrace,int lastBrace,Exception exception){
        Throwable diagnostic=deepestJsonCause(exception);
        JsonLocation location=diagnostic instanceof JsonProcessingException processing?processing.getLocation():null;
        String targetType="-",path="-";
        if(exception instanceof MismatchedInputException mismatch){
            if(mismatch.getTargetType()!=null)targetType=mismatch.getTargetType().getTypeName();
            String mappedPath=mismatch.getPath().stream().map(reference->{
                if(reference.getFieldName()!=null)return reference.getFieldName();
                return reference.getIndex()>=0?"["+reference.getIndex()+"]":"?";
            }).reduce((left,right)->left+"."+right).orElse("");
            if(StringUtils.hasText(mappedPath))path=mappedPath;
        }
        LOGGER.warn("Learning Path JSON parse failed: length={}, startsWithObject={}, endsWithObject={}, firstBrace={}, lastBrace={}, exception={}, message={}, line={}, column={}, targetType={}, path={}",
                raw.length(),normalized.startsWith("{"),normalized.endsWith("}"),firstBrace,lastBrace,
                diagnostic.getClass().getSimpleName(),safeMessage(diagnostic),location==null?-1:location.getLineNr(),
                location==null?-1:location.getColumnNr(),targetType,path);
    }
    private Throwable deepestJsonCause(Throwable exception){
        Throwable diagnostic=exception;
        while(diagnostic.getCause() instanceof JsonProcessingException)diagnostic=diagnostic.getCause();
        return diagnostic;
    }
    private String safeMessage(Throwable exception){
        String message=exception instanceof JsonProcessingException processing?processing.getOriginalMessage():exception.getMessage();
        if(!StringUtils.hasText(message))return "-";
        String safe=message.replaceAll("\\\"(?:\\\\.|[^\\\"\\\\])*\\\"","\"<redacted>\"").replaceAll("\\s+"," ").trim();
        int source=safe.indexOf(" at [Source");if(source>=0)safe=safe.substring(0,source);
        return safe.length()>180?safe.substring(0,180):safe;
    }
    LearningPathDraftView validate(LearningPathGenerateRequest request,LocalDate start,LearningPathModelResponse model){
        if(model==null||!text(model.title(),200)||model.stages()==null||model.stages().isEmpty()||model.stages().size()>20)invalid();
        List<LearningPathDraftView.StageView> stages=new ArrayList<>();Set<Integer> stageNos=new HashSet<>(),seqs=new HashSet<>();
        Set<String> topics=new HashSet<>();Map<Integer,Integer> minutesByDay=new HashMap<>();long total=0;int expected=1,expectedStage=1,itemCount=0;
        Integer durationDays=durationDays(start,request.targetDate());
        List<LearningPathQualityValidator.Node> qualityNodes=new ArrayList<>();
        for(var stage:model.stages()){
            if(stage==null||stage.stageNo()==null||stage.stageNo()!=expectedStage++||!stageNos.add(stage.stageNo())||!text(stage.title(),200)||stage.items()==null||stage.items().isEmpty())invalid();
            List<LearningPathDraftView.ItemView> items=new ArrayList<>();
            for(var item:stage.items()){
                if(++itemCount>10)invalid();
                if(item==null||item.sequenceNo()==null||item.sequenceNo()!=expected++||!seqs.add(item.sequenceNo())||!text(item.topic(),200)||!text(item.learningObjective(),1000)
                        ||item.estimatedMinutes()==null||item.estimatedMinutes()<15||item.estimatedMinutes()>1440||item.suggestedDay()!=null&&item.suggestedDay()<1)invalid();
                if(!topics.add(normalizeTopic(item.topic())))invalid();
                validateSchedule(item.suggestedDay(),item.estimatedMinutes(),durationDays,request.dailyMinutes(),minutesByDay);
                List<String> methods=clean(item.learningMethod()),criteria=clean(item.completionCriteria());
                LearningPathQualityValidator.validateContent(item.learningObjective(),methods,criteria);
                total+=item.estimatedMinutes();if(total>60000)invalid();
                qualityNodes.add(new LearningPathQualityValidator.Node(item.sequenceNo(),item.topic(),item.suggestedDay(),item.prerequisite()));
                items.add(new LearningPathDraftView.ItemView(item.sequenceNo(),item.topic().trim(),item.learningObjective().trim(),methods,criteria,item.estimatedMinutes(),item.suggestedDay(),trim(item.prerequisite()),trim(item.reason())));
            }
            stages.add(new LearningPathDraftView.StageView(stage.stageNo(),stage.title().trim(),trim(stage.description()),List.copyOf(items)));
        }
        LearningPathQualityValidator.validateOrder(qualityNodes);
        validateCapacity(total,durationDays,request.dailyMinutes());
        return new LearningPathDraftView(model.title().trim(),trim(model.summary()),request.subjectId(),request.requirement().trim(),trim(request.currentLevel()),trim(request.targetLevel()),request.dailyMinutes(),start,request.targetDate(),trim(request.preferences()),Math.toIntExact(total),List.copyOf(stages));
    }
    private List<String> clean(List<String> values){if(values==null||values.size()>10)return List.of();return values.stream().filter(StringUtils::hasText).map(String::trim).filter(v->v.length()<=300).distinct().toList();}
    private boolean text(String v,int max){return StringUtils.hasText(v)&&v.trim().length()<=max;}
    private Integer durationDays(LocalDate start,LocalDate target){return target==null?null:Math.toIntExact(ChronoUnit.DAYS.between(start,target)+1);}
    private void validateDuration(LocalDate start,LocalDate target){Integer days=durationDays(start,target);if(days!=null&&days>10)throw new BusinessException(ErrorCode.LEARNING_PATH_DURATION_EXCEEDED);}
    private void validateSchedule(Integer day,int minutes,Integer durationDays,Integer dailyMinutes,Map<Integer,Integer> minutesByDay){
        if(durationDays!=null&&(day==null||day>durationDays))invalid();
        if(day!=null&&dailyMinutes!=null&&minutesByDay.merge(day,minutes,Integer::sum)>dailyMinutes)invalid();
    }
    private void validateCapacity(long total,Integer durationDays,Integer dailyMinutes){if(durationDays!=null&&dailyMinutes!=null&&total>(long)durationDays*dailyMinutes)invalid();}
    private String normalizeTopic(String topic){return topic.trim().replaceAll("\\s+","").toLowerCase(Locale.ROOT);}
    private String trim(String v){return StringUtils.hasText(v)?v.trim():null;}
    private void invalid(){throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);}
}
