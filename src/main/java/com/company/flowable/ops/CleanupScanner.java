package com.company.flowable.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.idm.api.User;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.NativeExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CleanupScanner {
    private static final Logger logger = LoggerFactory.getLogger(CleanupScanner.class);

    private final ScannerConfig config;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final ManagementService managementService;
    private final IdentityService identityService;
    private final JobCountStrategy jobCountStrategy;

    public CleanupScanner(ScannerConfig config,
                          RuntimeService runtimeService,
                          HistoryService historyService,
                          TaskService taskService,
                          ManagementService managementService,
                          IdentityService identityService,
                          JobCountStrategy jobCountStrategy) {
        this.config = config;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskService = taskService;
        this.managementService = managementService;
        this.identityService = identityService;
        this.jobCountStrategy = jobCountStrategy;
    }

    public List<Candidate> scan(Instant cutoff, Instant now) {
        return scanWithFilters(cutoff, now, null);
    }

    public List<Candidate> scanWithFilters(Instant cutoff, Instant now, FilterCriteria criteria) {
        List<Candidate> results = new ArrayList<>();
        int pageSize = 200;
        int startIndex = 0;
        boolean historyAvailable = true;

        while (results.size() < config.getMaxPerRun()) {
            List<HistoricProcessInstance> page;
            try {
                org.flowable.engine.history.HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                    .unfinished()
                    .startedBefore(Date.from(cutoff));
                if (criteria != null) {
                    if (criteria.getProcDefKey() != null && !criteria.getProcDefKey().isEmpty()) {
                        query.processDefinitionKey(criteria.getProcDefKey());
                    }
                    if (criteria.getStarterUserId() != null && !criteria.getStarterUserId().isEmpty()) {
                        query.startedBy(criteria.getStarterUserId());
                    }
                }
                page = query.listPage(startIndex, pageSize);
            } catch (Exception ex) {
                historyAvailable = false;
                logger.warn("HistoricProcessInstance query failed; fallback to runtime-only scan", ex);
                break;
            }
            if (page == null || page.isEmpty()) {
                break;
            }
            PrefetchData prefetch = prefetchData(page, now);
            for (HistoricProcessInstance historic : page) {
                if (results.size() >= config.getMaxPerRun()) {
                    break;
                }
                if (!matchesBaseFilters(historic, criteria)) {
                    continue;
                }
                Candidate candidate = buildCandidateFromHistoric(historic, cutoff, now, prefetch);
                if (candidate != null) {
                    results.add(candidate);
                }
            }
            startIndex += pageSize;
        }

        if (!historyAvailable && results.isEmpty()) {
            results.addAll(scanRuntimeFallback(cutoff, now, criteria));
        }

        return results;
    }

    public List<Candidate> scanSelected(List<String> ids, Instant cutoff, Instant now, boolean force) {
        List<Candidate> results = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            String pid = id.trim();
            if (seen.contains(pid)) {
                continue;
            }
            seen.add(pid);
            HistoricProcessInstance historic = null;
            try {
                historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(pid)
                    .singleResult();
            } catch (Exception ex) {
                logger.warn("HistoricProcessInstance lookup failed for {}", pid, ex);
            }
            if (historic == null) {
                logger.warn("HistoricProcessInstance not found for {}", pid);
                continue;
            }
            Instant startTime = historic.getStartTime() == null ? null : historic.getStartTime().toInstant();
            if (!force && startTime != null && startTime.isAfter(cutoff)) {
                logger.info("Skipping {} because it is newer than cutoff", pid);
                continue;
            }
            PrefetchData prefetch = prefetchData(java.util.Collections.singletonList(historic), now);
            Candidate candidate = buildCandidateFromHistoric(historic, cutoff, now, prefetch);
            if (candidate != null) {
                results.add(candidate);
            }
        }
        return results;
    }

    private List<Candidate> scanRuntimeFallback(Instant cutoff, Instant now, FilterCriteria criteria) {
        List<Candidate> results = new ArrayList<>();
        int pageSize = 200;
        int startIndex = 0;
        while (results.size() < config.getMaxPerRun()) {
            List<ProcessInstance> page = runtimeService.createProcessInstanceQuery()
                .active()
                .listPage(startIndex, pageSize);
            if (page == null || page.isEmpty()) {
                break;
            }
            List<String> ids = new ArrayList<>();
            for (ProcessInstance instance : page) {
                ids.add(instance.getId());
            }
            List<HistoricProcessInstance> historics = historyService.createHistoricProcessInstanceQuery()
                .processInstanceIds(new java.util.HashSet<>(ids))
                .list();
            PrefetchData prefetch = prefetchData(historics, now);
            for (HistoricProcessInstance historic : historics) {
                if (results.size() >= config.getMaxPerRun()) {
                    break;
                }
                if (historic == null || historic.getStartTime() == null) {
                    continue;
                }
                if (historic.getStartTime().toInstant().isAfter(cutoff)) {
                    continue;
                }
                if (!matchesBaseFilters(historic, criteria)) {
                    continue;
                }
                Candidate candidate = buildCandidateFromHistoric(historic, cutoff, now, prefetch);
                if (candidate != null) {
                    results.add(candidate);
                }
            }
            startIndex += pageSize;
        }
        return results;
    }

    private Candidate buildCandidateFromHistoric(HistoricProcessInstance historic, Instant cutoff, Instant now, PrefetchData prefetch) {
        if (historic == null) {
            return null;
        }
        if (historic.getStartTime() == null) {
            return null;
        }
        Instant startTime = historic.getStartTime().toInstant();
        if (startTime.isAfter(cutoff)) {
            return null;
        }

        ProcessInstance runtime = prefetch.runtimeByProcessId.get(historic.getId());
        if (runtime == null) {
            return null;
        }

        String procDefKey = historic.getProcessDefinitionKey();
        if (!isKeyAllowed(procDefKey)) {
            return null;
        }

        Candidate candidate = new Candidate();
        candidate.setProcessInstanceId(historic.getId());
        candidate.setProcessDefinitionId(historic.getProcessDefinitionId());
        candidate.setProcessDefinitionKey(procDefKey);
        candidate.setStartTime(startTime);
        candidate.setHoursRunning(Duration.between(startTime, now).toHours());

        String starterUserId = historic.getStartUserId();
        if (starterUserId == null || starterUserId.trim().isEmpty()) {
            starterUserId = findStarterUserId(historic.getId());
        }
        candidate.setStarterUserId(starterUserId);
        if (starterUserId != null && !starterUserId.isEmpty()) {
            User user = prefetch.usersById.get(starterUserId);
            if (user != null) {
                String name = (user.getFirstName() == null ? "" : user.getFirstName()) +
                    " " + (user.getLastName() == null ? "" : user.getLastName());
                candidate.setStarterName(name.trim().isEmpty() ? null : name.trim());
                candidate.setStarterEmail(user.getEmail());
            }
        }

        List<Task> tasks = prefetch.tasksByProcessId.getOrDefault(historic.getId(), java.util.Collections.emptyList());
        for (Task task : tasks) {
            Instant createTime = task.getCreateTime() == null ? null : task.getCreateTime().toInstant();
            long ageHours = createTime == null ? 0 : Duration.between(createTime, now).toHours();
            candidate.getTasks().add(new TaskSummary(task.getId(), task.getName(), task.getAssignee(), createTime, ageHours));
        }

        candidate.setActiveActivityIds(prefetch.activeActivityIdsByProcessId.getOrDefault(historic.getId(), Collections.emptyList()));

        candidate.setJobCount(prefetch.jobCountByProcessId.getOrDefault(historic.getId(), 0));
        candidate.setOverdueJobCount(prefetch.overdueJobCountByProcessId.getOrDefault(historic.getId(), 0));
        candidate.setTimerCount(prefetch.timerCountByProcessId.getOrDefault(historic.getId(), 0));
        candidate.setOverdueTimerCount(prefetch.overdueTimerCountByProcessId.getOrDefault(historic.getId(), 0));

        String parentPid = prefetch.parentPidByProcessId.get(historic.getId());
        if (parentPid != null) {
            candidate.setSubprocess(true);
            candidate.setParentPid(parentPid);
        }

        if (!config.isIncludeSubprocesses() && candidate.isSubprocess()) {
            return null;
        }

        return candidate;
    }

    private boolean matchesBaseFilters(HistoricProcessInstance historic, FilterCriteria criteria) {
        if (criteria == null) {
            return true;
        }
        if (criteria.getProcDefKey() != null && !criteria.getProcDefKey().isEmpty()) {
            if (!criteria.getProcDefKey().equals(historic.getProcessDefinitionKey())) {
                return false;
            }
        }
        if (criteria.getStarterUserId() != null && !criteria.getStarterUserId().isEmpty()) {
            if (!criteria.getStarterUserId().equals(historic.getStartUserId())) {
                return false;
            }
        }
        return true;
    }

    private PrefetchData prefetchData(List<HistoricProcessInstance> page, Instant now) {
        List<String> ids = new ArrayList<>();
        java.util.Set<String> starterUserIds = new java.util.HashSet<>();
        for (HistoricProcessInstance historic : page) {
            if (historic != null) {
                ids.add(historic.getId());
                if (historic.getStartUserId() != null && !historic.getStartUserId().trim().isEmpty()) {
                    starterUserIds.add(historic.getStartUserId().trim());
                }
            }
        }
        PrefetchData data = new PrefetchData();
        if (ids.isEmpty()) {
            return data;
        }

        // Prefetch active runtime instances to avoid per-candidate lookups.
        List<ProcessInstance> runtimeInstances = runtimeService.createProcessInstanceQuery()
            .processInstanceIds(new java.util.HashSet<>(ids))
            .active()
            .list();
        for (ProcessInstance pi : runtimeInstances) {
            data.runtimeByProcessId.put(pi.getId(), pi);
        }

        // Prefetch executions in batches via native query (Flowable 6.7 lacks processInstanceIdIn on ExecutionQuery)
        java.util.Map<String, List<Execution>> executionsByPid = new java.util.HashMap<>();
        List<Execution> executions = fetchExecutionsByProcessIds(ids);
        for (Execution execution : executions) {
            executionsByPid.computeIfAbsent(execution.getProcessInstanceId(), k -> new ArrayList<>()).add(execution);
        }

        // Active activity IDs and subprocess mapping
        java.util.Map<String, String> subProcessExecutionIds = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, List<Execution>> entry : executionsByPid.entrySet()) {
            String pid = entry.getKey();
            for (Execution execution : entry.getValue()) {
                if (execution.getActivityId() != null) {
                    data.activeActivityIdsByProcessId
                        .computeIfAbsent(pid, k -> new ArrayList<>())
                        .add(execution.getActivityId());
                }
                if (execution.getSuperExecutionId() != null) {
                    subProcessExecutionIds.put(execution.getSuperExecutionId(), pid);
                }
            }
        }
        if (!subProcessExecutionIds.isEmpty()) {
            List<Execution> parentExecutions = fetchExecutionsByIds(new ArrayList<>(subProcessExecutionIds.keySet()));
            for (Execution parent : parentExecutions) {
                String subProcessId = subProcessExecutionIds.get(parent.getId());
                if (subProcessId != null) {
                    data.parentPidByProcessId.put(subProcessId, parent.getProcessInstanceId());
                }
            }
        }

        List<Task> tasks = taskService.createTaskQuery().processInstanceIdIn(new java.util.HashSet<>(ids)).active().list();
        for (Task task : tasks) {
            data.tasksByProcessId.computeIfAbsent(task.getProcessInstanceId(), k -> new ArrayList<>()).add(task);
        }

        jobCountStrategy.countJobsAndTimers(ids, now, data);

        if (!starterUserIds.isEmpty()) {
            List<User> users = identityService.createUserQuery()
                .userIds(new ArrayList<>(starterUserIds))
                .list();
            for (User user : users) {
                data.usersById.put(user.getId(), user);
            }
        }
        return data;
    }


    private String findStarterUserId(String processInstanceId) {
        try {
            List<IdentityLink> links = runtimeService.getIdentityLinksForProcessInstance(processInstanceId);
            for (IdentityLink link : links) {
                if ("starter".equalsIgnoreCase(link.getType())) {
                    return link.getUserId();
                }
            }
        } catch (Exception ex) {
            logger.debug("Starter user lookup failed for {}", processInstanceId, ex);
        }
        return null;
    }

    private boolean isKeyAllowed(String key) {
        if (key == null) {
            return false;
        }
        if (config.getProcDefKeyDenyList().contains(key)) {
            return false;
        }
        if (!config.getProcDefKeyAllowList().isEmpty() && !config.getProcDefKeyAllowList().contains(key)) {
            return false;
        }
        return true;
    }

    private List<Execution> fetchExecutionsByProcessIds(List<String> processIds) {
        List<Execution> result = new ArrayList<>();
        if (processIds == null || processIds.isEmpty()) {
            return result;
        }
        String tableName = managementService.getTableName(Execution.class);
        int chunkSize = 200;
        for (int i = 0; i < processIds.size(); i += chunkSize) {
            List<String> chunk = processIds.subList(i, Math.min(processIds.size(), i + chunkSize));
            StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(tableName)
                .append(" WHERE PROC_INST_ID_ IN (");
            List<String> paramNames = new ArrayList<>();
            for (int j = 0; j < chunk.size(); j++) {
                String param = "pid" + j;
                paramNames.add(param);
                sql.append("#{").append(param).append("}");
                if (j < chunk.size() - 1) {
                    sql.append(",");
                }
            }
            sql.append(")");
            NativeExecutionQuery query = runtimeService.createNativeExecutionQuery().sql(sql.toString());
            for (int j = 0; j < chunk.size(); j++) {
                query.parameter(paramNames.get(j), chunk.get(j));
            }
            result.addAll(query.list());
        }
        return result;
    }

    private List<Execution> fetchExecutionsByIds(List<String> executionIds) {
        List<Execution> result = new ArrayList<>();
        if (executionIds == null || executionIds.isEmpty()) {
            return result;
        }
        String tableName = managementService.getTableName(Execution.class);
        int chunkSize = 200;
        for (int i = 0; i < executionIds.size(); i += chunkSize) {
            List<String> chunk = executionIds.subList(i, Math.min(executionIds.size(), i + chunkSize));
            StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(tableName)
                .append(" WHERE ID_ IN (");
            List<String> paramNames = new ArrayList<>();
            for (int j = 0; j < chunk.size(); j++) {
                String param = "id" + j;
                paramNames.add(param);
                sql.append("#{").append(param).append("}");
                if (j < chunk.size() - 1) {
                    sql.append(",");
                }
            }
            sql.append(")");
            NativeExecutionQuery query = runtimeService.createNativeExecutionQuery().sql(sql.toString());
            for (int j = 0; j < chunk.size(); j++) {
                query.parameter(paramNames.get(j), chunk.get(j));
            }
            result.addAll(query.list());
        }
        return result;
    }

    static class PrefetchData {
        final java.util.Map<String, ProcessInstance> runtimeByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, List<Task>> tasksByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, Integer> jobCountByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, Integer> overdueJobCountByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, Integer> timerCountByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, Integer> overdueTimerCountByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, User> usersById = new java.util.HashMap<>();
        final java.util.Map<String, List<String>> activeActivityIdsByProcessId = new java.util.HashMap<>();
        final java.util.Map<String, String> parentPidByProcessId = new java.util.HashMap<>();
    }
}
