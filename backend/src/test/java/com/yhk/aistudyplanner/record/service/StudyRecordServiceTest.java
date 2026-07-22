package com.yhk.aistudyplanner.record.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.record.dto.RecordCreateRequest;
import com.yhk.aistudyplanner.record.dto.RecordUpdateRequest;
import com.yhk.aistudyplanner.record.entity.StudyRecord;
import com.yhk.aistudyplanner.record.mapper.StudyRecordMapper;
import com.yhk.aistudyplanner.record.vo.StudyRecordView;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyRecordServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 21, 0);
    @Mock private StudyRecordMapper recordMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private SubjectService subjectService;
    @Mock private AuthSessionService sessionService;
    private StudyRecordService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        service = new StudyRecordService(recordMapper, taskMapper, subjectService, sessionService, clock);
        lenient().when(sessionService.currentUserId()).thenReturn(1L);
    }

    @Test
    void createsRecordAndCalculatesDuration() {
        when(taskMapper.selectOne(any())).thenReturn(task(10L, 1L, 2L));
        doAnswer(invocation -> { ((StudyRecord) invocation.getArgument(0)).setId(7L); return 1; })
                .when(recordMapper).insert(any(StudyRecord.class));
        when(recordMapper.selectView(7L, 1L)).thenReturn(view(7L, 75));

        StudyRecordView result = service.create(createRequest(2L, 10L,
                NOW.minusMinutes(75), NOW, null));

        assertEquals(75, result.durationMinutes());
        ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals(75, captor.getValue().getDurationMinutes());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void updateRevalidatesAssociationsAndRecalculatesDuration() {
        StudyRecord existing = record(7L, 1L);
        when(recordMapper.selectOne(any())).thenReturn(existing);
        when(taskMapper.selectOne(any())).thenReturn(task(10L, 1L, 2L));
        when(recordMapper.update(any(StudyRecord.class), any())).thenReturn(1);
        when(recordMapper.selectView(7L, 1L)).thenReturn(view(7L, 90));

        StudyRecordView result = service.update(7L, new RecordUpdateRequest(
                2L, 10L, NOW.minusMinutes(90), NOW, "更新", null));

        assertEquals(90, result.durationMinutes());
        verify(subjectService).requireOwned(2L, 1L);
        verify(recordMapper).update(argThat(record -> record.getDurationMinutes() == 90
                && record.getTaskId() == 10L), any());
    }

    @Test
    void rejectsEndBeforeOrEqualStart() {
        assertError(ErrorCode.INVALID_RECORD_TIME, createRequest(2L, null, NOW.minusHours(1), NOW.minusHours(1), null));
        assertError(ErrorCode.INVALID_RECORD_TIME, createRequest(2L, null, NOW, NOW.minusMinutes(1), null));
    }

    @Test
    void rejectsDurationBelowOneMinute() {
        assertError(ErrorCode.INVALID_RECORD_DURATION,
                createRequest(2L, null, NOW.minusSeconds(30), NOW, null));
    }

    @Test
    void rejectsDurationOverOneDay() {
        assertError(ErrorCode.INVALID_RECORD_DURATION,
                createRequest(2L, null, NOW.minusMinutes(1441), NOW, null));
    }

    @Test
    void rejectsFutureEndTime() {
        assertError(ErrorCode.RECORD_END_TIME_IN_FUTURE,
                createRequest(2L, null, NOW, NOW.plusMinutes(1), null));
    }

    @Test
    void rejectsAnotherUsersSubject() {
        doThrow(new BusinessException(ErrorCode.SUBJECT_ACCESS_DENIED)).when(subjectService).requireOwned(9L, 1L);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(
                createRequest(9L, null, NOW.minusHours(1), NOW, null)));
        assertEquals(ErrorCode.SUBJECT_ACCESS_DENIED, exception.getErrorCode());
        verify(recordMapper, never()).insert(any(StudyRecord.class));
    }

    @Test
    void rejectsAnotherUsersTask() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 2L, 2L));
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(
                createRequest(2L, 10L, NOW.minusHours(1), NOW, null)));
        assertEquals(ErrorCode.TASK_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void rejectsTaskSubjectMismatch() {
        when(taskMapper.selectOne(any())).thenReturn(task(10L, 1L, 3L));
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(
                createRequest(2L, 10L, NOW.minusHours(1), NOW, null)));
        assertEquals(ErrorCode.RECORD_SUBJECT_MISMATCH, exception.getErrorCode());
    }

    @Test
    void cannotReadUpdateOrDeleteAnotherUsersRecord() {
        StudyRecord other = record(8L, 2L);
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.selectById(8L)).thenReturn(other);
        assertEquals(ErrorCode.RECORD_ACCESS_DENIED,
                assertThrows(BusinessException.class, () -> service.get(8L)).getErrorCode());
        assertEquals(ErrorCode.RECORD_ACCESS_DENIED,
                assertThrows(BusinessException.class, () -> service.update(8L, updateRequest())).getErrorCode());
        assertEquals(ErrorCode.RECORD_ACCESS_DENIED,
                assertThrows(BusinessException.class, () -> service.delete(8L)).getErrorCode());
        verify(recordMapper, never()).update(any(), any(Wrapper.class));
        verify(recordMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsesCurrentUserFiltersBoundariesAndMapperOrdering() {
        Page<StudyRecordView> result = new Page<>(1, 20);
        result.setRecords(List.of(view(2L, 30), view(1L, 45)));
        result.setTotal(2);
        when(taskMapper.selectOne(any())).thenReturn(task(10L, 1L, 2L));
        when(recordMapper.selectRecordPage(any(), eq(1L), eq(2L), eq(10L), any(), any())).thenReturn(result);

        var response = service.list(1, 20, 2L, 10L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));

        assertEquals(List.of(2L, 1L), response.list().stream().map(StudyRecordView::id).toList());
        verify(recordMapper).selectRecordPage(any(), eq(1L), eq(2L), eq(10L),
                eq(LocalDateTime.of(2026, 7, 20, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 22, 0, 0)));
    }

    @Test
    void listRejectsInvalidDateRange() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.list(
                1, 20, null, null, LocalDate.of(2026, 7, 22), LocalDate.of(2026, 7, 21)));
        assertEquals(ErrorCode.INVALID_DATE_RANGE, exception.getErrorCode());
        verify(recordMapper, never()).selectRecordPage(any(), anyLong(), any(), any(), any(), any());
    }

    private void assertError(ErrorCode code, RecordCreateRequest request) {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals(code, exception.getErrorCode());
        verify(recordMapper, never()).insert(any(StudyRecord.class));
    }

    private RecordCreateRequest createRequest(long subjectId, Long taskId, LocalDateTime start,
                                               LocalDateTime end, Integer submittedDuration) {
        return new RecordCreateRequest(subjectId, taskId, start, end, "反馈", submittedDuration);
    }

    private RecordUpdateRequest updateRequest() {
        return new RecordUpdateRequest(2L, null, NOW.minusHours(1), NOW, "反馈", null);
    }

    private StudyTask task(long id, long userId, long subjectId) {
        StudyTask task = new StudyTask();
        task.setId(id); task.setUserId(userId); task.setSubjectId(subjectId); task.setTitle("任务");
        return task;
    }

    private StudyRecord record(long id, long userId) {
        StudyRecord record = new StudyRecord();
        record.setId(id); record.setUserId(userId); record.setSubjectId(2L);
        return record;
    }

    private StudyRecordView view(long id, int duration) {
        return new StudyRecordView(id, 2L, "高等数学", "#409EFF", 10L, "极限练习",
                NOW.minusMinutes(duration), NOW, duration, "反馈", NOW, NOW);
    }
}
