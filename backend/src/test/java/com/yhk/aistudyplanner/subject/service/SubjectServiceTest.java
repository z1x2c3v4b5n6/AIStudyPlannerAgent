package com.yhk.aistudyplanner.subject.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.subject.dto.SubjectCreateRequest;
import com.yhk.aistudyplanner.subject.dto.SubjectUpdateRequest;
import com.yhk.aistudyplanner.subject.entity.Subject;
import com.yhk.aistudyplanner.subject.mapper.SubjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {
    @Mock private SubjectMapper mapper;
    @Mock private AuthSessionService sessionService;
    private SubjectService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Subject.class);
        service = new SubjectService(mapper, sessionService);
        when(sessionService.currentUserId()).thenReturn(1L);
    }

    @Test
    void createsSubjectForCurrentUser() {
        when(mapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> { ((Subject) invocation.getArgument(0)).setId(10L); return 1; })
                .when(mapper).insert(any(Subject.class));

        var result = service.create(new SubjectCreateRequest("  数学  ", "基础", "#fff", 2));

        assertEquals(10L, result.id());
        assertEquals("数学", result.name());
        verify(mapper).insert(argThat((Subject subject) -> subject.getUserId() == 1L));
    }

    @Test
    void rejectsDuplicateNameForSameUser() {
        when(mapper.selectCount(any())).thenReturn(1L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new SubjectCreateRequest("数学", null, null, null)));
        assertEquals(ErrorCode.SUBJECT_NAME_EXISTS, exception.getErrorCode());
    }

    @Test
    void allowsSameNameForDifferentUsersBecauseQueryIsUserScoped() {
        when(sessionService.currentUserId()).thenReturn(2L);
        when(mapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> { ((Subject) invocation.getArgument(0)).setId(11L); return 1; })
                .when(mapper).insert(any(Subject.class));

        service.create(new SubjectCreateRequest("数学", null, null, 0));

        ArgumentCaptor<Wrapper<Subject>> captor = wrapperCaptor();
        verify(mapper).selectCount(captor.capture());
        assertUserScoped(captor.getValue(), 2L);
    }

    @Test
    void cannotReadAnotherUsersSubject() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectById(9L)).thenReturn(subject(9L, 2L));
        BusinessException exception = assertThrows(BusinessException.class, () -> service.get(9L));
        assertEquals(ErrorCode.SUBJECT_ACCESS_DENIED, exception.getErrorCode());
        ArgumentCaptor<Wrapper<Subject>> captor = wrapperCaptor();
        verify(mapper).selectOne(captor.capture());
        assertUserScoped(captor.getValue(), 1L);
    }

    @Test
    void cannotUpdateOrDeleteAnotherUsersSubject() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectById(9L)).thenReturn(subject(9L, 2L));
        assertEquals(ErrorCode.SUBJECT_ACCESS_DENIED, assertThrows(BusinessException.class,
                () -> service.update(9L, new SubjectUpdateRequest("英语", null, null, 0))).getErrorCode());
        assertEquals(ErrorCode.SUBJECT_ACCESS_DENIED,
                assertThrows(BusinessException.class, () -> service.delete(9L)).getErrorCode());
        verify(mapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void rejectsDeleteWhenReferencesExist() {
        when(mapper.selectOne(any())).thenReturn(subject(5L, 1L));
        when(mapper.countGoals(1L, 5L)).thenReturn(1L);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(5L));
        assertEquals(ErrorCode.SUBJECT_HAS_REFERENCES, exception.getErrorCode());
        verify(mapper, never()).delete(any(Wrapper.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Wrapper<Subject>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private void assertUserScoped(Wrapper<?> wrapper, long userId) {
        assertTrue(wrapper.getSqlSegment().contains("user_id"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(userId));
        assertFalse(((AbstractWrapper<?, ?, ?>) wrapper).getExpression().getNormal().isEmpty());
    }

    private Subject subject(long id, long userId) {
        Subject subject = new Subject();
        subject.setId(id); subject.setUserId(userId); subject.setName("数学"); subject.setSortOrder(0);
        subject.setCreatedAt(LocalDateTime.now()); subject.setUpdatedAt(LocalDateTime.now());
        return subject;
    }
}
