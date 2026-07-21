package com.yhk.aistudyplanner.task.vo;

import java.util.List;

public record TodayTasksView(List<TaskView> tasks, int totalEstimatedMinutes) {
}

