package com.yhk.aistudyplanner.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yhk.aistudyplanner.ai.dto.QuickCreatePlanTaskRequest;
import com.yhk.aistudyplanner.ai.mapper.PlanningSelectionMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.subject.entity.Subject;
import com.yhk.aistudyplanner.subject.mapper.SubjectMapper;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PlanningQuickCreateServiceTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);

  @Mock private PlanningSelectionMapper selectionMapper;
  @Mock private SubjectMapper subjectMapper;
  @Mock private TaskMapper taskMapper;
  @Mock private AuthSessionService sessionService;

  private PlanningQuickCreateService service;

  @BeforeEach
  void setUp() {
    when(sessionService.currentUserId()).thenReturn(1L);
    service =
        new PlanningQuickCreateService(
            selectionMapper,
            subjectMapper,
            taskMapper,
            sessionService,
            Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai")));
  }

  @Test
  void reusesOwnedSubjectAndCreatesTask() {
    Subject subject = subject(10L, 1L, "C++");
    when(subjectMapper.selectOne(any())).thenReturn(subject);
    when(taskMapper.selectOne(any())).thenReturn(null);
    assignTaskId(20L);

    var result = service.createOrReuse(request(TODAY, 120));

    assertFalse(result.createdSubject());
    assertTrue(result.createdTask());
    assertEquals(10L, result.subjectId());
    assertEquals(20L, result.taskId());
    verify(subjectMapper, never()).insert(any(Subject.class));
    verify(taskMapper).insert(any(StudyTask.class));
  }

  @Test
  void createsOwnedSubjectAndTaskWhenSubjectDoesNotExist() {
    when(subjectMapper.selectOne(any())).thenReturn(null);
    when(taskMapper.selectOne(any())).thenReturn(null);
    doAnswer(
            invocation -> {
              Subject subject = invocation.getArgument(0);
              subject.setId(10L);
              return 1;
            })
        .when(subjectMapper)
        .insert(any(Subject.class));
    assignTaskId(20L);

    var result = service.createOrReuse(request(TODAY.plusDays(1), 120));

    assertTrue(result.createdSubject());
    assertTrue(result.createdTask());
    verify(selectionMapper).lockUser(1L);
    verify(subjectMapper).insert(any(Subject.class));
    verify(taskMapper).insert(any(StudyTask.class));
  }

  @Test
  void subjectCreationFailureStopsTaskCreation() {
    when(subjectMapper.selectOne(any())).thenReturn(null);
    when(subjectMapper.insert(any(Subject.class)))
        .thenThrow(new IllegalStateException("insert failed"));

    assertThrows(IllegalStateException.class, () -> service.createOrReuse(request(TODAY, 120)));

    verify(taskMapper, never()).insert(any(StudyTask.class));
  }

  @Test
  void taskCreationFailurePropagatesInsideTransactionalMethod() throws Exception {
    when(subjectMapper.selectOne(any())).thenReturn(null);
    when(taskMapper.selectOne(any())).thenReturn(null);
    doAnswer(
            invocation -> {
              Subject subject = invocation.getArgument(0);
              subject.setId(10L);
              return 1;
            })
        .when(subjectMapper)
        .insert(any(Subject.class));
    when(taskMapper.insert(any(StudyTask.class)))
        .thenThrow(new IllegalStateException("insert failed"));

    assertThrows(IllegalStateException.class, () -> service.createOrReuse(request(TODAY, 120)));
    assertTrue(
        PlanningQuickCreateService.class
                .getMethod("createOrReuse", QuickCreatePlanTaskRequest.class)
                .getAnnotation(Transactional.class)
            != null);
  }

  @Test
  void repeatedRequestReusesExistingPendingTask() {
    Subject subject = subject(10L, 1L, "C++");
    StudyTask task = task(20L, 1L, 10L, "学习C++", TODAY);
    when(subjectMapper.selectOne(any())).thenReturn(subject);
    when(taskMapper.selectOne(any())).thenReturn(task);

    var result = service.createOrReuse(request(TODAY, 120));

    assertFalse(result.createdSubject());
    assertFalse(result.createdTask());
    assertEquals(20L, result.taskId());
    verify(taskMapper, never()).insert(any(StudyTask.class));
  }

  @Test
  void sameNamedSubjectFromAnotherUserIsNotReused() {
    when(subjectMapper.selectOne(any())).thenReturn(null);
    when(taskMapper.selectOne(any())).thenReturn(null);
    doAnswer(
            invocation -> {
              Subject subject = invocation.getArgument(0);
              assertEquals(1L, subject.getUserId());
              subject.setId(10L);
              return 1;
            })
        .when(subjectMapper)
        .insert(any(Subject.class));
    assignTaskId(20L);

    var result = service.createOrReuse(request(TODAY, 120));

    assertTrue(result.createdSubject());
  }

  @Test
  void invalidDateAndDurationAreRejected() {
    BusinessException past =
        assertThrows(
            BusinessException.class,
            () -> service.createOrReuse(request(TODAY.minusDays(1), 120)));
    BusinessException duration =
        assertThrows(
            BusinessException.class,
            () -> service.createOrReuse(request(TODAY, 721)));

    assertEquals(ErrorCode.PLAN_DATE_IN_PAST, past.getErrorCode());
    assertEquals(ErrorCode.INVALID_PLAN_DURATION, duration.getErrorCode());
    verify(selectionMapper, never()).lockUser(1L);
  }

  private QuickCreatePlanTaskRequest request(LocalDate date, int minutes) {
    return new QuickCreatePlanTaskRequest("C++", "学习C++", date, minutes, "准备学习C++");
  }

  private Subject subject(long id, long userId, String name) {
    Subject subject = new Subject();
    subject.setId(id);
    subject.setUserId(userId);
    subject.setName(name);
    return subject;
  }

  private StudyTask task(long id, long userId, long subjectId, String title, LocalDate plannedDate) {
    StudyTask task = new StudyTask();
    task.setId(id);
    task.setUserId(userId);
    task.setSubjectId(subjectId);
    task.setTitle(title);
    task.setStatus(TaskStatus.TODO);
    task.setPlannedDate(plannedDate);
    return task;
  }

  private void assignTaskId(long id) {
    doAnswer(
            invocation -> {
              StudyTask task = invocation.getArgument(0);
              task.setId(id);
              return 1;
            })
        .when(taskMapper)
        .insert(any(StudyTask.class));
  }
}
