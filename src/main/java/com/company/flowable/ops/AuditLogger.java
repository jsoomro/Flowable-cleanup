package com.company.flowable.ops;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditLogger implements AutoCloseable {
    private final BufferedWriter writer;
    private final String runId;
    private final String host;
    private final String user;

    public AuditLogger(String filePath, String runId) throws IOException {
        this.runId = runId;
        this.host = resolveHost();
        this.user = System.getProperty("user.name");
        Path path = Path.of(filePath);
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void logEvaluation(Candidate candidate, ClassificationResult classification) {
        Map<String, Object> record = baseRecord(candidate, "EVALUATE", "OK");
        record.put("classification", classification.getClassification().name());
        record.put("recommendedAction", classification.getRecommendedAction().name());
        writeRecord(record);
    }

    public synchronized void logDeleteAttempt(Candidate candidate, int attempt, String result, String error) {
        Map<String, Object> record = baseRecord(candidate, "DELETE", result);
        record.put("attempt", attempt);
        if (error != null) {
            record.put("error", error);
        }
        writeRecord(record);
    }

    public synchronized void logVerify(Candidate candidate, VerificationSnapshot snapshot, String result, String error) {
        Map<String, Object> record = baseRecord(candidate, "VERIFY", result);
        record.put("verifyProcessInstanceCount", snapshot.getProcessInstanceCount());
        record.put("verifyTaskCount", snapshot.getTaskCount());
        record.put("verifyJobCount", snapshot.getJobCount());
        record.put("verifyTimerCount", snapshot.getTimerCount());
        record.put("verifyExecutionCount", snapshot.getExecutionCount());
        if (error != null) {
            record.put("error", error);
        }
        writeRecord(record);
    }

    public synchronized void logSkip(Candidate candidate, String reason) {
        Map<String, Object> record = baseRecord(candidate, "SKIP", "SKIPPED");
        record.put("error", reason);
        writeRecord(record);
    }

    private Map<String, Object> baseRecord(Candidate candidate, String operation, String result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", Instant.now().toString());
        record.put("runId", runId);
        record.put("host", host);
        record.put("user", user);
        record.put("operation", operation);
        record.put("result", result);
        record.put("pid", candidate.getProcessInstanceId());
        record.put("procDefId", candidate.getProcessDefinitionId());
        record.put("procDefKey", candidate.getProcessDefinitionKey());
        record.put("startTime", candidate.getStartTime() == null ? null : candidate.getStartTime().toString());
        record.put("hoursRunning", candidate.getHoursRunning());
        record.put("starterUserId", candidate.getStarterUserId());
        record.put("starterName", candidate.getStarterName());
        record.put("starterEmail", candidate.getStarterEmail());
        record.put("isSubprocess", candidate.isSubprocess());
        record.put("parentPid", candidate.getParentPid());
        record.put("openTasksCount", candidate.getOpenTasksCount());
        record.put("oldestTaskAgeHours", candidate.getOldestTaskAgeHours());
        record.put("taskSummaries", truncateList(taskSummaries(candidate), 20));
        record.put("activeActivityIds", truncateList(candidate.getActiveActivityIds(), 20));
        record.put("jobCount", candidate.getJobCount());
        record.put("overdueJobCount", candidate.getOverdueJobCount());
        record.put("timerCount", candidate.getTimerCount());
        record.put("overdueTimerCount", candidate.getOverdueTimerCount());
        record.put("classification", candidate.getClassification() == null ? null : candidate.getClassification().name());
        record.put("recommendedAction", candidate.getRecommendedAction() == null ? null : candidate.getRecommendedAction().name());
        return record;
    }

    private List<String> taskSummaries(Candidate candidate) {
        List<String> summaries = new ArrayList<>();
        for (TaskSummary summary : candidate.getTasks()) {
            summaries.add(summary.toShortString());
        }
        return summaries;
    }

    private List<String> truncateList(List<String> list, int max) {
        if (list == null) {
            return null;
        }
        if (list.size() <= max) {
            return list;
        }
        List<String> truncated = new ArrayList<>(list.subList(0, max));
        truncated.add("...truncated");
        return truncated;
    }

    private void writeRecord(Map<String, Object> record) {
        try {
            writer.write(JsonUtil.toJson(record));
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write audit record", ex);
        }
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}
