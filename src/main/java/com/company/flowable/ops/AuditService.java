package com.company.flowable.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
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
    private static final Logger auditLogger = LoggerFactory.getLogger("com.company.flowable.ops.audit");

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
        writeAuditLog(record);
        writeDb(record);
    }

    private void writeAuditLog(Map<String, Object> record) {
        try {
            auditLogger.info(mapper.writeValueAsString(record));
        } catch (Exception ex) {
            logger.warn("Failed to write audit log", ex);
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
