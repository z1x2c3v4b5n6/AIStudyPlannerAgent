package com.yhk.aistudyplanner.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("study_plan")
public class StudyPlan {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private String sourceDraftId; private LocalDate planDate;
    private Integer availableMinutes; private Integer plannedMinutes; private String requirement;
    private String summary; private PlanStatus status; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getSourceDraftId(){return sourceDraftId;} public void setSourceDraftId(String v){sourceDraftId=v;} public LocalDate getPlanDate(){return planDate;} public void setPlanDate(LocalDate v){planDate=v;}
    public Integer getAvailableMinutes(){return availableMinutes;} public void setAvailableMinutes(Integer v){availableMinutes=v;} public Integer getPlannedMinutes(){return plannedMinutes;} public void setPlannedMinutes(Integer v){plannedMinutes=v;}
    public String getRequirement(){return requirement;} public void setRequirement(String v){requirement=v;} public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public PlanStatus getStatus(){return status;} public void setStatus(PlanStatus v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
