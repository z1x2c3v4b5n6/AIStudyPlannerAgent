package com.yhk.aistudyplanner.adaptive;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LearningDiagnostics {
    public record Diagnostic(String diagnosticId, String type, String severity, String title, String description,
                             List<String> sourceReferences, Map<String, Number> evidence, String ruleVersion,
                             String recommendation, Instant generatedAt) {}

    public List<Diagnostic> analyze(LearningProfileService.Profile profile, Integer dailyBudget) {
        if (dailyBudget != null && (dailyBudget < 15 || dailyBudget > 720)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        var diagnostics = new ArrayList<Diagnostic>();
        var tasks = profile.tasks();
        var plans = profile.plans();
        int streak = 0;
        for (int i=profile.trend().size()-1; i>=0 && profile.trend().get(i).totalMinutes()>0; i--) streak++;
        boolean sufficient = profile.activity().activeDays() >= 2 && profile.priorWindowMinutes() > 0;
        boolean decline = sufficient && profile.activity().totalMinutes() < 0.7 * profile.priorWindowMinutes();
        add(diagnostics, profile, "continuity", "学习记录连续性", sufficient ? decline ? "ATTENTION" : "INFO" : "INSUFFICIENT_SAMPLE",
                sufficient ? (decline ? "记录时长较等长前一窗口下降超过30%" : "未触发记录时长下降规则")
                        : "当前记录天数或前一窗口时长不足以比较",
                Map.of("recordedDays",profile.activity().activeDays(),"streak",streak,
                        "actualMinutes",profile.activity().totalMinutes(),"priorMinutes",profile.priorWindowMinutes()),
                List.of("study_record"), "检查是否有未补录记录，再调整学习安排。");
        add(diagnostics, profile, "backlog", "待办积压",
                tasks.overdueNow() > 0 ? "HIGH" : tasks.unplannedHighPriority() > 0 ? "ATTENTION" : "INFO",
                "按当前任务状态、截止时间与优先级统计",
                Map.of("overdue",tasks.overdueNow(),"dueSoon",tasks.dueNextThreeDays(),"unplannedHigh",tasks.unplannedHighPriority()),
                List.of("study_task"), "先核对逾期和高优先级任务，再选择需要安排的任务。");
        add(diagnostics, profile, "plan-deviation", "计划与实际时长",
                plans.pairedItems() == 0 ? "INSUFFICIENT_SAMPLE"
                        : Math.abs(plans.deviationRatio()) >= .25 ? "ATTENTION" : "INFO",
                plans.pairedItems() == 0 ? "没有带实际学习记录的非跳过计划项" : "仅比较能匹配实际学习记录的计划项",
                Map.of("pairedItems",plans.pairedItems(),"plannedMinutes",plans.pairedPlannedMinutes(),
                        "actualMinutes",plans.pairedActualMinutes(),"deviationMinutes",plans.deviationMinutes(),
                        "missingCompletedRecords",plans.missingCompletedRecords()),
                List.of("study_plan","study_plan_item","study_record"), "优先核对缺失记录，再根据有效样本调整预计时长。");
        var capacity = new LinkedHashMap<String, Number>();
        capacity.put("requiredMinutes", tasks.openEstimatedNextThreeDays());
        capacity.put("observedDailyMinutes", profile.activity().averageDailyMinutes());
        long deficit = dailyBudget == null ? 0 : Math.max(0, tasks.openEstimatedNextThreeDays() - 3L * dailyBudget);
        if (dailyBudget != null) {
            capacity.put("availableMinutes",3L * dailyBudget);
            capacity.put("deficitMinutes",deficit);
        }
        add(diagnostics,profile,"capacity","未来三天容量",dailyBudget == null ? "INSUFFICIENT_SAMPLE" : deficit > 0 ? "HIGH" : "INFO",
                dailyBudget == null ? "缺少用户设定的可用时间" : "比较积压和三天内任务需求与用户提供的三天预算",
                capacity,List.of("study_task","用户输入的每日可用分钟"),
                "补充可用时间或比较规划方案。历史日均记录时长仅用于对照。");
        return List.copyOf(diagnostics);
    }

    private static void add(List<Diagnostic> items, LearningProfileService.Profile profile, String type,
                            String title, String severity, String description, Map<String, Number> evidence,
                            List<String> sources, String recommendation) {
        items.add(new Diagnostic(type + "-" + profile.window().to(), type, severity, title, description,
                sources, evidence, "c9-rules-v1", recommendation, profile.generatedAt()));
    }
}
