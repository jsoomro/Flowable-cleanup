package com.company.flowable.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final OpsCleanupProperties props;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final String host;

    public AuditService(OpsCleanupProperties props, ObjectMapper mapper, JdbcTemplate jdbcTemplate) {
        this.props = props;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.host = resolveHost();
    }

    public void logEvent(String operation, Candidate candidate, String result, String user, String reason, String error) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", Instant.now().toString());
        record.put("operation", operation);
        record.put("result", result);
        record.put("user", user);
        record.put("host", host);
        record.put("reason", reason);
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
        record.put("jobCount", candidate.getJobCount());
        record.put("overdueJobCount", candidate.getOverdueJobCount());
        record.put("timerCount", candidate.getTimerCount());
        record.put("overdueTimerCount", candidate.getOverdueTimerCount());
        record.put("classification", candidate.getClassification() == null ? null : candidate.getClassification().name());
        record.put("recommendedAction", candidate.getRecommendedAction() == null ? null : candidate.getRecommendedAction().name());
        if (error != null) {
            record.put("error", error);
        }
        writeFile(record);
        writeDb(record);
    }

    private void writeFile(Map<String, Object> record) {
        Path path = Path.of(props.getAudit().getFile());
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(mapper.writeValueAsString(record));
                writer.newLine();
            }
        } catch (IOException ex) {
            logger.warn("Failed to write audit file", ex);
        }
    }

    private void writeDb(Map<String, Object> record) {
        if (!props.getAudit().isDbEnabled()) {
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO OPS_CLEANUP_AUDIT (EVENT_TIME, OPERATION, RESULT, USERNAME, HOSTNAME, PID, PROC_DEF_KEY, REASON, ERROR) VALUES (SYSTIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?)",
            record.get("operation"),
            record.get("result"),
            record.get("user"),
            record.get("host"),
            record.get("pid"),
            record.get("procDefKey"),
            record.get("reason"),
            record.get("error"));
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
