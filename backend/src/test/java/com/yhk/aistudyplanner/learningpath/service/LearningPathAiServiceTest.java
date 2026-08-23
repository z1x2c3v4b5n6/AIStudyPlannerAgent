package com.yhk.aistudyplanner.learningpath.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class LearningPathAiServiceTest {
    private AiPlanningProperties properties; private PlanningGateway gateway; private LearningPathAiService service;
    private final LocalDate today=LocalDate.of(2026,8,14);
    @BeforeEach void setup(){
        properties=new AiPlanningProperties();properties.setEnabled(true);properties.setApiKey("test-key");gateway=mock(PlanningGateway.class);
        var factory=new StaticListableBeanFactory();factory.addBean("gateway",gateway);ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        service=new LearningPathAiService(properties,factory.getBeanProvider(PlanningGateway.class),new LearningPathPromptService(mapper),mapper,
                mock(SubjectService.class),mock(AuthSessionService.class),Clock.fixed(today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")));
    }
    @Test void parsesCompleteJavaLearningPathWithoutLosingFields(){when(gateway.generate(anyString(),anyString())).thenReturn(JavaLearningPathFixture.modelJson());var result=service.generate(request());assertEquals("Java后端面试学习路径",result.title());assertEquals(4,result.stages().size());var hashMap=result.stages().get(1).items().get(0);assertEquals("HashMap",hashMap.topic());assertTrue(hashMap.learningObjective().contains("put/get"));assertEquals(4,hashMap.learningMethod().size());assertEquals(3,hashMap.completionCriteria().size());assertEquals(120,hashMap.estimatedMinutes());assertEquals(5,hashMap.suggestedDay());assertEquals("面向对象",hashMap.prerequisite());assertTrue(hashMap.reason().contains("面试"));}
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
    @Test void rejectsInvalidDateRange(){var bad=new LearningPathGenerateRequest("Java",null,null,null,120,today.plusDays(3),today,null);assertThrows(BusinessException.class,()->service.generate(bad));verifyNoInteractions(gateway);}
    @Test void aiDisabledIsExplicit(){properties.setEnabled(false);assertThrows(BusinessException.class,()->service.generate(request()));verifyNoInteractions(gateway);}
    @Test void providerFailureReturnsSafeBusinessError(){when(gateway.generate(anyString(),anyString())).thenThrow(new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE));BusinessException error=assertThrows(BusinessException.class,()->service.generate(request()));assertEquals(ErrorCode.AI_PROVIDER_UNAVAILABLE,error.getErrorCode());}
    private LearningPathGenerateRequest request(){return new LearningPathGenerateRequest("复习Java",null,"基础一般","面试",120,today,today.plusDays(20),"轻松");}
    private String valid(){return "{\"title\":\"Java基础路线\",\"summary\":\"系统复习\",\"stages\":[{\"stageNo\":1,\"title\":\"语言基础\",\"description\":\"基础\",\"items\":[{\"sequenceNo\":1,\"topic\":\"面向对象\",\"learningObjective\":\"理解封装继承多态的核心概念\",\"learningMethod\":[\"阅读面向对象核心概念\",\"运行继承示例代码\"],\"completionCriteria\":[\"能脱稿解释封装继承和多态的区别\"],\"estimatedMinutes\":60,\"suggestedDay\":1,\"prerequisite\":null,\"reason\":\"基础能力\"}]}]}";}
    private String twoItems(String secondTopic,int secondDay,int secondMinutes){return """
            {"title":"Java基础路线","summary":"系统复习","stages":[{"stageNo":1,"title":"语言基础","description":"基础","items":[
            {"sequenceNo":1,"topic":"面向对象","learningObjective":"理解封装继承多态的核心概念","learningMethod":["阅读面向对象核心概念","运行继承示例代码"],"completionCriteria":["能脱稿解释封装继承和多态的区别"],"estimatedMinutes":60,"suggestedDay":1,"prerequisite":null,"reason":"基础能力"},
            {"sequenceNo":2,"topic":"%s","learningObjective":"理解该知识点的核心结构和实际应用场景","learningMethod":["阅读核心机制说明","编写并运行示例代码"],"completionCriteria":["能独立解释核心流程和适用场景"],"estimatedMinutes":%d,"suggestedDay":%d,"prerequisite":"面向对象","reason":"后续基础"}]}]}
            """.formatted(secondTopic,secondMinutes,secondDay);}
}
