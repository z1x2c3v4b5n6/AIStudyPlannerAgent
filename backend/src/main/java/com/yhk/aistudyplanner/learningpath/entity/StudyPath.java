package com.yhk.aistudyplanner.learningpath.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("study_path")
public class StudyPath {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long subjectId;
    private String title;
    private String summary;
    private String originalRequirement;
    private String goal;
    private String currentLevel;
    private String targetLevel;
    private String purpose;
    private String preferences;
    private LocalDate startDate;
    private LocalDate targetDate;
    private Integer dailyMinutes;
    private LearningPathStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getSubjectId(){return subjectId;} public void setSubjectId(Long v){subjectId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getOriginalRequirement(){return originalRequirement;} public void setOriginalRequirement(String v){originalRequirement=v;}
    public String getGoal(){return goal;} public void setGoal(String v){goal=v;}
    public String getCurrentLevel(){return currentLevel;} public void setCurrentLevel(String v){currentLevel=v;}
    public String getTargetLevel(){return targetLevel;} public void setTargetLevel(String v){targetLevel=v;}
    public String getPurpose(){return purpose;} public void setPurpose(String v){purpose=v;}
    public String getPreferences(){return preferences;} public void setPreferences(String v){preferences=v;}
    public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;}
    public LocalDate getTargetDate(){return targetDate;} public void setTargetDate(LocalDate v){targetDate=v;}
    public Integer getDailyMinutes(){return dailyMinutes;} public void setDailyMinutes(Integer v){dailyMinutes=v;}
    public LearningPathStatus getStatus(){return status;} public void setStatus(LearningPathStatus v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
