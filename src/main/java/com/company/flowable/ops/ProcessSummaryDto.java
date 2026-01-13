package com.company.flowable.ops;

import java.time.Instant;
import java.util.List;

public class ProcessSummaryDto {
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Instant startTime;
    private long hoursRunning;
    private String starterUserId;
    private String starterName;
    private String starterEmail;
    private boolean subprocess;
    private String parentProcessInstanceId;
    private int openTasksCount;
    private Long oldestTaskAgeHours;
    private List<String> taskSummaries;
    private List<String> activeActivityIds;
    private int jobCount;
    private int overdueJobCount;
    private int timerCount;
    private int overdueTimerCount;
    private RecommendedAction recommendedAction;
    private Classification classification;

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
        return subprocess;
    }

    public void setSubprocess(boolean subprocess) {
        this.subprocess = subprocess;
    }

    public String getParentProcessInstanceId() {
        return parentProcessInstanceId;
    }

    public void setParentProcessInstanceId(String parentProcessInstanceId) {
        this.parentProcessInstanceId = parentProcessInstanceId;
    }

    public int getOpenTasksCount() {
        return openTasksCount;
    }

    public void setOpenTasksCount(int openTasksCount) {
        this.openTasksCount = openTasksCount;
    }

    public Long getOldestTaskAgeHours() {
        return oldestTaskAgeHours;
    }

    public void setOldestTaskAgeHours(Long oldestTaskAgeHours) {
        this.oldestTaskAgeHours = oldestTaskAgeHours;
    }

    public List<String> getTaskSummaries() {
        return taskSummaries;
    }

    public void setTaskSummaries(List<String> taskSummaries) {
        this.taskSummaries = taskSummaries;
    }

    public List<String> getActiveActivityIds() {
        return activeActivityIds;
    }

    public void setActiveActivityIds(List<String> activeActivityIds) {
        this.activeActivityIds = activeActivityIds;
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

    public RecommendedAction getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(RecommendedAction recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public Classification getClassification() {
        return classification;
    }

    public void setClassification(Classification classification) {
        this.classification = classification;
    }
}
