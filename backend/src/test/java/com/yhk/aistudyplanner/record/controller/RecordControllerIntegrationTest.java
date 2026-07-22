package com.yhk.aistudyplanner.record.controller;

import com.yhk.aistudyplanner.common.exception.GlobalExceptionHandler;
import com.yhk.aistudyplanner.record.dto.RecordCreateRequest;
import com.yhk.aistudyplanner.record.service.StudyRecordService;
import com.yhk.aistudyplanner.record.vo.StudyRecordView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecordControllerIntegrationTest {
    @Mock private StudyRecordService recordService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new StudyRecordController(recordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsCalculatedRecord() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 7, 21, 19, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 21, 20, 15);
        when(recordService.create(any(RecordCreateRequest.class))).thenReturn(new StudyRecordView(
                1L, 2L, "高等数学", "#409EFF", 10L, "极限练习", start, end, 75,
                "完成练习", end, end));

        mockMvc.perform(post("/api/v1/records")
                        .contentType("application/json")
                        .content("""
                                {"subjectId":2,"taskId":10,"startedAt":"2026-07-21T19:00:00",
                                 "endedAt":"2026-07-21T20:15:00","feedback":"完成练习"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMinutes").value(75))
                .andExpect(jsonPath("$.data.subjectName").value("高等数学"));
    }

    @Test
    void callerCannotSubmitDurationMinutes() throws Exception {
        mockMvc.perform(post("/api/v1/records")
                        .contentType("application/json")
                        .content("""
                                {"subjectId":2,"startedAt":"2026-07-21T19:00:00",
                                 "endedAt":"2026-07-21T20:15:00","durationMinutes":999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.durationMinutes").exists());
        verify(recordService, never()).create(any());
    }
}
