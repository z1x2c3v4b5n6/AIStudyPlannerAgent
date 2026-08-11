package com.yhk.aistudyplanner.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.dto.CandidateTasksRequest;
import com.yhk.aistudyplanner.ai.dto.NaturalLanguagePlanModelResponse;
import com.yhk.aistudyplanner.ai.dto.NaturalLanguagePlanParseRequest;
import com.yhk.aistudyplanner.ai.gateway.PlanningGateway;
import com.yhk.aistudyplanner.ai.mapper.PlanningSelectionMapper;
import com.yhk.aistudyplanner.ai.vo.CandidateSubjectView;
import com.yhk.aistudyplanner.ai.vo.NaturalLanguagePlanParseView;
import com.yhk.aistudyplanner.ai.vo.SelectableTaskView;
import com.yhk.aistudyplanner.ai.vo.SubjectMatchData;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NaturalLanguagePlanService {
  private static final Logger log = LoggerFactory.getLogger(NaturalLanguagePlanService.class);
  private static final int MAX_AVAILABLE_MINUTES = 720;

  private final AiPlanningProperties properties;
  private final ObjectProvider<PlanningGateway> gatewayProvider;
  private final NaturalLanguagePlanPromptService promptService;
  private final PlanningSelectionMapper selectionMapper;
  private final AuthSessionService sessionService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public NaturalLanguagePlanService(
      AiPlanningProperties properties,
      ObjectProvider<PlanningGateway> gatewayProvider,
      NaturalLanguagePlanPromptService promptService,
      PlanningSelectionMapper selectionMapper,
      AuthSessionService sessionService,
      ObjectMapper objectMapper,
      Clock clock) {
    this.properties = properties;
    this.gatewayProvider = gatewayProvider;
    this.promptService = promptService;
    this.selectionMapper = selectionMapper;
    this.sessionService = sessionService;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public NaturalLanguagePlanParseView parse(NaturalLanguagePlanParseRequest request) {
    LocalDate today = LocalDate.now(clock);
    PlanningGateway gateway = gatewayProvider.getIfAvailable();
    if (!properties.configured() || gateway == null) {
      return unavailableResult(request);
    }

    try {
      String content =
          gateway.generate(
              promptService.system(),
              promptService.user(request, today, clock.getZone().getId()));
      NaturalLanguagePlanModelResponse model = parseModelResponse(content);
      return validateAndMerge(request, model, today);
    } catch (Exception exception) {
      log.info(
          "Natural language planning parse unavailable category={}",
          exception.getClass().getSimpleName());
      return unavailableResult(request);
    }
  }

  public List<SelectableTaskView> candidateTasks(CandidateTasksRequest request) {
    long userId = sessionService.currentUserId();
    List<Long> selectedIds = request.selectedSubjectIds().stream().distinct().toList();
    List<SubjectMatchData> ownedSubjects = selectionMapper.subjects(userId);
    Set<Long> ownedIds =
        ownedSubjects.stream().map(SubjectMatchData::subjectId).collect(java.util.stream.Collectors.toSet());
    if (selectedIds.size() != request.selectedSubjectIds().size()
        || !ownedIds.containsAll(selectedIds)) {
      throw new BusinessException(ErrorCode.SUBJECT_ACCESS_DENIED);
    }
    return selectionMapper.tasks(
        userId, selectedIds, request.planDate(), request.planDate().plusDays(3));
  }

  private NaturalLanguagePlanModelResponse parseModelResponse(String content) {
    if (!StringUtils.hasText(content)) {
      throw new IllegalArgumentException("Empty AI parse response");
    }
    String json = content.trim();
    if (json.startsWith("```")) {
      json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
    int start = json.indexOf('{');
    int end = json.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new IllegalArgumentException("Invalid AI parse response");
    }
    try {
      return objectMapper.readValue(
          json.substring(start, end + 1), NaturalLanguagePlanModelResponse.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid AI parse response", exception);
    }
  }

  private NaturalLanguagePlanParseView validateAndMerge(
      NaturalLanguagePlanParseRequest request,
      NaturalLanguagePlanModelResponse model,
      LocalDate today) {
    List<String> issues = new ArrayList<>();
    LocalDate planDate = parseDate(model.planDate(), issues);
    LocalTime startTime = parseTime(model.startTime(), issues);
    Integer availableMinutes = model.availableMinutes();

    if (planDate != null && planDate.isBefore(today)) {
      issues.add("计划日期不能早于今天");
      planDate = null;
    }
    if (availableMinutes != null
        && (availableMinutes < 1 || availableMinutes > MAX_AVAILABLE_MINUTES)) {
      issues.add("学习时长必须在1至720分钟之间");
      availableMinutes = null;
    }

    boolean modelMissingDate = planDate == null;
    boolean modelMissingTime = startTime == null;
    boolean modelMissingDuration = availableMinutes == null;
    if (planDate == null) planDate = validFallbackDate(request.fallbackPlanDate(), today);
    if (startTime == null) startTime = request.fallbackStartTime();
    if (availableMinutes == null) {
      availableMinutes = validFallbackDuration(request.fallbackAvailableMinutes());
    }

    if (modelMissingDate) issues.add("请确认计划日期");
    if (modelMissingTime) issues.add("请确认开始时间");
    if (modelMissingDuration) issues.add("请确认可用学习时长");

    if (planDate != null && startTime != null && availableMinutes != null) {
      LocalDateTime end = LocalDateTime.of(planDate, startTime).plusMinutes(availableMinutes);
      if (!end.toLocalDate().equals(planDate)) {
        issues.add("开始时间和学习时长不能跨越自然日");
        availableMinutes = null;
      }
    }

    if (Boolean.TRUE.equals(model.needsClarification())
        && StringUtils.hasText(model.clarificationMessage())) {
      issues.add(model.clarificationMessage().trim());
    }

    List<String> preferences =
        model.preferences() == null
            ? List.of()
            : model.preferences().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(10)
                .toList();
    List<String> topicKeywords = sanitizeKeywords(model.topicKeywords());
    SubjectResolution subjectResolution =
        resolveSubjects(
            request.text(),
            topicKeywords,
            selectionMapper.subjects(sessionService.currentUserId()));
    if (subjectResolution.needsSelection()) {
      issues.add(
          subjectResolution.candidates().isEmpty()
              ? "没有找到相关科目，请重新选择或先创建相关科目和任务"
              : "请确认要学习的科目");
    }
    String requirement =
        StringUtils.hasText(model.requirement())
            ? model.requirement().trim()
            : request.text().trim();

    return new NaturalLanguagePlanParseView(
        planDate,
        startTime,
        availableMinutes,
        requirement,
        preferences,
        topicKeywords,
        subjectResolution.candidates(),
        subjectResolution.ambiguousTopics(),
        subjectResolution.needsSelection(),
        subjectResolution.selectedSubjectIds(),
        subjectResolution.unmatchedKeywords(),
        !issues.isEmpty(),
        issues.isEmpty() ? null : String.join("；", issues.stream().distinct().toList()),
        true);
  }

  private LocalDate parseDate(String value, List<String> issues) {
    if (!StringUtils.hasText(value)) return null;
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeException exception) {
      issues.add("无法识别计划日期");
      return null;
    }
  }

  private LocalTime parseTime(String value, List<String> issues) {
    if (!StringUtils.hasText(value)) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (DateTimeException exception) {
      issues.add("无法识别开始时间");
      return null;
    }
  }

  private LocalDate validFallbackDate(LocalDate value, LocalDate today) {
    return value != null && !value.isBefore(today) ? value : null;
  }

  private Integer validFallbackDuration(Integer value) {
    return value != null && value >= 1 && value <= MAX_AVAILABLE_MINUTES ? value : null;
  }

  private NaturalLanguagePlanParseView unavailableResult(
      NaturalLanguagePlanParseRequest request) {
    LocalDate today = LocalDate.now(clock);
    LocalDate planDate = validFallbackDate(request.fallbackPlanDate(), today);
    LocalTime startTime = request.fallbackStartTime();
    Integer availableMinutes = validFallbackDuration(request.fallbackAvailableMinutes());
    String message = "AI暂时无法解析，请检查并补充高级设置后继续生成计划";
    if (planDate != null && startTime != null && availableMinutes != null) {
      LocalDateTime end = LocalDateTime.of(planDate, startTime).plusMinutes(availableMinutes);
      if (!end.toLocalDate().equals(planDate)) {
        availableMinutes = null;
        message += "；开始时间和学习时长不能跨越自然日";
      }
    }
    List<CandidateSubjectView> manualCandidates =
        selectionMapper.subjects(sessionService.currentUserId()).stream()
            .map(
                subject ->
                    new CandidateSubjectView(
                        subject.subjectId(),
                        subject.subjectName(),
                        subject.subjectColor(),
                        subject.pendingTaskCount(),
                        "AI暂时无法识别主题，请手动确认",
                        "RELATED",
                        false))
            .toList();
    return new NaturalLanguagePlanParseView(
        planDate,
        startTime,
        availableMinutes,
        request.text().trim(),
        List.of(),
        List.of(),
        manualCandidates,
        List.of(),
        true,
        List.of(),
        List.of(),
        true,
        message,
        false);
  }

  private List<String> sanitizeKeywords(List<String> keywords) {
    if (keywords == null) return List.of();
    return keywords.stream()
        .filter(StringUtils::hasText)
        .map(String::trim)
        .filter(value -> value.length() <= 100)
        .distinct()
        .limit(20)
        .toList();
  }

  private SubjectResolution resolveSubjects(
      String input, List<String> keywords, List<SubjectMatchData> subjects) {
    Map<Long, MatchCandidate> matches = new LinkedHashMap<>();
    Set<Long> selectedIds = new LinkedHashSet<>();
    List<String> ambiguous = new ArrayList<>();
    List<String> unmatched = new ArrayList<>();
    String normalizedInput = normalize(input);
    boolean selectAllRelated =
        input.contains("全部") || input.contains("所有") || input.contains("都学");

    for (String keyword : keywords) {
      String normalizedKeyword = normalize(keyword);
      List<SubjectMatchData> exact =
          subjects.stream()
              .filter(subject -> normalize(subject.subjectName()).equals(normalizedKeyword))
              .toList();
      if (!exact.isEmpty()) {
        exact.forEach(
            subject -> {
              putCandidate(matches, subject, keyword, "EXACT", "与学习主题精确匹配", true);
              selectedIds.add(subject.subjectId());
            });
        continue;
      }

      List<SubjectMatchData> related =
          subjects.stream()
              .filter(subject -> normalize(subject.subjectName()).contains(normalizedKeyword))
              .toList();
      if (related.isEmpty()) {
        unmatched.add(keyword);
        continue;
      }
      if (related.size() == 1) {
        SubjectMatchData subject = related.get(0);
        putCandidate(matches, subject, keyword, "UNIQUE", "唯一相关科目", true);
        selectedIds.add(subject.subjectId());
        continue;
      }

      boolean allNamesExplicit =
          related.stream().allMatch(subject -> normalizedInput.contains(normalize(subject.subjectName())));
      boolean explicitMultiple =
          selectAllRelated || (allNamesExplicit && (input.contains("和") || input.contains("、")));
      SubjectMatchData recommended =
          related.stream()
              .max(
                  Comparator.comparingLong(SubjectMatchData::pendingTaskCount)
                      .thenComparing(SubjectMatchData::subjectId, Comparator.reverseOrder()))
              .orElse(related.get(0));
      for (SubjectMatchData subject : related) {
        boolean recommendedByProgress = subject.subjectId().equals(recommended.subjectId());
        putCandidate(
            matches,
            subject,
            keyword,
            "RELATED",
            recommendedByProgress ? "相关科目，按当前待办进度推荐" : "名称包含该学习主题",
            recommendedByProgress);
        if (explicitMultiple) selectedIds.add(subject.subjectId());
      }
      if (!explicitMultiple) ambiguous.add(keyword);
    }

    boolean needsSelection =
        !ambiguous.isEmpty() || selectedIds.isEmpty() || !unmatched.isEmpty();
    return new SubjectResolution(
        matches.values().stream().map(MatchCandidate::view).toList(),
        List.copyOf(ambiguous),
        needsSelection,
        List.copyOf(selectedIds),
        List.copyOf(unmatched));
  }

  private void putCandidate(
      Map<Long, MatchCandidate> target,
      SubjectMatchData subject,
      String keyword,
      String level,
      String reason,
      boolean recommended) {
    CandidateSubjectView view =
        new CandidateSubjectView(
            subject.subjectId(),
            subject.subjectName(),
            subject.subjectColor(),
            subject.pendingTaskCount(),
            "“" + keyword + "”： " + reason,
            level,
            recommended);
    MatchCandidate existing = target.get(subject.subjectId());
    if (existing == null || rank(level) > rank(existing.view().matchLevel())) {
      target.put(subject.subjectId(), new MatchCandidate(view));
    }
  }

  private int rank(String level) {
    return switch (level) {
      case "EXACT" -> 3;
      case "UNIQUE" -> 2;
      default -> 1;
    };
  }

  private String normalize(String value) {
    return value == null
        ? ""
        : value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_]+", "");
  }

  private record MatchCandidate(CandidateSubjectView view) {}

  private record SubjectResolution(
      List<CandidateSubjectView> candidates,
      List<String> ambiguousTopics,
      boolean needsSelection,
      List<Long> selectedSubjectIds,
      List<String> unmatchedKeywords) {}
}
