package com.yhk.aistudyplanner.learningpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.*;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.learningpath.dto.*;
import com.yhk.aistudyplanner.learningpath.entity.*;
import com.yhk.aistudyplanner.learningpath.mapper.*;
import com.yhk.aistudyplanner.learningpath.vo.*;
import com.yhk.aistudyplanner.subject.entity.Subject;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.dto.TaskCreateRequest;
import com.yhk.aistudyplanner.task.entity.*;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningPathService {
    private final LearningPathMapper pathMapper;
    private final LearningPathItemMapper itemMapper;
    private final TaskService taskService;
    private final SubjectService subjectService;
    private final AuthSessionService session;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LearningPathService(LearningPathMapper pathMapper, LearningPathItemMapper itemMapper,
            TaskService taskService, SubjectService subjectService, AuthSessionService session,
            ObjectMapper objectMapper, Clock clock) {
        this.pathMapper=pathMapper; this.itemMapper=itemMapper; this.taskService=taskService;
        this.subjectService=subjectService; this.session=session; this.objectMapper=objectMapper; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public PageResponse<LearningPathListView> list(long page,long pageSize){
        long userId=session.currentUserId();
        var result=pathMapper.selectPageView(new Page<>(page,pageSize),userId);
        return new PageResponse<>(result.getRecords(),page,pageSize,result.getTotal());
    }

    @Transactional(readOnly=true)
    public LearningPathDetailView get(long id){long userId=session.currentUserId();return detail(requireOwned(id,userId),userId);}

    @Transactional
    public LearningPathDetailView confirm(LearningPathConfirmRequest request){
        long userId=session.currentUserId(); validateDates(request.startDate(),request.targetDate());
        Subject subject=request.subjectId()==null?null:subjectService.requireOwned(request.subjectId(),userId);
        validateItems(request.items(),request.startDate(),request.targetDate(),request.dailyMinutes()); LocalDateTime now=LocalDateTime.now(clock);
        StudyPath path=new StudyPath(); path.setUserId(userId); path.setSubjectId(request.subjectId());
        path.setTitle(request.title().trim()); path.setSummary(trim(request.summary()));
        path.setOriginalRequirement(request.originalRequirement().trim()); path.setGoal(trim(request.goal()));
        path.setCurrentLevel(trim(request.currentLevel())); path.setTargetLevel(trim(request.targetLevel()));
        path.setPurpose(trim(request.purpose())); path.setPreferences(trim(request.preferences()));
        path.setStartDate(request.startDate()); path.setTargetDate(request.targetDate()); path.setDailyMinutes(request.dailyMinutes());
        path.setStatus(LearningPathStatus.ACTIVE); path.setCreatedAt(now); path.setUpdatedAt(now); pathMapper.insert(path);
        for(var source:request.items().stream().sorted(Comparator.comparingInt(LearningPathConfirmRequest.Item::sequenceNo)).toList()){
            StudyPathItem item=new StudyPathItem(); item.setUserId(userId); item.setPathId(path.getId());
            item.setSequenceNo(source.sequenceNo()); item.setStageNo(source.stageNo()); item.setStageTitle(source.stageTitle().trim());
            item.setStageDescription(trim(source.stageDescription())); item.setTopic(source.topic().trim());
            item.setLearningObjective(source.learningObjective().trim()); item.setLearningMethod(json(source.learningMethod()));
            item.setCompletionCriteria(json(source.completionCriteria())); item.setEstimatedMinutes(source.estimatedMinutes());
            item.setSuggestedDay(source.suggestedDay()); item.setPrerequisiteText(trim(source.prerequisite())); item.setReason(trim(source.reason()));
            item.setStatus(LearningPathItemStatus.PENDING); item.setCreatedAt(now); item.setUpdatedAt(now); itemMapper.insert(item);
        }
        return detail(path,userId);
    }

    @Transactional
    public LearningPathTaskView createTask(long pathId,long itemId){
        long userId=session.currentUserId(); StudyPath path=lockOwned(pathId,userId);
        if(path.getStatus()==LearningPathStatus.CANCELLED) throw new BusinessException(ErrorCode.LEARNING_PATH_CANCELLED);
        if(path.getSubjectId()==null) throw new BusinessException(ErrorCode.LEARNING_PATH_SUBJECT_REQUIRED);
        subjectService.requireOwned(path.getSubjectId(),userId);
        StudyPathItem item=itemMapper.selectOwnedForUpdate(pathId,itemId,userId);
        if(item==null) throw new BusinessException(ErrorCode.LEARNING_PATH_ITEM_NOT_FOUND);
        if(item.getTaskId()!=null){
            TaskView existing=taskService.get(item.getTaskId());
            return new LearningPathTaskView(pathId,itemId,existing.id(),existing.title(),false,"已关联学习任务");
        }
        LocalDate planned=item.getSuggestedDay()==null?null:path.getStartDate().plusDays(item.getSuggestedDay()-1L);
        LocalDateTime now=LocalDateTime.now(clock);
        TaskView task=taskService.create(new TaskCreateRequest(path.getSubjectId(),null,item.getTopic(),
                item.getLearningObjective(),2,item.getEstimatedMinutes(),planned,null));
        try{
            int updated=itemMapper.update(null,new LambdaUpdateWrapper<StudyPathItem>()
                    .eq(StudyPathItem::getId,itemId).eq(StudyPathItem::getPathId,pathId).eq(StudyPathItem::getUserId,userId)
                    .isNull(StudyPathItem::getTaskId).set(StudyPathItem::getTaskId,task.id()).set(StudyPathItem::getUpdatedAt,now));
            if(updated!=1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }catch(DuplicateKeyException ex){throw new BusinessException(ErrorCode.DATA_CONFLICT);}
        return new LearningPathTaskView(pathId,itemId,task.id(),task.title(),true,"任务已创建，可进入每日计划");
    }

    @Transactional
    public LearningPathDetailView changeItemStatus(long pathId,long itemId,LearningPathItemStatusRequest request){
        long userId=session.currentUserId(); StudyPath path=lockOwned(pathId,userId);
        StudyPathItem item=itemMapper.selectOwnedForUpdate(pathId,itemId,userId);
        if(item==null) throw new BusinessException(ErrorCode.LEARNING_PATH_ITEM_NOT_FOUND);
        if(item.getTaskId()!=null) throw new BusinessException(ErrorCode.LEARNING_PATH_ITEM_LINKED);
        int updated=itemMapper.update(null,new LambdaUpdateWrapper<StudyPathItem>().eq(StudyPathItem::getId,itemId)
                .eq(StudyPathItem::getPathId,pathId).eq(StudyPathItem::getUserId,userId)
                .set(StudyPathItem::getStatus,request.status()).set(StudyPathItem::getUpdatedAt,LocalDateTime.now(clock)));
        if(updated!=1) throw new BusinessException(ErrorCode.LEARNING_PATH_ITEM_NOT_FOUND);
        return detail(path,userId);
    }

    @Transactional
    public LearningPathDetailView changeStatus(long pathId,LearningPathStatusRequest request){
        long userId=session.currentUserId(); StudyPath path=lockOwned(pathId,userId);
        if(request.status()==LearningPathStatus.COMPLETED) throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);
        int updated=pathMapper.update(null,new LambdaUpdateWrapper<StudyPath>().eq(StudyPath::getId,pathId)
                .eq(StudyPath::getUserId,userId).set(StudyPath::getStatus,request.status())
                .set(StudyPath::getUpdatedAt,LocalDateTime.now(clock)));
        if(updated!=1)throw new BusinessException(ErrorCode.LEARNING_PATH_NOT_FOUND);
        path.setStatus(request.status());return detail(path,userId);
    }

    private void validateDates(LocalDate start,LocalDate target){
        if(start.isBefore(LocalDate.now(clock))) throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
        if(target!=null&&start.isAfter(target)) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
    }
    private void validateItems(List<LearningPathConfirmRequest.Item> items,LocalDate start,LocalDate target,Integer dailyMinutes){
        if(items.size()>100) throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);
        Set<Integer> sequences=new HashSet<>();Set<String> topics=new HashSet<>();Map<Integer,Integer> minutesByDay=new HashMap<>();long total=0;
        Integer durationDays=target==null?null:Math.toIntExact(ChronoUnit.DAYS.between(start,target)+1);
        List<LearningPathConfirmRequest.Item> ordered=items.stream().sorted(Comparator.comparingInt(LearningPathConfirmRequest.Item::sequenceNo)).toList();
        int expectedStage=1;Integer previousStage=null;
        List<LearningPathQualityValidator.Node> qualityNodes=new ArrayList<>();
        for(var item:ordered){
            if(!sequences.add(item.sequenceNo())||!topics.add(item.topic().trim().replaceAll("\\s+","").toLowerCase(Locale.ROOT)))invalid();
            if(previousStage==null||!previousStage.equals(item.stageNo())){if(item.stageNo()!=expectedStage++)invalid();previousStage=item.stageNo();}
            if(durationDays!=null&&(item.suggestedDay()==null||item.suggestedDay()>durationDays))invalid();
            if(item.suggestedDay()!=null&&dailyMinutes!=null&&minutesByDay.merge(item.suggestedDay(),item.estimatedMinutes(),Integer::sum)>dailyMinutes)invalid();
            LearningPathQualityValidator.validateContent(item.learningObjective(),item.learningMethod(),item.completionCriteria());
            qualityNodes.add(new LearningPathQualityValidator.Node(item.sequenceNo(),item.topic(),item.suggestedDay(),item.prerequisite()));
            total+=item.estimatedMinutes();
        }
        LearningPathQualityValidator.validateOrder(qualityNodes);
        if(total>60000) throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);
        if(durationDays!=null&&dailyMinutes!=null&&total>(long)durationDays*dailyMinutes)invalid();
        List<Integer> sorted=sequences.stream().sorted().toList(); for(int i=0;i<sorted.size();i++)if(sorted.get(i)!=i+1)throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);
    }
    private void invalid(){throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);}
    public StudyPath requireOwned(long id,long userId){
        StudyPath path=pathMapper.selectOne(new LambdaQueryWrapper<StudyPath>().eq(StudyPath::getId,id).eq(StudyPath::getUserId,userId));
        if(path!=null)return path; if(pathMapper.selectById(id)!=null)throw new BusinessException(ErrorCode.LEARNING_PATH_ACCESS_DENIED);
        throw new BusinessException(ErrorCode.LEARNING_PATH_NOT_FOUND);
    }
    private StudyPath lockOwned(long id,long userId){StudyPath path=pathMapper.selectOwnedForUpdate(id,userId);if(path!=null)return path;return requireOwned(id,userId);}
    private LearningPathDetailView detail(StudyPath path,long userId){
        Subject subject=path.getSubjectId()==null?null:subjectService.requireOwned(path.getSubjectId(),userId);
        List<LearningPathItemView> items=itemMapper.selectRows(path.getId(),userId).stream().map(this::view).toList();
        int completed=(int)items.stream().filter(i->"COMPLETED".equals(i.effectiveStatus())).count();
        int skipped=(int)items.stream().filter(i->"SKIPPED".equals(i.effectiveStatus())).count();
        int stages=(int)items.stream().map(LearningPathItemView::stageNo).distinct().count();
        double progress=items.isEmpty()?0:Math.round(completed*10000.0/items.size())/100.0;
        LearningPathStatus effectiveStatus=path.getStatus()==LearningPathStatus.ACTIVE&&!items.isEmpty()&&completed==items.size()?LearningPathStatus.COMPLETED:path.getStatus();
        return new LearningPathDetailView(path.getId(),path.getSubjectId(),subject==null?null:subject.getName(),subject==null?null:subject.getColor(),
                path.getTitle(),path.getSummary(),path.getOriginalRequirement(),path.getGoal(),path.getCurrentLevel(),path.getTargetLevel(),path.getPurpose(),path.getPreferences(),
                path.getStartDate(),path.getTargetDate(),path.getDailyMinutes(),effectiveStatus,stages,items.size(),completed,skipped,progress,path.getCreatedAt(),path.getUpdatedAt(),items);
    }
    private LearningPathItemView view(LearningPathItemMapper.ItemRow row){
        String effective=row.taskId()!=null?("COMPLETED".equals(row.taskStatus())?"COMPLETED":"PENDING"):row.status();
        return new LearningPathItemView(row.id(),row.sequenceNo(),row.stageNo(),row.stageTitle(),row.stageDescription(),row.topic(),row.learningObjective(),
                list(row.learningMethod()),list(row.completionCriteria()),row.estimatedMinutes(),row.suggestedDay(),row.prerequisiteText(),row.reason(),
                LearningPathItemStatus.valueOf(row.status()),effective,row.taskId(),row.taskTitle(),row.taskStatus()==null?null:TaskStatus.valueOf(row.taskStatus()));
    }
    private String json(List<String> values){try{return objectMapper.writeValueAsString(values.stream().map(String::trim).toList());}catch(Exception e){throw new IllegalStateException(e);}}
    private List<String> list(String json){try{return objectMapper.readValue(json,new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
}
