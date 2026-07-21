package com.yhk.aistudyplanner.goal.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.goal.controller.GoalController;
import com.yhk.aistudyplanner.goal.dto.GoalCreateRequest;
import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import com.yhk.aistudyplanner.goal.entity.StudyGoal;
import com.yhk.aistudyplanner.goal.mapper.GoalMapper;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {
    @Mock private GoalMapper mapper;
    @Mock private SubjectService subjectService;
    @Mock private AuthSessionService sessionService;
    private GoalService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), StudyGoal.class);
        service = new GoalService(mapper, subjectService, sessionService);
        lenient().when(sessionService.currentUserId()).thenReturn(1L);
    }

    @Test
    void rejectsAnotherUsersSubjectWhenCreatingGoal() {
        doThrow(new BusinessException(ErrorCode.SUBJECT_ACCESS_DENIED))
                .when(subjectService).requireOwned(8L, 1L);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(
                new GoalCreateRequest(8L, "通过考试", null, 600, LocalDate.now(), GoalStatus.ACTIVE)));
        assertEquals(ErrorCode.SUBJECT_ACCESS_DENIED, exception.getErrorCode());
        verify(mapper, never()).insert(any(StudyGoal.class));
    }

    @Test
    void returnsTaskProgressForOwnedGoal() {
        when(mapper.selectOne(any())).thenReturn(goal(3L, 1L, 2L));
        when(mapper.countTasks(1L, 3L)).thenReturn(4L);
        when(mapper.countCompletedTasks(1L, 3L)).thenReturn(3L);

        var detail = service.get(3L);

        assertEquals(4, detail.taskCount());
        assertEquals(3, detail.completedTaskCount());
        assertEquals(75D, detail.completionRate());
        Wrapper<?> wrapper = mockingDetails(mapper).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("selectOne"))
                .map(i -> (Wrapper<?>) i.getArgument(0)).findFirst().orElseThrow();
        assertTrue(wrapper.getSqlSegment().contains("user_id"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(1L));
        assertFalse(((AbstractWrapper<?, ?, ?>) wrapper).getExpression().getNormal().isEmpty());
    }

    @Test
    void rejectsDeleteWhenGoalHasTasks() {
        when(mapper.selectOne(any())).thenReturn(goal(3L, 1L, 2L));
        when(mapper.countTasks(1L, 3L)).thenReturn(1L);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(3L));
        assertEquals(ErrorCode.GOAL_HAS_TASKS, exception.getErrorCode());
        verify(mapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        GoalController controller = new GoalController(service);
        Method method = GoalController.class.getMethod("list", long.class, long.class, Long.class, GoalStatus.class);
        var validator = Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
        assertFalse(validator.validateParameters(controller, method, new Object[]{0L, 101L, null, null}).isEmpty());
    }

    private StudyGoal goal(long id, long userId, long subjectId) {
        StudyGoal goal = new StudyGoal();
        goal.setId(id); goal.setUserId(userId); goal.setSubjectId(subjectId); goal.setTitle("目标");
        goal.setTargetMinutes(600); goal.setStatus(GoalStatus.ACTIVE);
        goal.setCreatedAt(LocalDateTime.now()); goal.setUpdatedAt(LocalDateTime.now());
        return goal;
    }
}
