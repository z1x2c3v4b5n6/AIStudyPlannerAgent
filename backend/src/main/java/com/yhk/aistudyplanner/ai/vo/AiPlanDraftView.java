package com.yhk.aistudyplanner.ai.vo;

import com.yhk.aistudyplanner.plan.vo.PlanDraftView;

public record AiPlanDraftView(
    String generatorType,
    String provider,
    String model,
    boolean fallbackUsed,
    String fallbackReason,
    PlanDraftView draft) {}
