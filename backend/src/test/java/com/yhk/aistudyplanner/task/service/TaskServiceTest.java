package com.yhk.aistudyplanner.task.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import com.yhk.aistudyplanner.goal.entity.StudyGoal;
import com.yhk.aistudyplanner.goal.service.GoalService;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.dto.TaskCreateRequest;
import com.yhk.aistudyplanner.task.dto.TaskStatusRequest;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock private TaskMapper mapper;
    @Mock private SubjectService subjectService;
    @Mock private GoalService goalService;
    @Mock private AuthSessionService sessionService;
    private TaskService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StudyTask.class);
        service = new TaskService(mapper, subjectService, goalService, sessionService);
        when(sessionService.currentUserId()).thenReturn(1L);
    }

    @Test
    void rejectsAnotherUsersGoal() {
        doThrow(new BusinessException(ErrorCode.GOAL_ACCESS_DENIED)).when(goalService).requireOwned(9L, 1L);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(createRequest(2L, 9L)));
        assertEquals(ErrorCode.GOAL_ACCESS_DENIED, exception.getErrorCode());
        verify(mapper, never()).insert(any(StudyTask.class));
    }

    @Test
    void rejectsGoalSubjectMismatch() {
        when(goalService.requireOwned(5L, 1L)).thenReturn(goal(5L, 3L));
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(createRequest(2L, 5L)));
        assertEquals(ErrorCode.GOAL_SUBJECT_MISMATCH, exception.getErrorCode());
    }

    @Test
    void completingTaskSetsCompletedAt() {
        when(mapper.selectOne(any())).thenReturn(task(4L, 1L, TaskStatus.IN_PROGRESS));
        when(mapper.update(isNull(), any())).thenReturn(1);
        var result = service.changeStatus(4L, new TaskStatusRequest(TaskStatus.COMPLETED));
        assertEquals(TaskStatus.COMPLETED, result.status());
        assertNotNull(result.completedAt());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaUpdateWrapper<StudyTask>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("completed_at"));
        assertTrue(captor.getValue().getParamNameValuePairs().values().stream()
                .anyMatch(LocalDateTime.class::isInstance));
    }

    @Test
    void reopeningCompletedTaskClearsCompletedAt() {
        StudyTask task = task(4L, 1L, TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now().minusHours(1));
        when(mapper.selectOne(any())).thenReturn(task);
        when(mapper.update(isNull(), any())).thenReturn(1);
        var result = service.changeStatus(4L, new TaskStatusRequest(TaskStatus.IN_PROGRESS));
        assertEquals(TaskStatus.IN_PROGRESS, result.status());
        assertNull(result.completedAt());
    }

    @Test
    void todayUsesCurrentUserAndReturnsOnlyMapperFilteredTasks() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        when(mapper.selectToday(1L, date)).thenReturn(List.of(
                task(1L, 1L, TaskStatus.TODO), task(2L, 1L, TaskStatus.IN_PROGRESS)));
        var result = service.today(date);
        assertEquals(2, result.tasks().size());
        assertEquals(120, result.totalEstimatedMinutes());
        verify(mapper).selectToday(1L, date);
    }

    @Test
    void upcomingUsesCurrentUserAndBoundedWindow() {
        StudyTask first = task(1L, 1L, TaskStatus.TODO);
        StudyTask second = task(2L, 1L, TaskStatus.IN_PROGRESS);
        first.setDueAt(LocalDateTime.now().plusHours(1));
        second.setDueAt(LocalDateTime.now().plusHours(2));
        when(mapper.selectUpcoming(eq(1L), any(), any())).thenReturn(List.of(first, second));
        var result = service.upcoming(7);
        assertEquals(List.of(1L, 2L), result.stream().map(view -> view.id()).toList());
        verify(mapper).selectUpcoming(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void rejectsDeleteWhenTaskHasReferences() {
        when(mapper.selectOne(any())).thenReturn(task(4L, 1L, TaskStatus.TODO));
        when(mapper.countRecords(1L, 4L)).thenReturn(1L);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(4L));
        assertEquals(ErrorCode.TASK_HAS_REFERENCES, exception.getErrorCode());
        verify(mapper, never()).delete(any(Wrapper.class));
    }

    private TaskCreateRequest createRequest(long subjectId, Long goalId) {
        return new TaskCreateRequest(subjectId, goalId, "任务", null, 3, 60, LocalDate.now(), LocalDateTime.now().plusDays(1));
    }

    private StudyTask task(long id, long userId, TaskStatus status) {
        StudyTask task = new StudyTask();
        task.setId(id); task.setUserId(userId); task.setSubjectId(2L); task.setTitle("任务");
        task.setPriority(3); task.setStatus(status); task.setEstimatedMinutes(60);
        task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private StudyGoal goal(long id, long subjectId) {
        StudyGoal goal = new StudyGoal();
        goal.setId(id); goal.setUserId(1L); goal.setSubjectId(subjectId); goal.setTitle("目标");
        goal.setTargetMinutes(100); goal.setStatus(GoalStatus.ACTIVE);
        return goal;
    }
}
