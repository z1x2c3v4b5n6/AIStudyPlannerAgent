package com.yhk.aistudyplanner.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.dto.CandidateTasksRequest;
import com.yhk.aistudyplanner.ai.dto.NaturalLanguagePlanParseRequest;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.ai.gateway.PlanningGateway;
import com.yhk.aistudyplanner.ai.mapper.PlanningSelectionMapper;
import com.yhk.aistudyplanner.ai.vo.SubjectMatchData;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class NaturalLanguagePlanServiceTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  @Mock private ObjectProvider<PlanningGateway> gatewayProvider;
  @Mock private PlanningGateway gateway;
  @Mock private PlanningSelectionMapper selectionMapper;
  @Mock private AuthSessionService sessionService;

  private AiPlanningProperties properties;
  private NaturalLanguagePlanService service;

  @BeforeEach
  void setUp() {
    properties = new AiPlanningProperties();
    properties.setEnabled(true);
    properties.setApiKey("test-key");
    lenient().when(sessionService.currentUserId()).thenReturn(1L);
    lenient()
        .when(selectionMapper.subjects(1L))
        .thenReturn(
            java.util.List.of(
                new SubjectMatchData(1L, "Java基础", "#409EFF", 4),
                new SubjectMatchData(2L, "Java高级", "#7C3AED", 3)));
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    service =
        new NaturalLanguagePlanService(
            properties,
            gatewayProvider,
            new NaturalLanguagePlanPromptService(objectMapper),
            selectionMapper,
            sessionService,
            objectMapper,
            Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE));
  }

  @Test
  void parsesCompleteNaturalLanguageInput() {
    available(
        """
        {"planDate":"2026-07-24","startTime":"09:00:00","availableMinutes":180,
        "requirement":"优先复习Java集合和并发，然后做Redis面试题并安排算法",
        "preferences":["节奏轻松","按指定顺序"],"topicKeywords":["Java基础"],"needsClarification":false,
        "clarificationMessage":null}
        """);

    var result = service.parse(request("我明天上午9点开始学习3小时"));

    assertEquals(LocalDate.of(2026, 7, 24), result.planDate());
    assertEquals(LocalTime.of(9, 0), result.startTime());
    assertEquals(180, result.availableMinutes());
    assertEquals(2, result.preferences().size());
    assertFalse(result.needsClarification());
    assertEquals(java.util.List.of(1L), result.selectedSubjectIds());
    assertTrue(result.aiParsed());
  }

  @Test
  void missingDateUsesExplicitFallbackAndRequiresConfirmation() {
    available(
        """
        {"planDate":null,"startTime":"19:00:00","availableMinutes":120,
        "requirement":"复习Java","preferences":[],"topicKeywords":["Java"],"needsClarification":true,
        "clarificationMessage":"没有说明日期"}
        """);

    var result = service.parse(request("晚上学习两小时"));

    assertEquals(TODAY, result.planDate());
    assertTrue(result.needsClarification());
    assertTrue(result.clarificationMessage().contains("确认计划日期"));
  }

  @Test
  void missingDurationUsesFallbackAndRequiresConfirmation() {
    available(
        """
        {"planDate":"2026-07-23","startTime":"09:00:00","availableMinutes":null,
        "requirement":"复习Java","preferences":[],"topicKeywords":["Java"],"needsClarification":true,
        "clarificationMessage":"没有说明时长"}
        """);

    var result = service.parse(request("今天上午九点复习Java"));

    assertEquals(120, result.availableMinutes());
    assertTrue(result.needsClarification());
    assertTrue(result.clarificationMessage().contains("确认可用学习时长"));
  }

  @Test
  void pastDateIsRejectedAndFallsBackToTodayForReview() {
    available(
        """
        {"planDate":"2026-07-22","startTime":"09:00:00","availableMinutes":60,
        "requirement":"复习Java","preferences":[],"topicKeywords":["Java"],"needsClarification":false,
        "clarificationMessage":null}
        """);

    var result = service.parse(request("昨天学习一小时"));

    assertEquals(TODAY, result.planDate());
    assertTrue(result.needsClarification());
    assertTrue(result.clarificationMessage().contains("不能早于今天"));
  }

  @Test
  void crossingNaturalDayClearsDurationAndRequiresCorrection() {
    available(
        """
        {"planDate":"2026-07-23","startTime":"23:00:00","availableMinutes":120,
        "requirement":"复习Java","preferences":[],"topicKeywords":["Java"],"needsClarification":false,
        "clarificationMessage":null}
        """);

    var result = service.parse(request("今天晚上十一点学习两小时"));

    assertNull(result.availableMinutes());
    assertTrue(result.needsClarification());
    assertTrue(result.clarificationMessage().contains("不能跨越自然日"));
  }

  @Test
  void invalidJsonReturnsEditableFallbackInsteadOfThrowing() {
    available("not-json");

    var result = service.parse(request("帮我安排学习"));

    assertFalse(result.aiParsed());
    assertTrue(result.needsClarification());
    assertEquals(TODAY, result.planDate());
    assertEquals(120, result.availableMinutes());
  }

  @Test
  void unavailableAiReturnsEditableFallbackWithoutSecondProviderCall() {
    when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
    when(gateway.generate(anyString(), anyString()))
        .thenThrow(new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE));

    var result = service.parse(request("帮我安排学习"));

    assertFalse(result.aiParsed());
    assertTrue(result.needsClarification());
    assertTrue(result.clarificationMessage().contains("高级设置"));
  }

  @Test
  void disabledAiDoesNotCallProvider() {
    properties.setEnabled(false);

    var result = service.parse(request("帮我安排学习"));

    assertFalse(result.aiParsed());
    verify(gatewayProvider, never()).getObject();
  }

  @Test
  void broadJavaTopicIsAmbiguousWhenMultipleSubjectsMatch() {
    available(
        """
        {"planDate":"2026-07-23","startTime":"09:00:00","availableMinutes":120,
        "requirement":"学习Java","preferences":[],"topicKeywords":["Java"],
        "needsClarification":false,"clarificationMessage":null}
        """);

    var result = service.parse(request("我要学习Java"));

    assertTrue(result.needsSubjectSelection());
    assertEquals(java.util.List.of("Java"), result.ambiguousTopics());
    assertEquals(2, result.candidateSubjects().size());
    assertTrue(result.selectedSubjectIds().isEmpty());
  }

  @Test
  void uniqueRelatedSubjectIsSelectedAutomatically() {
    when(selectionMapper.subjects(1L))
        .thenReturn(java.util.List.of(new SubjectMatchData(1L, "Java基础", "#409EFF", 4)));
    available(
        """
        {"planDate":"2026-07-23","startTime":"09:00:00","availableMinutes":120,
        "requirement":"学习Java","preferences":[],"topicKeywords":["Java"],
        "needsClarification":false,"clarificationMessage":null}
        """);

    var result = service.parse(request("我要学习Java"));

    assertFalse(result.needsSubjectSelection());
    assertEquals(java.util.List.of(1L), result.selectedSubjectIds());
  }

  @Test
  void allRelatedSubjectsCanBeSelectedExplicitly() {
    available(
        """
        {"planDate":"2026-07-23","startTime":"09:00:00","availableMinutes":120,
        "requirement":"学习全部Java内容","preferences":[],"topicKeywords":["Java"],
        "needsClarification":false,"clarificationMessage":null}
        """);

    var result = service.parse(request("我要学习全部Java内容"));

    assertFalse(result.needsSubjectSelection());
    assertEquals(java.util.List.of(1L, 2L), result.selectedSubjectIds());
  }

  @Test
  void unrelatedTopicDoesNotRecommendAnySubject() {
    available(
        """
        {"planDate":"2026-07-23","startTime":"09:00:00","availableMinutes":120,
        "requirement":"学习英语","preferences":[],"topicKeywords":["英语"],
        "needsClarification":false,"clarificationMessage":null}
        """);

    var result = service.parse(request("我要学习英语"));

    assertTrue(result.needsSubjectSelection());
    assertTrue(result.candidateSubjects().isEmpty());
    assertEquals(java.util.List.of("英语"), result.unmatchedKeywords());
  }

  @Test
  void otherUsersSubjectIsRejectedWhenLoadingTasks() {
    var request = new CandidateTasksRequest(java.util.List.of(999L), TODAY, "学习Java");

    var exception =
        assertThrows(BusinessException.class, () -> service.candidateTasks(request));

    assertEquals(ErrorCode.SUBJECT_ACCESS_DENIED, exception.getErrorCode());
    verify(selectionMapper, never())
        .tasks(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void candidateTasksUseRequestedPlanDateInsteadOfSystemToday() {
    LocalDate tomorrow = TODAY.plusDays(1);
    var request = new CandidateTasksRequest(java.util.List.of(1L), tomorrow, "tomorrow Java");
    when(selectionMapper.tasks(1L, java.util.List.of(1L), tomorrow, tomorrow.plusDays(3)))
        .thenReturn(java.util.List.of());

    service.candidateTasks(request);

    verify(selectionMapper)
        .tasks(1L, java.util.List.of(1L), tomorrow, tomorrow.plusDays(3));
  }

  private NaturalLanguagePlanParseRequest request(String text) {
    return new NaturalLanguagePlanParseRequest(
        text, TODAY, LocalTime.of(9, 0), 120);
  }

  private void available(String response) {
    when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
    when(gateway.generate(anyString(), anyString())).thenReturn(response);
  }
}
