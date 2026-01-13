package com.company.flowable.ops;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Candidate {
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Instant startTime;
    private long hoursRunning;
    private String starterUserId;
    private String starterName;
    private String starterEmail;
    private boolean isSubprocess;
    private String parentPid;
    private final List<TaskSummary> tasks = new ArrayList<>();
    private List<String> activeActivityIds = new ArrayList<>();
    private int jobCount;
    private int overdueJobCount;
    private int timerCount;
    private int overdueTimerCount;
    private Classification classification;
    private RecommendedAction recommendedAction;

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    public void setProcessDefinitionKey(String processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public long getHoursRunning() {
        return hoursRunning;
    }

    public void setHoursRunning(long hoursRunning) {
        this.hoursRunning = hoursRunning;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getStarterName() {
        return starterName;
    }

    public void setStarterName(String starterName) {
        this.starterName = starterName;
    }

    public String getStarterEmail() {
        return starterEmail;
    }

    public void setStarterEmail(String starterEmail) {
        this.starterEmail = starterEmail;
    }

    public boolean isSubprocess() {
        return isSubprocess;
    }

    public void setSubprocess(boolean subprocess) {
        isSubprocess = subprocess;
    }

    public String getParentPid() {
        return parentPid;
    }

    public void setParentPid(String parentPid) {
        this.parentPid = parentPid;
    }

    public List<TaskSummary> getTasks() {
        return tasks;
    }

    public int getOpenTasksCount() {
        return tasks.size();
    }

    public Long getOldestTaskAgeHours() {
        if (tasks.isEmpty()) {
            return null;
        }
        long max = 0;
        for (TaskSummary t : tasks) {
            if (t.getAgeHours() > max) {
                max = t.getAgeHours();
            }
        }
        return max;
    }

    public List<String> getActiveActivityIds() {
        return activeActivityIds;
    }

    public void setActiveActivityIds(List<String> activeActivityIds) {
        this.activeActivityIds = activeActivityIds == null ? new ArrayList<>() : activeActivityIds;
    }

    public int getJobCount() {
        return jobCount;
    }

    public void setJobCount(int jobCount) {
        this.jobCount = jobCount;
    }

    public int getOverdueJobCount() {
        return overdueJobCount;
    }

    public void setOverdueJobCount(int overdueJobCount) {
        this.overdueJobCount = overdueJobCount;
    }

    public int getTimerCount() {
        return timerCount;
    }

    public void setTimerCount(int timerCount) {
        this.timerCount = timerCount;
    }

    public int getOverdueTimerCount() {
        return overdueTimerCount;
    }

    public void setOverdueTimerCount(int overdueTimerCount) {
        this.overdueTimerCount = overdueTimerCount;
    }

    public Classification getClassification() {
        return classification;
    }

    public void setClassification(Classification classification) {
        this.classification = classification;
    }

    public RecommendedAction getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(RecommendedAction recommendedAction) {
        this.recommendedAction = recommendedAction;
    }
}
