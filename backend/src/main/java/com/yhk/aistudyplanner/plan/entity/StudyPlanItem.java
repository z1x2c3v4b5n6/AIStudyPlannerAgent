package com.yhk.aistudyplanner.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("study_plan_item")
public class StudyPlanItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long planId; private Long taskId; private Integer sequenceNo;
    private LocalDateTime startAt; private LocalDateTime endAt; private Integer plannedMinutes;
    private String reason; private PlanItemStatus status; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public Long getPlanId(){return planId;} public void setPlanId(Long v){planId=v;} public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;}
    public Integer getSequenceNo(){return sequenceNo;} public void setSequenceNo(Integer v){sequenceNo=v;} public LocalDateTime getStartAt(){return startAt;} public void setStartAt(LocalDateTime v){startAt=v;} public LocalDateTime getEndAt(){return endAt;} public void setEndAt(LocalDateTime v){endAt=v;}
    public Integer getPlannedMinutes(){return plannedMinutes;} public void setPlannedMinutes(Integer v){plannedMinutes=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} public PlanItemStatus getStatus(){return status;} public void setStatus(PlanItemStatus v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
