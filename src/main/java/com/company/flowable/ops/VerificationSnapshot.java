package com.company.flowable.ops;

public class VerificationSnapshot {
    private final long processInstanceCount;
    private final long taskCount;
    private final long jobCount;
    private final long timerCount;
    private final long executionCount;

    public VerificationSnapshot(long processInstanceCount, long taskCount, long jobCount, long timerCount, long executionCount) {
        this.processInstanceCount = processInstanceCount;
        this.taskCount = taskCount;
        this.jobCount = jobCount;
        this.timerCount = timerCount;
        this.executionCount = executionCount;
    }

    public long getProcessInstanceCount() {
        return processInstanceCount;
    }

    public long getTaskCount() {
        return taskCount;
    }

    public long getJobCount() {
        return jobCount;
    }

    public long getTimerCount() {
        return timerCount;
    }

    public long getExecutionCount() {
        return executionCount;
    }

    public boolean isDeleted() {
        return processInstanceCount == 0 && taskCount == 0 && jobCount == 0 && timerCount == 0 && executionCount == 0;
    }
}
