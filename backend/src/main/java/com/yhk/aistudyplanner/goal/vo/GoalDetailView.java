package com.yhk.aistudyplanner.goal.vo;

public record GoalDetailView(GoalView goal, long taskCount, long completedTaskCount, double completionRate) {
}

