package com.company.flowable.ops;

import java.util.concurrent.TimeUnit;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.RuntimeService;
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
    private final VerificationService verificationService;
    private final OpsCleanupProperties props;

    public DeleteWorker(RuntimeService runtimeService, VerificationService verificationService, OpsCleanupProperties props) {
        this.runtimeService = runtimeService;
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
                        return DeleteOutcome.ok();
                    }
                    if (attempt < maxAttempts) {
                        logger.info("pid={} verification failed; retrying delete after backoff", pid);
                        backoff(attempt);
                        continue;
                    }
                    return DeleteOutcome.fail("Verification failed");
                }
                return DeleteOutcome.ok();
            } catch (Exception ex) {
                if (isCallActivityNpe(ex)) {
                    String msg = "Flowable engine bug: call activity metadata missing (callActivityElement null). "
                        + "Redeploy parent/called process definitions or upgrade Flowable to 6.7.3+/6.8+.";
                    logger.warn("{} pid={}", msg, pid, ex);
                    return DeleteOutcome.fail(msg);
                }
                if (isRetryable(ex) && attempt < maxAttempts) {
                    logger.info("pid={} delete retryable failure on attempt {}: {}", pid, attempt + 1, ex.getMessage());
                    backoff(attempt);
                    continue;
                }
                logger.warn("Delete failed for {}", pid, ex);
                return DeleteOutcome.fail(ex.getMessage());
            }
        }
        return DeleteOutcome.fail("Delete attempts exhausted");
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
        return msg.contains("optimistic") || msg.contains("concurrent");
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
}
