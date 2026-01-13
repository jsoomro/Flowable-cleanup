package com.company.flowable.ops;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanupExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CleanupExecutor.class);

    private final CleanupConfig config;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ManagementService managementService;
    private final AuditLogger auditLogger;

    public CleanupExecutor(CleanupConfig config,
                           RuntimeService runtimeService,
                           TaskService taskService,
                           ManagementService managementService,
                           AuditLogger auditLogger) {
        this.config = config;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.managementService = managementService;
        this.auditLogger = auditLogger;
    }

    public void execute(List<Candidate> candidates, Instant now) {
        List<Candidate> targets = filterCandidates(candidates);
        Map<String, Candidate> map = new HashMap<>();
        for (Candidate c : candidates) {
            map.put(c.getProcessInstanceId(), c);
        }
        targets.sort(Comparator.comparingInt((Candidate c) -> computeDepth(c, map)).reversed());

        if (config.isDryRun()) {
            logger.info("Dry run enabled; no deletions will be performed.");
            return;
        }

        if (targets.isEmpty()) {
            logger.info("No candidates eligible for deletion.");
            return;
        }

        if (config.getParallelism() <= 1) {
            for (Candidate candidate : targets) {
                deleteSingle(candidate);
                pause();
            }
        } else {
            ExecutorService executor = Executors.newFixedThreadPool(config.getParallelism());
            List<Future<Void>> futures = new ArrayList<>();
            for (Candidate candidate : targets) {
                futures.add(executor.submit(new DeleteTask(candidate)));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    logger.warn("Deletion interrupted", ex);
                } catch (ExecutionException ex) {
                    logger.warn("Deletion task failed", ex.getCause());
                }
            }
            executor.shutdown();
        }
    }

    private List<Candidate> filterCandidates(List<Candidate> candidates) {
        List<Candidate> targets = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (config.getDeleteMode() == DeleteMode.SAFE && candidate.getClassification() != Classification.SAFE_TO_DELETE) {
                continue;
            }
            targets.add(candidate);
        }
        return targets;
    }

    private int computeDepth(Candidate candidate, Map<String, Candidate> map) {
        if (!candidate.isSubprocess()) {
            return 0;
        }
        int depth = 0;
        String current = candidate.getParentPid();
        while (current != null) {
            depth++;
            Candidate parent = map.get(current);
            if (parent == null) {
                break;
            }
            current = parent.getParentPid();
        }
        return depth;
    }

    private void deleteSingle(Candidate candidate) {
        boolean active = runtimeService.createProcessInstanceQuery()
            .processInstanceId(candidate.getProcessInstanceId())
            .active()
            .count() > 0;
        if (!active) {
            auditLogger.logSkip(candidate, "Process instance not active");
            return;
        }

        int maxAttempts = Math.max(0, config.getRetryCount());
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                runtimeService.deleteProcessInstance(candidate.getProcessInstanceId(), config.getReason());
                auditLogger.logDeleteAttempt(candidate, attempt + 1, "OK", null);
                VerificationSnapshot snapshot = verify(candidate);
                if (snapshot.isDeleted()) {
                    auditLogger.logVerify(candidate, snapshot, "OK", null);
                    return;
                }
                auditLogger.logVerify(candidate, snapshot, "FAIL", "Verification failed");
                if (attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                return;
            } catch (Exception ex) {
                if (isRetryable(ex) && attempt < maxAttempts) {
                    auditLogger.logDeleteAttempt(candidate, attempt + 1, "FAIL", ex.getMessage());
                    backoff(attempt);
                    continue;
                }
                auditLogger.logDeleteAttempt(candidate, attempt + 1, "FAIL", ex.getMessage());
                return;
            }
        }
    }

    private VerificationSnapshot verify(Candidate candidate) {
        String pid = candidate.getProcessInstanceId();
        long procCount = runtimeService.createProcessInstanceQuery().processInstanceId(pid).count();
        long taskCount = taskService.createTaskQuery().processInstanceId(pid).count();
        long jobCount = managementService.createJobQuery().processInstanceId(pid).count();
        long timerCount = managementService.createTimerJobQuery().processInstanceId(pid).count();
        long execCount = runtimeService.createExecutionQuery().processInstanceId(pid).count();
        return new VerificationSnapshot(procCount, taskCount, jobCount, timerCount, execCount);
    }

    private boolean isRetryable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof FlowableOptimisticLockingException) {
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

    private void backoff(int attempt) {
        long delay = config.getRetryBackoffMillis();
        long sleep = delay * (attempt + 1);
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void pause() {
        if (config.getDeletePauseMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(config.getDeletePauseMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private class DeleteTask implements Callable<Void> {
        private final Candidate candidate;

        DeleteTask(Candidate candidate) {
            this.candidate = candidate;
        }

        @Override
        public Void call() {
            deleteSingle(candidate);
            return null;
        }
    }
}
