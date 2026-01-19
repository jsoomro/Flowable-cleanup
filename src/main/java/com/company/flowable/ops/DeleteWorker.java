package com.company.flowable.ops;

import java.util.concurrent.TimeUnit;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteWorker {
    private static final Logger logger = LoggerFactory.getLogger(DeleteWorker.class);

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ManagementService managementService;
    private final VerificationService verificationService;
    private final OpsCleanupProperties props;

    public DeleteWorker(RuntimeService runtimeService,
                        TaskService taskService,
                        HistoryService historyService,
                        ManagementService managementService,
                        VerificationService verificationService,
                        OpsCleanupProperties props) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.managementService = managementService;
        this.verificationService = verificationService;
        this.props = props;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeleteOutcome deleteProcess(String pid, String reason, boolean verify) {
        boolean active = runtimeService.createProcessInstanceQuery().processInstanceId(pid).active().count() > 0;
        if (!active) {
            logger.info("pid={} not active; skipping delete", pid);
            return DeleteOutcome.skipped("Process instance not active");
        }

        int maxAttempts = Math.max(0, props.getRetryCount());
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                logger.info("Attempt {} deleting pid={} (verify={}, reason='{}')", attempt + 1, pid, verify, reason);
                long taskCount = getTaskCount(pid);
                JobCounts jobCounts = getJobCounts(pid);
                logger.info("Preflight pid={} tasks={} jobs async={} timer={} deadLetter={}", pid, taskCount,
                        jobCounts.asyncJobs, jobCounts.timerJobs, jobCounts.deadLetterJobs);
                if (props.isSuspendBeforeDelete()) {
                    try {
                        runtimeService.suspendProcessInstanceById(pid);
                        logger.info("pid={} suspended prior to delete", pid);
                    } catch (FlowableObjectNotFoundException ex) {
                        logger.info("pid={} already deleted before suspend attempt", pid);
                        return DeleteOutcome.ok(attempt + 1, System.currentTimeMillis());
                    } catch (Exception ex) {
                        logger.warn("pid={} suspend failed (continuing to delete): {}", pid, ex.getMessage());
                    }
                }
                runtimeService.deleteProcessInstance(pid, reason);
                if (verify) {
                    VerificationSnapshot snapshot = verificationService.verify(pid);
                    logger.info("Verification for pid={}: deleted={}, procInstances={}, tasks={}, jobs={}, timers={}, executions={}",
                        pid,
                        snapshot.isDeleted(),
                        snapshot.getProcessInstanceCount(),
                        snapshot.getTaskCount(),
                        snapshot.getJobCount(),
                        snapshot.getTimerCount(),
                        snapshot.getExecutionCount());
                    if (snapshot.isDeleted()) {
                        return DeleteOutcome.ok(attempt + 1, System.currentTimeMillis());
                    }
                    if (attempt < maxAttempts) {
                        logger.info("pid={} verification failed; retrying delete after backoff", pid);
                        backoff(attempt);
                        continue;
                    }
                    return DeleteOutcome.fail("Verification failed", attempt + 1, System.currentTimeMillis(), "VERIFY");
                }
                if (props.isDeleteHistoric()) {
                    try {
                        historyService.deleteHistoricProcessInstance(pid);
                    } catch (Exception ex) {
                        logger.warn("pid={} historic delete failed (continuing): {}", pid, ex.getMessage());
                    }
                }
                return DeleteOutcome.ok(attempt + 1, System.currentTimeMillis());
            } catch (Exception ex) {
                if (ex instanceof FlowableObjectNotFoundException) {
                    logger.info("pid={} already deleted during attempt {}", pid, attempt + 1);
                    return DeleteOutcome.ok(attempt + 1, System.currentTimeMillis());
                }
                if (props.isNpeFallbackEnabled() && isCallActivityNpe(ex)) {
                    logger.warn("pid={} delete hit call-activity NPE; attempting suspend+retry once", pid, ex);
                    try {
                        runtimeService.suspendProcessInstanceById(pid);
                    } catch (Exception suspendEx) {
                        logger.warn("pid={} suspend during NPE fallback failed: {}", pid, suspendEx.getMessage());
                    }
                    try {
                        runtimeService.deleteProcessInstance(pid, reason);
                        return DeleteOutcome.ok(attempt + 1, System.currentTimeMillis());
                    } catch (Exception retryEx) {
                        logger.warn("pid={} NPE fallback retry failed", pid, retryEx);
                        return DeleteOutcome.quarantined("Call-activity delete NPE persisted after fallback: " + retryEx.getMessage(),
                                attempt + 1, System.currentTimeMillis());
                    }
                }
                if (isRetryable(ex) && attempt < maxAttempts) {
                    logger.info("pid={} delete retryable failure on attempt {}: {}", pid, attempt + 1, ex.getMessage());
                    backoff(attempt);
                    continue;
                }
                String classification = classify(ex);
                logger.warn("Delete failed for {}", pid, ex);
                return DeleteOutcome.fail(ex.getMessage(), attempt + 1, System.currentTimeMillis(), classification);
            }
        }
        return DeleteOutcome.fail("Delete attempts exhausted", maxAttempts + 1, System.currentTimeMillis(), "RETRY_EXHAUSTED");
    }

    private void backoff(int attempt) {
        long delay = props.getRetryBackoffMillis() * (attempt + 1);
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isRetryable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof FlowableOptimisticLockingException || current instanceof OptimisticLockingFailureException) {
                return true;
            }
            if (current instanceof FlowableObjectNotFoundException) {
                return false;
            }
            current = current.getCause();
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return msg.contains("optimistic") || msg.contains("concurrent") || msg.contains("deadlock") || msg.contains("ora-00060") || msg.contains("timeout");
    }

    private boolean isCallActivityNpe(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof NullPointerException) {
                String msg = current.getMessage() == null ? "" : current.getMessage();
                if (msg.contains("callActivityElement")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String classify(Throwable ex) {
        if (ex instanceof FlowableOptimisticLockingException || ex instanceof OptimisticLockingFailureException) {
            return "OPT_LOCK";
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("deadlock") || msg.contains("ora-00060")) {
            return "DEADLOCK";
        }
        if (isCallActivityNpe(ex)) {
            return "NPE";
        }
        return "OTHER";
    }

    private JobCounts getJobCounts(String pid) {
        try {
            long asyncJobs = managementService.createJobQuery().processInstanceId(pid).count();
            long timerJobs = managementService.createTimerJobQuery().processInstanceId(pid).count();
            long deadLetterJobs = managementService.createDeadLetterJobQuery().processInstanceId(pid).count();
            return new JobCounts(asyncJobs, timerJobs, deadLetterJobs);
        } catch (Exception ex) {
            logger.debug("pid={} job summary query failed: {}", pid, ex.getMessage());
            return new JobCounts(0, 0, 0);
        }
    }

    private long getTaskCount(String pid) {
        try {
            return taskService.createTaskQuery().processInstanceId(pid).count();
        } catch (Exception ex) {
            logger.debug("pid={} task query failed: {}", pid, ex.getMessage());
            return 0;
        }
    }

    private static class JobCounts {
        final long asyncJobs;
        final long timerJobs;
        final long deadLetterJobs;

        JobCounts(long asyncJobs, long timerJobs, long deadLetterJobs) {
            this.asyncJobs = asyncJobs;
            this.timerJobs = timerJobs;
            this.deadLetterJobs = deadLetterJobs;
        }
    }
}
