package com.company.flowable.ops;

import java.time.Instant;

public class TaskSummary {
    private final String taskId;
    private final String name;
    private final String assignee;
    private final Instant createTime;
    private final long ageHours;

    public TaskSummary(String taskId, String name, String assignee, Instant createTime, long ageHours) {
        this.taskId = taskId;
        this.name = name;
        this.assignee = assignee;
        this.createTime = createTime;
        this.ageHours = ageHours;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public String getAssignee() {
        return assignee;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public long getAgeHours() {
        return ageHours;
    }

    public String toShortString() {
        String safeName = name == null ? "" : name;
        String safeAssignee = assignee == null ? "" : assignee;
        return taskId + "|" + safeName + "|" + safeAssignee + "|" + ageHours + "h";
    }
}
