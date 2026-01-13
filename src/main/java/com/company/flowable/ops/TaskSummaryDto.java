package com.company.flowable.ops;

import java.time.Instant;

public class TaskSummaryDto {
    private String taskId;
    private String name;
    private String assignee;
    private Instant createTime;
    private long ageHours;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public long getAgeHours() {
        return ageHours;
    }

    public void setAgeHours(long ageHours) {
        this.ageHours = ageHours;
    }
}
