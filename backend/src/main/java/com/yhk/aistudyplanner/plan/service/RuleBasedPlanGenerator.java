package com.yhk.aistudyplanner.plan.service;

import com.yhk.aistudyplanner.common.exception.*;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import com.yhk.aistudyplanner.plan.vo.*;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import org.springframework.stereotype.Component;
import java.time.*; import java.util.*;

@Component
public class RuleBasedPlanGenerator {
    private final StudyPlanMapper mapper; private final Clock clock;
    public RuleBasedPlanGenerator(StudyPlanMapper mapper, Clock clock){this.mapper=mapper;this.clock=clock;}

    public PlanDraftView generate(long userId, PlanDraftRequest request) {
        LocalDate today=LocalDate.now(clock);
        if(request.planDate().isBefore(today)) throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
        LocalDateTime cursor=LocalDateTime.of(request.planDate(),request.startTime());
        LocalDateTime limit=cursor.plusMinutes(request.availableMinutes());
        if(!limit.toLocalDate().equals(request.planDate()))
            throw new BusinessException(ErrorCode.INVALID_PLAN_TIME);
        List<PlanDraftItemView> items=new ArrayList<>(); int remaining=request.availableMinutes();
        for(PlanTaskCandidate task:mapper.selectCandidates(userId,request.planDate(),request.planDate().atStartOfDay())){
            if(remaining<15) break;
            int minutes=Math.min(Math.max(15,task.estimatedMinutes()),remaining);
            LocalDateTime end=cursor.plusMinutes(minutes);
            if(!end.toLocalDate().equals(request.planDate())) break;
            items.add(new PlanDraftItemView(items.size()+1,task.taskId(),task.taskTitle(),task.subjectId(),
                    task.subjectName(),task.subjectColor(),cursor,end,minutes,reason(task,request.planDate())));
            cursor=end; remaining-=minutes;
        }
        int planned=request.availableMinutes()-remaining;
        String summary=items.isEmpty()?"暂无符合条件的未完成任务":String.format("共安排%d项任务，计划学习%d分钟",items.size(),planned);
        return new PlanDraftView(UUID.randomUUID().toString(),request.planDate(),request.startTime(),
                request.availableMinutes(),planned,trim(request.requirement()),summary,List.copyOf(items));
    }
    private String reason(PlanTaskCandidate task, LocalDate date){
        if(task.dueAt()!=null && task.dueAt().isBefore(date.atStartOfDay())) return "任务已逾期";
        if(task.dueAt()!=null && !task.dueAt().isAfter(date.plusDays(3).atStartOfDay())) return "即将截止";
        if(task.priority()>=3) return "高优先级任务";
        if(task.plannedDate()!=null && task.plannedDate().isBefore(date)) return "历史未完成任务";
        if(task.status()==TaskStatus.IN_PROGRESS) return "进行中的任务";
        return "普通待办任务";
    }
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
}
