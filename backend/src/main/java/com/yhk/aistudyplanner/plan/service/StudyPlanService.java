package com.yhk.aistudyplanner.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.*;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.plan.dto.*;
import com.yhk.aistudyplanner.plan.entity.*;
import com.yhk.aistudyplanner.plan.mapper.*;
import com.yhk.aistudyplanner.plan.vo.*;
import com.yhk.aistudyplanner.task.entity.*;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.time.temporal.ChronoUnit; import java.util.*; import java.util.function.Function; import java.util.stream.Collectors;

@Service
public class StudyPlanService {
    private final StudyPlanMapper planMapper; private final StudyPlanItemMapper itemMapper;
    private final TaskMapper taskMapper; private final AuthSessionService session; private final RuleBasedPlanGenerator generator; private final Clock clock;
    public StudyPlanService(StudyPlanMapper p, StudyPlanItemMapper i, TaskMapper t, AuthSessionService s, RuleBasedPlanGenerator g, Clock c){planMapper=p;itemMapper=i;taskMapper=t;session=s;generator=g;clock=c;}

    @Transactional(readOnly=true) public PlanDraftView draft(PlanDraftRequest request){return generator.generate(session.currentUserId(),request);}

    @Transactional
    public PlanDetailView confirm(PlanConfirmRequest request){
        long userId=session.currentUserId(); StudyPlan existing=findByDraft(userId,request.draftId());
        if(existing!=null) return detail(existing,userId);
        validateConfirm(userId,request);
        LocalDateTime now=LocalDateTime.now(clock); StudyPlan plan=new StudyPlan();
        plan.setUserId(userId);plan.setSourceDraftId(request.draftId());plan.setPlanDate(request.planDate());
        plan.setAvailableMinutes(request.availableMinutes());plan.setPlannedMinutes(request.plannedMinutes());
        plan.setRequirement(trim(request.requirement()));plan.setSummary(request.summary().trim());plan.setStatus(PlanStatus.CONFIRMED);plan.setCreatedAt(now);plan.setUpdatedAt(now);
        try { planMapper.insert(plan); } catch(DuplicateKeyException ex){ StudyPlan duplicate=findByDraft(userId,request.draftId()); if(duplicate!=null)return detail(duplicate,userId); throw ex; }
        for(PlanConfirmItemRequest source:request.items().stream().sorted(Comparator.comparingInt(PlanConfirmItemRequest::sequenceNo)).toList()){
            StudyPlanItem item=new StudyPlanItem();item.setUserId(userId);item.setPlanId(plan.getId());item.setTaskId(source.taskId());item.setSequenceNo(source.sequenceNo());item.setStartAt(source.startAt());item.setEndAt(source.endAt());item.setPlannedMinutes(source.plannedMinutes());item.setReason(source.reason().trim());item.setStatus(PlanItemStatus.PENDING);item.setCreatedAt(now);item.setUpdatedAt(now);itemMapper.insert(item);
        }
        return detail(plan,userId);
    }

    @Transactional(readOnly=true)
    public PageResponse<PlanListView> list(long page,long pageSize,LocalDate start,LocalDate end,PlanStatus status){
        if(start!=null&&end!=null){if(start.isAfter(end))throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);if(ChronoUnit.DAYS.between(start,end)+1>366)throw new BusinessException(ErrorCode.DATE_RANGE_TOO_LARGE);}
        long userId=session.currentUserId(); var result=planMapper.selectPlanPage(new Page<>(page,pageSize),userId,start,end,status);
        return new PageResponse<>(result.getRecords(),page,pageSize,result.getTotal());
    }
    @Transactional(readOnly=true) public PlanDetailView get(long id){long userId=session.currentUserId();return detail(requireOwned(id,userId),userId);}

    @Transactional
    public PlanDetailView changeStatus(long id, PlanStatusRequest request){
        long userId=session.currentUserId();StudyPlan plan=requireOwned(id,userId);
        if(request.status()!=PlanStatus.CANCELLED||plan.getStatus()!=PlanStatus.CONFIRMED) throw new BusinessException(plan.getStatus()==PlanStatus.CANCELLED?ErrorCode.PLAN_ALREADY_CANCELLED:ErrorCode.INVALID_PLAN_STATUS_TRANSITION);
        updatePlanStatus(id,userId,PlanStatus.CANCELLED);plan.setStatus(PlanStatus.CANCELLED);plan.setUpdatedAt(LocalDateTime.now(clock));return detail(plan,userId);
    }
    @Transactional
    public PlanDetailView changeItemStatus(long planId,long itemId,PlanItemStatusRequest request){
        long userId=session.currentUserId();StudyPlan plan=requireOwned(planId,userId);
        if(plan.getStatus()==PlanStatus.CANCELLED)throw new BusinessException(ErrorCode.PLAN_ALREADY_CANCELLED);
        StudyPlanItem item=itemMapper.selectOne(new LambdaQueryWrapper<StudyPlanItem>().eq(StudyPlanItem::getId,itemId).eq(StudyPlanItem::getPlanId,planId).eq(StudyPlanItem::getUserId,userId));
        if(item==null)throw new BusinessException(ErrorCode.PLAN_ITEM_NOT_FOUND);
        if(!allowed(item.getStatus(),request.status()))throw new BusinessException(ErrorCode.INVALID_PLAN_ITEM_STATUS_TRANSITION);
        LocalDateTime now=LocalDateTime.now(clock);int updated=itemMapper.update(null,new LambdaUpdateWrapper<StudyPlanItem>().eq(StudyPlanItem::getId,itemId).eq(StudyPlanItem::getPlanId,planId).eq(StudyPlanItem::getUserId,userId).set(StudyPlanItem::getStatus,request.status()).set(StudyPlanItem::getUpdatedAt,now));
        if(updated!=1)throw new BusinessException(ErrorCode.PLAN_ITEM_NOT_FOUND);
        long pending=itemMapper.selectCount(new LambdaQueryWrapper<StudyPlanItem>().eq(StudyPlanItem::getPlanId,planId).eq(StudyPlanItem::getUserId,userId).eq(StudyPlanItem::getStatus,PlanItemStatus.PENDING));
        PlanStatus next=pending==0?PlanStatus.COMPLETED:PlanStatus.CONFIRMED;updatePlanStatus(planId,userId,next);plan.setStatus(next);plan.setUpdatedAt(now);return detail(plan,userId);
    }
    private boolean allowed(PlanItemStatus from,PlanItemStatus to){return from==PlanItemStatus.PENDING&&(to==PlanItemStatus.COMPLETED||to==PlanItemStatus.SKIPPED)||(from==PlanItemStatus.COMPLETED||from==PlanItemStatus.SKIPPED)&&to==PlanItemStatus.PENDING;}
    private void validateConfirm(long userId,PlanConfirmRequest request){
        if(request.items()==null||request.items().isEmpty())throw new BusinessException(ErrorCode.PLAN_DRAFT_EMPTY);
        if(request.planDate().isBefore(LocalDate.now(clock)))throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
        List<PlanConfirmItemRequest> items=request.items().stream().sorted(Comparator.comparingInt(PlanConfirmItemRequest::sequenceNo)).toList();
        Set<Long> ids=new HashSet<>();int total=0;LocalDateTime previous=null;
        for(int index=0;index<items.size();index++){var item=items.get(index);if(item.sequenceNo()!=index+1)throw new BusinessException(ErrorCode.INVALID_PLAN_TIME);if(!ids.add(item.taskId()))throw new BusinessException(ErrorCode.PLAN_TASK_DUPLICATED);
            if(!item.startAt().toLocalDate().equals(request.planDate())||!item.endAt().toLocalDate().equals(request.planDate())||!item.endAt().isAfter(item.startAt()))throw new BusinessException(ErrorCode.INVALID_PLAN_TIME);
            long minutes=ChronoUnit.MINUTES.between(item.startAt(),item.endAt());if(minutes!=item.plannedMinutes()||minutes<15)throw new BusinessException(ErrorCode.INVALID_PLAN_DURATION);
            if(previous!=null&&item.startAt().isBefore(previous))throw new BusinessException(ErrorCode.PLAN_TIME_OVERLAP);previous=item.endAt();total+=item.plannedMinutes();}
        if(total!=request.plannedMinutes()||total>request.availableMinutes())throw new BusinessException(ErrorCode.INVALID_PLAN_DURATION);
        List<StudyTask> tasks=taskMapper.selectList(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getUserId,userId).in(StudyTask::getId,ids).in(StudyTask::getStatus,TaskStatus.TODO,TaskStatus.IN_PROGRESS));
        if(tasks.size()!=ids.size())throw new BusinessException(ErrorCode.PLAN_TASK_INVALID);
    }
    private StudyPlan findByDraft(long userId,String draft){return planMapper.selectOne(new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getUserId,userId).eq(StudyPlan::getSourceDraftId,draft));}
    private StudyPlan requireOwned(long id,long userId){StudyPlan p=planMapper.selectOne(new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getId,id).eq(StudyPlan::getUserId,userId));if(p!=null)return p;if(planMapper.selectById(id)!=null)throw new BusinessException(ErrorCode.PLAN_ACCESS_DENIED);throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);}
    private void updatePlanStatus(long id,long userId,PlanStatus status){int n=planMapper.update(null,new LambdaUpdateWrapper<StudyPlan>().eq(StudyPlan::getId,id).eq(StudyPlan::getUserId,userId).set(StudyPlan::getStatus,status).set(StudyPlan::getUpdatedAt,LocalDateTime.now(clock)));if(n!=1)throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);}
    private PlanDetailView detail(StudyPlan p,long userId){return new PlanDetailView(p.getId(),p.getSourceDraftId(),p.getPlanDate(),p.getAvailableMinutes(),p.getPlannedMinutes(),p.getRequirement(),p.getSummary(),p.getStatus(),p.getCreatedAt(),p.getUpdatedAt(),itemMapper.selectViews(p.getId(),userId));}
    private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
}
