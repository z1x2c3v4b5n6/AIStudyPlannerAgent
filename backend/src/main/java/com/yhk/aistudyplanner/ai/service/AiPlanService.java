package com.yhk.aistudyplanner.ai.service;

import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.*;
import com.yhk.aistudyplanner.ai.validator.AiPlanResponseValidator;
import com.yhk.aistudyplanner.ai.vo.*;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.*;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.service.RuleBasedPlanGenerator;
import java.time.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AiPlanService {
  private static final Logger log = LoggerFactory.getLogger(AiPlanService.class);
  private final AiPlanningProperties properties;
  private final ObjectProvider<PlanningGateway> gateway;
  private final AuthSessionService session;
  private final AiPlanningContextService contextService;
  private final AiPlanPromptService promptService;
  private final AiPlanResponseParser parser;
  private final AiPlanResponseValidator validator;
  private final RuleBasedPlanGenerator rules;
  private final Clock clock;

  public AiPlanService(
      AiPlanningProperties p,
      ObjectProvider<PlanningGateway> g,
      AuthSessionService s,
      AiPlanningContextService c,
      AiPlanPromptService ps,
      AiPlanResponseParser parser,
      AiPlanResponseValidator v,
      RuleBasedPlanGenerator r,
      Clock clock) {
    properties = p;
    gateway = g;
    session = s;
    contextService = c;
    promptService = ps;
    this.parser = parser;
    validator = v;
    rules = r;
    this.clock = clock;
  }

  public AiPlanDraftView generate(PlanDraftRequest request) {
    validateRequest(request);
    long userId = session.currentUserId();
    var context = contextService.build(userId, request.planDate());
    if (context.tasks().isEmpty())
      return response("RULE", false, null, rules.generate(userId, request));
    if (!properties.configured() || gateway.getIfAvailable() == null)
      return fallback(userId, request, ErrorCode.AI_NOT_CONFIGURED);
    long started = System.nanoTime();
    try {
      String content =
          gateway
              .getObject()
              .generate(promptService.system(), promptService.user(request, context));
      var validation = validator.validateWithReport(request, context, parser.parse(content));
      var draft = validation.draft();
      if (validation.adjustedItems() > 0) {
        log.info(
            "AI planning duration normalized userId={}, adjustedItems={}",
            userId,
            validation.adjustedItems());
      }
      log.info(
          "AI planning userId={}, model={}, elapsedMs={}, fallback=false, candidates={}, items={}",
          userId,
          properties.getModel(),
          elapsed(started),
          context.tasks().size(),
          draft.items().size());
      return response("AI", false, null, draft);
    } catch (AiProviderException e) {
      log.info(
          "AI planning userId={}, model={}, elapsedMs={}, fallback=true, category={},"
              + " candidates={}",
          userId,
          properties.getModel(),
          elapsed(started),
          e.category(),
          context.tasks().size());
      return fallback(userId, request, e.code());
    } catch (Exception e) {
      log.warn(
          "AI planning failed userId={}, model={}, fallback=true, category={}",
          userId,
          properties.getModel(),
          ErrorCode.AI_PROVIDER_UNAVAILABLE.name());
      return fallback(userId, request, ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }
  }

  private void validateRequest(PlanDraftRequest r) {
    if (r.planDate().isBefore(LocalDate.now(clock)))
      throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
    if (!LocalDateTime.of(r.planDate(), r.startTime())
        .plusMinutes(r.availableMinutes())
        .toLocalDate()
        .equals(r.planDate())) throw new BusinessException(ErrorCode.INVALID_PLAN_TIME);
  }

  private AiPlanDraftView fallback(long userId, PlanDraftRequest request, ErrorCode reason) {
    return response("RULE", true, message(reason), rules.generate(userId, request));
  }

  private AiPlanDraftView response(
      String type,
      boolean used,
      String reason,
      com.yhk.aistudyplanner.plan.vo.PlanDraftView draft) {
    return new AiPlanDraftView(type, "DEEPSEEK", properties.getModel(), used, reason, draft);
  }

  private long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  private String message(ErrorCode code) {
    return switch (code) {
      case AI_NOT_CONFIGURED -> "AI功能未配置，已使用规则生成";
      case AI_PROVIDER_TIMEOUT -> "AI服务响应超时，已使用规则生成";
      case AI_PROVIDER_RATE_LIMITED -> "AI服务请求繁忙，已使用规则生成";
      case AI_RESPONSE_EMPTY -> "AI返回内容为空，已使用规则生成";
      case AI_RESPONSE_INVALID -> "AI响应格式或内容无效，已使用规则生成";
      default -> "AI服务暂时不可用，已使用规则生成";
    };
  }
}
