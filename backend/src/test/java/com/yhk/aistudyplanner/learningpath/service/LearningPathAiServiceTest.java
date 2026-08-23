package com.yhk.aistudyplanner.learningpath.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.PlanningGateway;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import java.time.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class LearningPathAiServiceTest {
    private AiPlanningProperties properties; private PlanningGateway gateway; private LearningPathAiService service;
    private final LocalDate today=LocalDate.of(2026,8,23);
    @BeforeEach void setup(){
        properties=new AiPlanningProperties();properties.setEnabled(true);properties.setApiKey("test-key");gateway=mock(PlanningGateway.class);
        var factory=new StaticListableBeanFactory();factory.addBean("gateway",gateway);ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        service=new LearningPathAiService(properties,factory.getBeanProvider(PlanningGateway.class),new LearningPathPromptService(mapper),mapper,
                mock(SubjectService.class),mock(AuthSessionService.class),Clock.fixed(today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")));
    }
    @Test void parsesCompleteJavaLearningPathWithoutLosingFields(){when(gateway.generate(anyString(),anyString())).thenReturn(JavaLearningPathFixture.modelJson());var result=service.generate(request());assertEquals("Java后端面试学习路径",result.title());assertEquals(4,result.stages().size());var hashMap=result.stages().get(1).items().get(0);assertEquals("HashMap",hashMap.topic());assertTrue(hashMap.learningObjective().contains("put/get"));assertEquals(4,hashMap.learningMethod().size());assertEquals(3,hashMap.completionCriteria().size());assertEquals(120,hashMap.estimatedMinutes());assertEquals(3,hashMap.suggestedDay());assertEquals("面向对象",hashMap.prerequisite());assertTrue(hashMap.reason().contains("面试"));}
    @Test void validFirstResponseCallsAiOnlyOnce(){when(gateway.generate(anyString(),anyString())).thenReturn(valid());assertEquals("Java基础路线",service.generate(request()).title());verify(gateway,times(1)).generate(anyString(),anyString());}
    @Test void tenDayWindowIsAllowed(){when(gateway.generate(anyString(),anyString())).thenReturn(pathWithNodes(6,10));var result=service.generate(request());assertEquals(6,result.stages().get(0).items().size());verify(gateway,times(1)).generate(anyString(),anyString());}
    @Test void elevenDayWindowIsRejectedBeforeCallingAi(){
        var request=new LearningPathGenerateRequest("复习Java",null,"基础一般","面试",120,today,LocalDate.of(2026,9,2),"轻松");
        BusinessException error=assertThrows(BusinessException.class,()->service.generate(request));
        assertEquals(ErrorCode.LEARNING_PATH_DURATION_EXCEEDED,error.getErrorCode());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,error.getErrorCode().status());
        verifyNoInteractions(gateway);
    }
    @Test void missingTargetDateKeepsOpenEndedPathSemantics(){
        var request=new LearningPathGenerateRequest("复习Java",null,"基础一般","面试",120,today,null,"轻松");
        when(gateway.generate(anyString(),anyString())).thenReturn(valid());
        assertEquals("Java基础路线",service.generate(request).title());
        verify(gateway,times(1)).generate(anyString(),anyString());
    }
    @Test void threeDayJavaCollectionsPathStillPasses(){
        var request=new LearningPathGenerateRequest("3天复习Java集合",null,"基础一般","掌握集合",120,today,today.plusDays(2),"循序渐进");
        when(gateway.generate(anyString(),anyString())).thenReturn(pathWithNodes(3,3));
        assertEquals(3,service.generate(request).stages().get(0).items().size());
    }
    @Test void aiResponseCannotExceedTenCoreNodes(){
        when(gateway.generate(anyString(),anyString())).thenReturn(pathWithNodes(11,10));
        BusinessException error=assertThrows(BusinessException.class,()->service.generate(request()));
        assertEquals(ErrorCode.LEARNING_PATH_INVALID,error.getErrorCode());
    }
    @Test void invalidFirstResponseRetriesOnceWithConstraintReminder(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("\"estimatedMinutes\":60","\"estimatedMinutes\":0"),valid());assertEquals("Java基础路线",service.generate(request()).title());var systems=ArgumentCaptor.forClass(String.class);verify(gateway,times(2)).generate(systems.capture(),anyString());String retry=systems.getAllValues().get(1);assertAll(()->assertTrue(retry.contains("上一份结果没有通过系统业务约束")),()->assertTrue(retry.contains("stageNo必须从1开始连续")),()->assertTrue(retry.contains("sequenceNo必须在全路径从1开始连续")),()->assertTrue(retry.contains("topic不得重复")),()->assertTrue(retry.contains("suggestedDay必须非递减")),()->assertTrue(retry.contains("不得超过dailyMinutes")),()->assertTrue(retry.contains("总周期容量")),()->assertTrue(retry.contains("learningMethod必须具体且最多3条")),()->assertTrue(retry.contains("completionCriteria必须可验证且最多3条")),()->assertTrue(retry.contains("prerequisite不得依赖当前或未来节点")));}
    @Test void truncatedResponseRetriesOnceWithCompactEightNodePrompt(){
        String truncated=valid().substring(0,valid().length()-8);
        when(gateway.generate(anyString(),anyString())).thenReturn(truncated,valid());
        assertEquals("Java基础路线",service.generate(request()).title());
        var systems=ArgumentCaptor.forClass(String.class);
        verify(gateway,times(2)).generate(systems.capture(),anyString());
        assertTrue(systems.getAllValues().get(1).contains("最多8个核心节点"));
    }
    @Test void twoInvalidResponsesReturnLearningPathInvalidAfterOneRetry(){String invalid=valid().replace("\"estimatedMinutes\":60","\"estimatedMinutes\":0");when(gateway.generate(anyString(),anyString())).thenReturn(invalid);BusinessException error=assertThrows(BusinessException.class,()->service.generate(request()));assertEquals(ErrorCode.LEARNING_PATH_INVALID,error.getErrorCode());verify(gateway,times(2)).generate(anyString(),anyString());}
    @Test void rejectsEmptyStages(){when(gateway.generate(anyString(),anyString())).thenReturn("{\"title\":\"Java\",\"stages\":[]}");assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsEmptyItems(){when(gateway.generate(anyString(),anyString())).thenReturn("{\"title\":\"Java\",\"stages\":[{\"stageNo\":1,\"title\":\"基础\",\"items\":[]}]}");assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsDuplicateSequence(){String raw=valid().replace("}]}","},{\"sequenceNo\":1,\"topic\":\"集合\",\"learningObjective\":\"掌握集合\",\"learningMethod\":[\"练习\"],\"completionCriteria\":[\"能解释\"],\"estimatedMinutes\":60,\"suggestedDay\":2}]}]}");when(gateway.generate(anyString(),anyString())).thenReturn(raw);assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsInvalidMinutes(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("\"estimatedMinutes\":60","\"estimatedMinutes\":0"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsDuplicateTopics(){when(gateway.generate(anyString(),anyString())).thenReturn(twoItems("面向对象",1,60));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsSuggestedDayBeyondRequestedPeriod(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("\"suggestedDay\":1","\"suggestedDay\":22"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsDailyWorkloadBeyondAvailableMinutes(){when(gateway.generate(anyString(),anyString())).thenReturn(twoItems("集合",1,90));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsTotalCapacityBeyondRequestedPeriod(){var oneDay=new LearningPathGenerateRequest("复习Java",null,"基础一般","面试",120,today,today,"循序渐进");when(gateway.generate(anyString(),anyString())).thenReturn(twoItems("HashMap",1,61));assertThrows(BusinessException.class,()->service.generate(oneDay));}
    @Test void rejectsNonContinuousStages(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("\"stageNo\":1","\"stageNo\":2"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsMissingLearningObjective(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("理解封装继承多态的核心概念",""));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsGenericLearningObjective(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("理解封装继承多态的核心概念","掌握OOP"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsMissingLearningMethod(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("[\"阅读面向对象核心概念\",\"运行继承示例代码\"]","[]"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsOnlyGenericLearningMethods(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("[\"阅读面向对象核心概念\",\"运行继承示例代码\"]","[\"学习\",\"练习\",\"复习\"]"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsMissingCompletionCriteria(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("[\"能脱稿解释封装继承和多态的区别\"]","[]"));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsFutureTopicAsPrerequisite(){String raw=twoItems("HashMap",2,60).replace("\"prerequisite\":null","\"prerequisite\":\"HashMap\"");when(gateway.generate(anyString(),anyString())).thenReturn(raw);assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsCurrentTopicAsItsOwnPrerequisite(){when(gateway.generate(anyString(),anyString())).thenReturn(valid().replace("\"prerequisite\":null","\"prerequisite\":\"面向对象\""));assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void rejectsSuggestedDayMovingBackwards(){String raw=twoItems("HashMap",1,60).replaceFirst("\"suggestedDay\":1","\"suggestedDay\":2");when(gateway.generate(anyString(),anyString())).thenReturn(raw);assertThrows(BusinessException.class,()->service.generate(request()));}
    @Test void promptClearlySeparatesLearningPathFromDailySchedule(){String system=new LearningPathPromptService(new ObjectMapper()).system();assertTrue(system.contains("学什么"));assertTrue(system.contains("Daily Plan"));assertTrue(system.contains("禁止安排09:00"));assertTrue(system.contains("不返回subjectId"));}
    @Test void promptLimitsVerboseNodeContentWithoutChangingSchema(){
        String system=new LearningPathPromptService(new ObjectMapper()).system();
        assertAll(
                ()->assertTrue(system.contains("learningObjective使用1至2个简洁句子")),
                ()->assertTrue(system.contains("learningMethod最多3条")),
                ()->assertTrue(system.contains("completionCriteria最多3条")),
                ()->assertTrue(system.contains("reason最多1个简短句子")),
                ()->assertTrue(system.contains("否则必须返回null")),
                ()->assertTrue(system.contains("不得输出长篇解释、教程、教材内容或背景知识")),
                ()->assertTrue(system.contains("保持上述JSON字段和结构不变")));
    }
    @Test void promptUsesCoreNodeRangesAndDoesNotForceCapacityFilling(){
        String system=new LearningPathPromptService(new ObjectMapper()).system();
        assertAll(
                ()->assertTrue(system.contains("1至3天通常生成3至6个核心节点")),
                ()->assertTrue(system.contains("4至7天通常生成4至8个核心节点")),
                ()->assertTrue(system.contains("8至10天通常生成6至10个核心节点")),
                ()->assertTrue(system.contains("全路径最多10个核心知识节点")),
                ()->assertTrue(system.contains("不要为了让每天都有内容或填满每天的学习时间强行创建知识点")),
                ()->assertTrue(system.contains("允许suggestedDay跳跃")),
                ()->assertTrue(system.contains("suggestedDay不得超过周期自然日数")),
                ()->assertTrue(system.contains("全路径总时长不得超过周期容量")));
    }
    @Test void truncatedJsonReturns50211AndLogsOnlySafeTruncationDiagnostics(){
        Logger logger=(Logger)LoggerFactory.getLogger(LearningPathAiService.class);ListAppender<ILoggingEvent> appender=new ListAppender<>();appender.start();logger.addAppender(appender);
        try{String truncated=valid().substring(0,valid().length()-8);BusinessException error=assertThrows(BusinessException.class,()->service.parse(truncated));assertEquals(50211,error.getErrorCode().code());String message=appender.list.get(appender.list.size()-1).getFormattedMessage();assertAll(()->assertTrue(message.contains("Learning Path JSON parse failed")),()->assertTrue(message.contains("length="+truncated.length())),()->assertTrue(message.contains("startsWithObject=true")),()->assertTrue(message.contains("endsWithObject=false")),()->assertTrue(message.contains("firstBrace=0")),()->assertTrue(message.contains("exception=JsonEOFException")),()->assertTrue(message.contains("line=")),()->assertTrue(message.contains("column=")),()->assertFalse(message.contains("Java基础路线")),()->assertFalse(message.contains("理解封装继承多态")));}
        finally{logger.detachAppender(appender);appender.stop();}
    }
    @Test void wrongJsonFieldTypeReturns50211WithMismatchTypeAndPathDiagnostics(){
        Logger logger=(Logger)LoggerFactory.getLogger(LearningPathAiService.class);ListAppender<ILoggingEvent> appender=new ListAppender<>();appender.start();logger.addAppender(appender);
        try{String wrongType=valid().replace("\"estimatedMinutes\":60","\"estimatedMinutes\":\"很多\"");BusinessException error=assertThrows(BusinessException.class,()->service.parse(wrongType));assertEquals(50211,error.getErrorCode().code());String message=appender.list.get(appender.list.size()-1).getFormattedMessage();assertAll(()->assertTrue(message.contains("exception=InvalidFormatException")),()->assertTrue(message.contains("targetType=java.lang.Integer")),()->assertTrue(message.contains("path=stages.[0].items.[0].estimatedMinutes")),()->assertFalse(message.contains("很多")));}
        finally{logger.detachAppender(appender);appender.stop();}
    }
    @Test void validJsonStillParsesNormally(){var model=service.parse(valid());assertEquals("Java基础路线",model.title());assertEquals(60,model.stages().get(0).items().get(0).estimatedMinutes());}
    @Test void rejectsInvalidDateRange(){var bad=new LearningPathGenerateRequest("Java",null,null,null,120,today.plusDays(3),today,null);assertThrows(BusinessException.class,()->service.generate(bad));verifyNoInteractions(gateway);}
    @Test void aiDisabledIsExplicit(){properties.setEnabled(false);assertThrows(BusinessException.class,()->service.generate(request()));verifyNoInteractions(gateway);}
    @Test void providerFailureReturnsSafeBusinessError(){when(gateway.generate(anyString(),anyString())).thenThrow(new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE));BusinessException error=assertThrows(BusinessException.class,()->service.generate(request()));assertEquals(ErrorCode.AI_PROVIDER_UNAVAILABLE,error.getErrorCode());verify(gateway,times(1)).generate(anyString(),anyString());}
    private LearningPathGenerateRequest request(){return new LearningPathGenerateRequest("复习Java",null,"基础一般","面试",120,today,LocalDate.of(2026,9,1),"轻松");}
    private String valid(){return "{\"title\":\"Java基础路线\",\"summary\":\"系统复习\",\"stages\":[{\"stageNo\":1,\"title\":\"语言基础\",\"description\":\"基础\",\"items\":[{\"sequenceNo\":1,\"topic\":\"面向对象\",\"learningObjective\":\"理解封装继承多态的核心概念\",\"learningMethod\":[\"阅读面向对象核心概念\",\"运行继承示例代码\"],\"completionCriteria\":[\"能脱稿解释封装继承和多态的区别\"],\"estimatedMinutes\":60,\"suggestedDay\":1,\"prerequisite\":null,\"reason\":\"基础能力\"}]}]}";}
    private String twoItems(String secondTopic,int secondDay,int secondMinutes){return """
            {"title":"Java基础路线","summary":"系统复习","stages":[{"stageNo":1,"title":"语言基础","description":"基础","items":[
            {"sequenceNo":1,"topic":"面向对象","learningObjective":"理解封装继承多态的核心概念","learningMethod":["阅读面向对象核心概念","运行继承示例代码"],"completionCriteria":["能脱稿解释封装继承和多态的区别"],"estimatedMinutes":60,"suggestedDay":1,"prerequisite":null,"reason":"基础能力"},
            {"sequenceNo":2,"topic":"%s","learningObjective":"理解该知识点的核心结构和实际应用场景","learningMethod":["阅读核心机制说明","编写并运行示例代码"],"completionCriteria":["能独立解释核心流程和适用场景"],"estimatedMinutes":%d,"suggestedDay":%d,"prerequisite":"面向对象","reason":"后续基础"}]}]}
            """.formatted(secondTopic,secondMinutes,secondDay);}
    private String pathWithNodes(int count,int durationDays){
        StringBuilder items=new StringBuilder();
        for(int index=1;index<=count;index++){
            if(index>1)items.append(',');
            int day=1+(index-1)*(durationDays-1)/Math.max(1,count-1);
            String prerequisite=index==1?"null":"\"知识点"+(index-1)+"\"";
            items.append("""
                    {"sequenceNo":%d,"topic":"知识点%d","learningObjective":"理解知识点%d的核心机制和实际应用场景","learningMethod":["阅读知识点%d的核心说明","完成知识点%d示例练习"],"completionCriteria":["能独立解释知识点%d的核心流程"],"estimatedMinutes":60,"suggestedDay":%d,"prerequisite":%s,"reason":"构建连续知识基础"}
                    """.formatted(index,index,index,index,index,index,day,prerequisite));
        }
        return "{\"title\":\"Java核心路线\",\"summary\":\"循序渐进掌握核心知识。\",\"stages\":[{\"stageNo\":1,\"title\":\"核心阶段\",\"description\":\"完成核心知识学习。\",\"items\":["+items+"]}]}";
    }
}
