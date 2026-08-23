package com.yhk.aistudyplanner.learningpath.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("study_path_item")
public class StudyPathItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long pathId;
    private Integer sequenceNo;
    private Integer stageNo;
    private String stageTitle;
    private String stageDescription;
    private String topic;
    private String learningObjective;
    private String learningMethod;
    private String completionCriteria;
    private Integer estimatedMinutes;
    private Integer suggestedDay;
    private String prerequisiteText;
    private String reason;
    private LearningPathItemStatus status;
    private Long taskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getPathId(){return pathId;} public void setPathId(Long v){pathId=v;}
    public Integer getSequenceNo(){return sequenceNo;} public void setSequenceNo(Integer v){sequenceNo=v;}
    public Integer getStageNo(){return stageNo;} public void setStageNo(Integer v){stageNo=v;}
    public String getStageTitle(){return stageTitle;} public void setStageTitle(String v){stageTitle=v;}
    public String getStageDescription(){return stageDescription;} public void setStageDescription(String v){stageDescription=v;}
    public String getTopic(){return topic;} public void setTopic(String v){topic=v;}
    public String getLearningObjective(){return learningObjective;} public void setLearningObjective(String v){learningObjective=v;}
    public String getLearningMethod(){return learningMethod;} public void setLearningMethod(String v){learningMethod=v;}
    public String getCompletionCriteria(){return completionCriteria;} public void setCompletionCriteria(String v){completionCriteria=v;}
    public Integer getEstimatedMinutes(){return estimatedMinutes;} public void setEstimatedMinutes(Integer v){estimatedMinutes=v;}
    public Integer getSuggestedDay(){return suggestedDay;} public void setSuggestedDay(Integer v){suggestedDay=v;}
    public String getPrerequisiteText(){return prerequisiteText;} public void setPrerequisiteText(String v){prerequisiteText=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public LearningPathItemStatus getStatus(){return status;} public void setStatus(LearningPathItemStatus v){status=v;}
    public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
