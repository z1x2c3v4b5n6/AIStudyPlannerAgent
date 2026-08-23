package com.yhk.aistudyplanner.learningpath.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.learningpath.dto.*;
import com.yhk.aistudyplanner.learningpath.entity.*;
import com.yhk.aistudyplanner.learningpath.mapper.*;
import com.yhk.aistudyplanner.subject.entity.Subject;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.entity.*;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import java.time.*;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningPathServiceTest {
    @Mock LearningPathMapper pathMapper; @Mock LearningPathItemMapper itemMapper; @Mock TaskService taskService;
    @Mock SubjectService subjectService; @Mock AuthSessionService session;
    LearningPathService service; LocalDate today=LocalDate.of(2026,8,14);
    @BeforeEach void setup(){
        var assistant=new MapperBuilderAssistant(new MybatisConfiguration(),"path-test");TableInfoHelper.initTableInfo(assistant,StudyPath.class);TableInfoHelper.initTableInfo(assistant,StudyPathItem.class);TableInfoHelper.initTableInfo(assistant,StudyTask.class);
        service=new LearningPathService(pathMapper,itemMapper,taskService,subjectService,session,new ObjectMapper().findAndRegisterModules(),Clock.fixed(today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")));
        lenient().when(session.currentUserId()).thenReturn(1L);
    }
    @Test void confirmsPathAndItemsTransactionally(){Subject subject=subject();when(subjectService.requireOwned(2L,1L)).thenReturn(subject);when(pathMapper.insert(any(StudyPath.class))).thenAnswer(i->{((StudyPath)i.getArgument(0)).setId(7L);return 1;});when(itemMapper.selectRows(7L,1L)).thenReturn(List.of());var result=service.confirm(confirm());assertEquals(7L,result.id());verify(itemMapper).insert(any(StudyPathItem.class));}
    @Test void confirmPersistsUsersFinalEditedDraftWithoutLosingFields(){when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(pathMapper.insert(any(StudyPath.class))).thenAnswer(i->{((StudyPath)i.getArgument(0)).setId(7L);return 1;});when(itemMapper.selectRows(7L,1L)).thenReturn(List.of());var edited=new LearningPathConfirmRequest(2L,"用户修改后的Java路线","修改后的摘要","三周复习Java",null,"基础一般","通过Java后端面试",null,null,today,today.plusDays(20),120,List.of(new LearningPathConfirmRequest.Item(1,1,"Java集合",null,"HashMap扩容机制","理解JDK8 HashMap结构、put流程和扩容条件",List.of("编写put与get示例","分析一次扩容过程"),List.of("能解释put流程和扩容条件","能说明线程不安全原因"),90,4,"面向对象和集合体系","面试高频知识点")));service.confirm(edited);var captor=ArgumentCaptor.forClass(StudyPathItem.class);verify(itemMapper).insert(captor.capture());var saved=captor.getValue();assertEquals("HashMap扩容机制",saved.getTopic());assertEquals("理解JDK8 HashMap结构、put流程和扩容条件",saved.getLearningObjective());assertEquals("[\"编写put与get示例\",\"分析一次扩容过程\"]",saved.getLearningMethod());assertEquals("[\"能解释put流程和扩容条件\",\"能说明线程不安全原因\"]",saved.getCompletionCriteria());assertEquals(90,saved.getEstimatedMinutes());assertEquals(4,saved.getSuggestedDay());assertEquals("面向对象和集合体系",saved.getPrerequisiteText());assertEquals("面试高频知识点",saved.getReason());}
    @Test void confirmRejectsFutureTopicAsPrerequisite(){var base=confirm().items().get(0);var items=List.of(new LearningPathConfirmRequest.Item(1,1,"基础",null,"面向对象",base.learningObjective(),base.learningMethod(),base.completionCriteria(),60,1,"HashMap",null),new LearningPathConfirmRequest.Item(2,2,"集合",null,"HashMap","理解JDK8 HashMap结构和put与扩容流程",List.of("编写HashMap示例并分析put流程"),List.of("能解释HashMap扩容条件"),60,2,"面向对象",null));var request=new LearningPathConfirmRequest(2L,"Java路线","摘要","复习Java",null,"一般","面试",null,null,today,today.plusDays(20),120,items);when(subjectService.requireOwned(2L,1L)).thenReturn(subject());assertThrows(BusinessException.class,()->service.confirm(request));verify(pathMapper,never()).insert(any(StudyPath.class));}
    @Test void otherUsersSubjectIsRejected(){when(subjectService.requireOwned(2L,1L)).thenThrow(new BusinessException(com.yhk.aistudyplanner.common.exception.ErrorCode.SUBJECT_ACCESS_DENIED));assertThrows(BusinessException.class,()->service.confirm(confirm()));verify(pathMapper,never()).insert(any(StudyPath.class));}
    @Test void otherUsersPathIsRejected(){when(pathMapper.selectOne(any())).thenReturn(null);StudyPath foreign=path();foreign.setUserId(2L);when(pathMapper.selectById(7L)).thenReturn(foreign);assertThrows(BusinessException.class,()->service.get(7L));}
    @Test void createsTaskThroughTaskServiceAndLinksItem(){StudyPath path=path();StudyPathItem item=item();when(pathMapper.selectOwnedForUpdate(7L,1L)).thenReturn(path);when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(itemMapper.selectOwnedForUpdate(7L,3L,1L)).thenReturn(item);when(taskService.create(any())).thenReturn(taskView(11L));when(itemMapper.update(isNull(),any())).thenReturn(1);var result=service.createTask(7L,3L);assertTrue(result.created());assertEquals(11L,result.taskId());var request=ArgumentCaptor.forClass(com.yhk.aistudyplanner.task.dto.TaskCreateRequest.class);verify(taskService).create(request.capture());assertEquals("面向对象",request.getValue().title());assertEquals(60,request.getValue().estimatedMinutes());assertEquals(today,request.getValue().plannedDate());}
    @Test void repeatedCreateReusesLinkedTask(){StudyPathItem item=item();item.setTaskId(11L);when(pathMapper.selectOwnedForUpdate(7L,1L)).thenReturn(path());when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(itemMapper.selectOwnedForUpdate(7L,3L,1L)).thenReturn(item);when(taskService.get(11L)).thenReturn(taskView(11L));var result=service.createTask(7L,3L);assertFalse(result.created());verify(taskService,never()).create(any());}
    @Test void taskCompletionDrivesProgressAndSkippedUnlinkedDoesNotComplete(){when(pathMapper.selectOne(any())).thenReturn(path());when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(itemMapper.selectRows(7L,1L)).thenReturn(List.of(row(3L,"COMPLETED","PENDING",11L),row(4L,null,"SKIPPED",null)));var result=service.get(7L);assertEquals(1,result.completedItems());assertEquals(1,result.skippedItems());assertEquals(50.0,result.progress());}
    @Test void restoredLinkedTaskReturnsPathItemToPending(){when(pathMapper.selectOne(any())).thenReturn(path());when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(itemMapper.selectRows(7L,1L)).thenReturn(List.of(row(3L,"TODO","PENDING",11L)));var result=service.get(7L);assertEquals(0,result.completedItems());assertEquals("PENDING",result.items().get(0).effectiveStatus());assertEquals(0.0,result.progress());}
    @Test void dailyPlanSkipDoesNotCompleteLinkedPathItem(){when(pathMapper.selectOne(any())).thenReturn(path());when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(itemMapper.selectRows(7L,1L)).thenReturn(List.of(row(3L,"IN_PROGRESS","PENDING",11L)));var result=service.get(7L);assertEquals("PENDING",result.items().get(0).effectiveStatus());assertEquals(0,result.completedItems());}
    @Test void otherUserCannotCreateTaskForOwnedPath(){when(pathMapper.selectOwnedForUpdate(7L,1L)).thenReturn(null);when(pathMapper.selectOne(any())).thenReturn(null);when(pathMapper.selectById(7L)).thenReturn(path());assertThrows(BusinessException.class,()->service.createTask(7L,3L));verify(taskService,never()).create(any());}
    @Test void cancellingPathDoesNotModifyLinkedTasks(){StudyPath path=path();when(pathMapper.selectOwnedForUpdate(7L,1L)).thenReturn(path);when(pathMapper.update(isNull(),any())).thenReturn(1);when(subjectService.requireOwned(2L,1L)).thenReturn(subject());when(itemMapper.selectRows(7L,1L)).thenReturn(List.of(row(3L,"TODO","PENDING",11L)));service.changeStatus(7L,new LearningPathStatusRequest(LearningPathStatus.CANCELLED));verifyNoInteractions(taskService);}
    private LearningPathConfirmRequest confirm(){return new LearningPathConfirmRequest(2L,"Java路线","摘要","三周复习Java",null,"一般","面试",null,null,today,today.plusDays(20),120,List.of(new LearningPathConfirmRequest.Item(1,1,"基础",null,"面向对象","理解封装继承多态及对象模型的核心概念",List.of("阅读面向对象核心概念","编写继承示例代码"),List.of("能解释封装继承和多态的区别"),60,1,null,null)));}
    private StudyPath path(){StudyPath p=new StudyPath();p.setId(7L);p.setUserId(1L);p.setSubjectId(2L);p.setTitle("Java路线");p.setOriginalRequirement("Java");p.setStartDate(today);p.setStatus(LearningPathStatus.ACTIVE);p.setCreatedAt(today.atStartOfDay());p.setUpdatedAt(today.atStartOfDay());return p;}
    private StudyPathItem item(){StudyPathItem i=new StudyPathItem();i.setId(3L);i.setPathId(7L);i.setUserId(1L);i.setTopic("面向对象");i.setLearningObjective("掌握OOP");i.setEstimatedMinutes(60);i.setSuggestedDay(1);i.setStatus(LearningPathItemStatus.PENDING);return i;}
    private Subject subject(){Subject s=new Subject();s.setId(2L);s.setUserId(1L);s.setName("Java基础");s.setColor("#409EFF");return s;}
    private TaskView taskView(long id){return new TaskView(id,2L,null,"面向对象","掌握OOP",2,TaskStatus.TODO,60,today,null,null,today.atStartOfDay(),today.atStartOfDay());}
    private LearningPathItemMapper.ItemRow row(Long id,String taskStatus,String stored,Long taskId){return new LearningPathItemMapper.ItemRow(id,id.intValue()-2,1,"基础",null,"主题","目标","[\"阅读\"]","[\"掌握\"]",60,1,null,null,stored,taskId,"任务",taskStatus);}
}
