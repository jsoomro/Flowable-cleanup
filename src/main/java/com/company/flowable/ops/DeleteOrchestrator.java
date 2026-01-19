package com.company.flowable.ops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeleteOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(DeleteOrchestrator.class);
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private final OpsCleanupProperties props;
    private final DeleteWorker deleteWorker;
    private final OpsCleanupService cleanupService;
    private final AuditService auditService;

    public DeleteOrchestrator(OpsCleanupProperties props,
                              DeleteWorker deleteWorker,
                              OpsCleanupService cleanupService,
                              AuditService auditService) {
        this.props = props;
        this.deleteWorker = deleteWorker;
        this.cleanupService = cleanupService;
        this.auditService = auditService;
    }

    public List<DeleteResultDto> terminateSelected(List<String> pids, String reason, boolean verify, String user) {
        if (pids == null || pids.isEmpty()) {
            throw new OpsException(400, "No process instance IDs provided");
        }
        if (pids.size() > props.getMaxBulkDelete()) {
            throw new OpsException(400, "Too many process instances requested");
        }
        logger.info("Terminate selected requested: {} pids, verify={}, user={}, dryRun={}", pids.size(), verify, user, props.isDryRun());
        return deleteByIds(pids, reason, verify, user);
    }

    public List<DeleteResultDto> terminateAll(FilterCriteria criteria, String reason, String user) {
        criteria.setPage(0);
        criteria.setSize(props.getMaxBulkDelete());
        PageResult<ProcessSummaryDto> page = cleanupService.findCandidates(criteria);
        List<String> ids = new ArrayList<>();
        for (ProcessSummaryDto dto : page.getItems()) {
            ids.add(dto.getProcessInstanceId());
        }
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        logger.info("Terminate ALL requested: {} candidates (capped at {}), user={}, dryRun={}", ids.size(), props.getMaxBulkDelete(), user, props.isDryRun());
        return deleteByIds(ids, reason, true, user);
    }

    private List<DeleteResultDto> deleteByIds(List<String> pids, String reason, boolean verify, String user) {
        if (props.isJobExecutorQuietMode()) {
            logger.info("Job executor quiet mode requested; async executor is already disabled in FlowableEngineConfig.");
        }
        if (props.isDryRun()) {
            List<DeleteResultDto> results = new ArrayList<>();
            for (String pid : pids) {
                Candidate candidate = cleanupService.loadCandidateForDelete(pid);
                if (candidate != null) {
                    logger.info("[DRY RUN] Would delete pid={}, procKey={}, reason={}", pid, candidate.getProcessDefinitionKey(), reason);
                    auditService.logEvent("DELETE", candidate, "DRY_RUN", user, reason, null);
                    results.add(new DeleteResultDto(pid, "DRY_RUN", null));
                } else {
                    Candidate placeholder = new Candidate();
                    placeholder.setProcessInstanceId(pid);
                    logger.info("[DRY RUN] Skipping pid={} because it is not eligible or not found", pid);
                    auditService.logEvent("DELETE", placeholder, "SKIPPED", user, reason, "Not eligible or not found");
                    results.add(new DeleteResultDto(pid, "SKIPPED", "Not eligible or not found"));
                }
            }
            return results;
        }

        List<Candidate> candidates = new ArrayList<>();
        List<DeleteResultDto> results = new ArrayList<>();
        for (String pid : pids) {
            Candidate candidate = cleanupService.loadCandidateForDelete(pid);
            if (candidate == null) {
                Candidate placeholder = new Candidate();
                placeholder.setProcessInstanceId(pid);
                logger.info("Skipping pid={} because it is not eligible or not found", pid);
                auditService.logEvent("DELETE", placeholder, "SKIPPED", user, reason, "Not eligible or not found");
                results.add(new DeleteResultDto(pid, "SKIPPED", "Not eligible or not found"));
                continue;
            }
            candidates.add(candidate);
        }
        Map<String, Candidate> map = new HashMap<>();
        for (Candidate c : candidates) {
            map.put(c.getProcessInstanceId(), c);
        }
        candidates.sort(Comparator.comparingInt((Candidate c) -> computeDepth(c, map)).reversed());

        for (Candidate candidate : candidates) {
            String pid = candidate.getProcessInstanceId();
            if (!inFlight.add(pid)) {
                logger.info("Skipping pid={} because a delete is already in-flight this run", pid);
                results.add(new DeleteResultDto(pid, "SKIPPED", "Already in progress"));
                continue;
            }
            try {
                int depth = computeDepth(candidate, map);
                logger.info("Deleting pid={}, procKey={}, depth={}, verify={}", pid,
                    candidate.getProcessDefinitionKey(), depth, verify);
                DeleteOutcome outcome = deleteWorker.deleteProcess(pid, reason, verify);
                results.add(new DeleteResultDto(pid, outcome.getResult(), outcome.getError()));
                auditService.logEvent("DELETE", candidate, outcome.getResult(), user, reason, outcome.getError());
                pause();
            } finally {
                inFlight.remove(pid);
            }
        }
        return results;
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

    private void pause() {
        if (props.getDelayBetweenDeletesMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(props.getDelayBetweenDeletesMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
