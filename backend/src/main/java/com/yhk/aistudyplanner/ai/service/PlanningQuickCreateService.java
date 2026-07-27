package com.yhk.aistudyplanner.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yhk.aistudyplanner.ai.dto.QuickCreatePlanTaskRequest;
import com.yhk.aistudyplanner.ai.mapper.PlanningSelectionMapper;
import com.yhk.aistudyplanner.ai.vo.QuickCreatePlanTaskView;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.subject.entity.Subject;
import com.yhk.aistudyplanner.subject.mapper.SubjectMapper;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlanningQuickCreateService {
  private static final String DEFAULT_SUBJECT_COLOR = "#409EFF";
  private static final int DEFAULT_PRIORITY = 2;

  private final PlanningSelectionMapper selectionMapper;
  private final SubjectMapper subjectMapper;
  private final TaskMapper taskMapper;
  private final AuthSessionService sessionService;
  private final Clock clock;

  public PlanningQuickCreateService(
      PlanningSelectionMapper selectionMapper,
      SubjectMapper subjectMapper,
      TaskMapper taskMapper,
      AuthSessionService sessionService,
      Clock clock) {
    this.selectionMapper = selectionMapper;
    this.subjectMapper = subjectMapper;
    this.taskMapper = taskMapper;
    this.sessionService = sessionService;
    this.clock = clock;
  }

  @Transactional
  public QuickCreatePlanTaskView createOrReuse(QuickCreatePlanTaskRequest request) {
    long userId = sessionService.currentUserId();
    validateDate(request.planDate());
    validateContent(request);
    String topicName = request.topicName().trim();
    String taskTitle = request.taskTitle().trim();
    selectionMapper.lockUser(userId);

    Subject subject =
        subjectMapper.selectOne(
            new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId)
                .eq(Subject::getName, topicName)
                .last("LIMIT 1"));
    boolean createdSubject = subject == null;
    if (createdSubject) {
      subject = createSubject(userId, topicName);
    }

    StudyTask existing =
        taskMapper.selectOne(
            new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getSubjectId, subject.getId())
                .eq(StudyTask::getTitle, taskTitle)
                .last("LIMIT 1"));
    if (existing != null) {
      if ((existing.getStatus() == TaskStatus.TODO
              || existing.getStatus() == TaskStatus.IN_PROGRESS)
          && (existing.getPlannedDate() == null
              || !existing.getPlannedDate().isAfter(request.planDate()))) {
        return view(subject, existing, createdSubject, false);
      }
      throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    StudyTask task = createTask(userId, subject.getId(), taskTitle, request);
    return view(subject, task, createdSubject, true);
  }

  private Subject createSubject(long userId, String name) {
    LocalDateTime now = LocalDateTime.now(clock);
    Subject subject = new Subject();
    subject.setUserId(userId);
    subject.setName(name);
    subject.setDescription(null);
    subject.setColor(DEFAULT_SUBJECT_COLOR);
    subject.setSortOrder(0);
    subject.setCreatedAt(now);
    subject.setUpdatedAt(now);
    try {
      subjectMapper.insert(subject);
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }
    return subject;
  }

  private StudyTask createTask(
      long userId,
      long subjectId,
      String title,
      QuickCreatePlanTaskRequest request) {
    LocalDateTime now = LocalDateTime.now(clock);
    StudyTask task = new StudyTask();
    task.setUserId(userId);
    task.setSubjectId(subjectId);
    task.setGoalId(null);
    task.setTitle(title);
    task.setDescription(trimToNull(request.requirement()));
    task.setPriority(DEFAULT_PRIORITY);
    task.setStatus(TaskStatus.TODO);
    task.setEstimatedMinutes(request.estimatedMinutes());
    task.setPlannedDate(request.planDate());
    task.setDueAt(null);
    task.setCompletedAt(null);
    task.setCreatedAt(now);
    task.setUpdatedAt(now);
    taskMapper.insert(task);
    return task;
  }

  private void validateDate(LocalDate planDate) {
    if (planDate == null || planDate.isBefore(LocalDate.now(clock))) {
      throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
    }
  }

  private void validateContent(QuickCreatePlanTaskRequest request) {
    if (!StringUtils.hasText(request.topicName())
        || request.topicName().trim().length() > 100
        || !StringUtils.hasText(request.taskTitle())
        || request.taskTitle().trim().length() > 200) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    if (request.estimatedMinutes() == null
        || request.estimatedMinutes() < 1
        || request.estimatedMinutes() > 720) {
      throw new BusinessException(ErrorCode.INVALID_PLAN_DURATION);
    }
  }

  private QuickCreatePlanTaskView view(
      Subject subject, StudyTask task, boolean createdSubject, boolean createdTask) {
    return new QuickCreatePlanTaskView(
        subject.getId(),
        subject.getName(),
        task.getId(),
        task.getTitle(),
        createdSubject,
        createdTask);
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) return null;
    return value.trim();
  }
}
