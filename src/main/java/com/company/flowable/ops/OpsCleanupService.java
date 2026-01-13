package com.company.flowable.ops;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;

@Service
public class OpsCleanupService {
    private final OpsCleanupProperties props;
    private final CleanupScanner cleanupScanner;
    private final ClassificationService classificationService;
    private final BpmnModelCache bpmnModelCache;

    public OpsCleanupService(OpsCleanupProperties props,
                             CleanupScanner cleanupScanner,
                             RepositoryService repositoryService,
                             ClassificationService classificationService) {
        this.props = props;
        this.cleanupScanner = cleanupScanner;
        this.classificationService = classificationService;
        this.bpmnModelCache = new BpmnModelCache(repositoryService, 128);
    }

    public PageResult<ProcessSummaryDto> findCandidates(FilterCriteria criteria) {
        if (!props.isEnabled()) {
            throw new OpsException(503, "Cleanup service disabled");
        }
        int hours = criteria.getHours() > 0 ? criteria.getHours() : props.getDefaultHours();
        int size = Math.min(criteria.getSize() <= 0 ? 50 : criteria.getSize(), props.getMaxPageSize());
        int page = Math.max(criteria.getPage(), 0);

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(hours));

        List<Candidate> candidates = cleanupScanner.scanWithFilters(cutoff, now, criteria);
        ScanAccumulator accumulator = new ScanAccumulator(page, size);
        accumulateCandidates(criteria, candidates, accumulator);

        int totalPages = accumulator.totalItems == 0 ? 0 : (int) Math.ceil(accumulator.totalItems / (double) size);
        PageInfo pageInfo = new PageInfo(page, size, accumulator.totalItems, totalPages);
        SummaryCounts summary = new SummaryCounts(accumulator.waitCount, accumulator.escalateCount, accumulator.terminateCount);
        return new PageResult<>(accumulator.items, pageInfo, summary);
    }

    public SummaryCounts getSummaryCounts(FilterCriteria criteria) {
        int hours = criteria.getHours() > 0 ? criteria.getHours() : props.getDefaultHours();
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(hours));
        List<Candidate> candidates = cleanupScanner.scanWithFilters(cutoff, now, criteria);
        ScanAccumulator accumulator = new ScanAccumulator(0, 0);
        accumulateCandidates(criteria, candidates, accumulator);
        return new SummaryCounts(accumulator.waitCount, accumulator.escalateCount, accumulator.terminateCount);
    }

    public ProcessDetailDto getDetails(String pid, int hours) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(hours <= 0 ? props.getDefaultHours() : hours));
        List<Candidate> candidates = cleanupScanner.scanSelected(Collections.singletonList(pid), cutoff, now, false);
        Candidate candidate = candidates.isEmpty() ? null : candidates.get(0);
        if (candidate == null) {
            throw new OpsException(404, "Process instance not active or outside filters");
        }
        ClassificationResult classification = classificationService.classify(candidate, props);
        candidate.setClassification(classification.getClassification());
        candidate.setRecommendedAction(classification.getRecommendedAction());
        return toDetailDto(candidate);
    }

    public Candidate loadCandidateForDelete(String pid) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(props.getDefaultHours()));
        List<Candidate> candidates = cleanupScanner.scanSelected(Collections.singletonList(pid), cutoff, now, false);
        Candidate candidate = candidates.isEmpty() ? null : candidates.get(0);
        if (candidate == null) {
            return null;
        }
        ClassificationResult classification = classificationService.classify(candidate, props);
        candidate.setClassification(classification.getClassification());
        candidate.setRecommendedAction(classification.getRecommendedAction());
        return candidate;
    }

    public void exportCsv(FilterCriteria criteria, OutputStream outputStream) throws IOException {
        int hours = criteria.getHours() > 0 ? criteria.getHours() : props.getDefaultHours();
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(hours));
        String header = "pid,procDefKey,startTime,hoursRunning,classification,recommendedAction,openTasksCount,oldestTaskAge,timerCount,overdueJobCount,overdueTimerCount,starterUserId,starterEmail,isSubprocess,parentPid\n";
        outputStream.write(header.getBytes(StandardCharsets.UTF_8));
        List<Candidate> candidates = cleanupScanner.scanWithFilters(cutoff, now, criteria);
        accumulateCandidates(criteria, candidates, new ScanConsumer() {
            @Override
            public void accept(Candidate candidate) {
                ProcessSummaryDto dto = toSummaryDto(candidate);
                String line = csv(dto.getProcessInstanceId()) + "," +
                    csv(dto.getProcessDefinitionKey()) + "," +
                    csv(dto.getStartTime() == null ? "" : dto.getStartTime().toString()) + "," +
                    dto.getHoursRunning() + "," +
                    csv(dto.getClassification() == null ? "" : dto.getClassification().name()) + "," +
                    csv(dto.getRecommendedAction() == null ? "" : dto.getRecommendedAction().name()) + "," +
                    dto.getOpenTasksCount() + "," +
                    (dto.getOldestTaskAgeHours() == null ? "" : dto.getOldestTaskAgeHours()) + "," +
                    dto.getTimerCount() + "," +
                    dto.getOverdueJobCount() + "," +
                    dto.getOverdueTimerCount() + "," +
                    csv(dto.getStarterUserId()) + "," +
                    csv(dto.getStarterEmail()) + "," +
                    dto.isSubprocess() + "," +
                    csv(dto.getParentProcessInstanceId()) + "\n";
                try {
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ex) {
                    throw new OpsException(500, "Failed to stream CSV", ex);
                }
            }
        });
    }

    private void accumulateCandidates(FilterCriteria criteria, List<Candidate> candidates, ScanAccumulator accumulator) {
        accumulateCandidates(criteria, candidates, new ScanConsumer() {
            @Override
            public void accept(Candidate candidate) {
                if (candidate.getRecommendedAction() == RecommendedAction.WAIT) {
                    accumulator.waitCount++;
                } else if (candidate.getRecommendedAction() == RecommendedAction.ESCALATE) {
                    accumulator.escalateCount++;
                } else if (candidate.getRecommendedAction() == RecommendedAction.TERMINATE) {
                    accumulator.terminateCount++;
                }

                accumulator.totalItems++;

                int start = accumulator.page * accumulator.size;
                int end = start + accumulator.size;
                if (accumulator.totalItems > start && accumulator.totalItems <= end) {
                    accumulator.items.add(toSummaryDto(candidate));
                }
            }
        });
    }

    private void accumulateCandidates(FilterCriteria criteria, List<Candidate> candidates, ScanConsumer consumer) {
        for (Candidate candidate : candidates) {
            ClassificationResult classification = classificationService.classify(candidate, props);
            candidate.setClassification(classification.getClassification());
            candidate.setRecommendedAction(classification.getRecommendedAction());

            if (!matchesAction(criteria.getAction(), candidate.getRecommendedAction())) {
                continue;
            }
            if (criteria.getHasTasks() != null) {
                boolean hasTasks = candidate.getOpenTasksCount() > 0;
                if (criteria.getHasTasks() != hasTasks) {
                    continue;
                }
            }
            consumer.accept(candidate);
        }
    }

    private boolean matchesAction(String action, RecommendedAction candidateAction) {
        if (action == null || action.isEmpty() || "ALL".equalsIgnoreCase(action)) {
            return true;
        }
        return candidateAction != null && candidateAction.name().equalsIgnoreCase(action);
    }

    private ProcessSummaryDto toSummaryDto(Candidate candidate) {
        ProcessSummaryDto dto = new ProcessSummaryDto();
        dto.setProcessInstanceId(candidate.getProcessInstanceId());
        dto.setProcessDefinitionId(candidate.getProcessDefinitionId());
        dto.setProcessDefinitionKey(candidate.getProcessDefinitionKey());
        dto.setStartTime(candidate.getStartTime());
        dto.setHoursRunning(candidate.getHoursRunning());
        dto.setStarterUserId(candidate.getStarterUserId());
        dto.setStarterName(candidate.getStarterName());
        dto.setStarterEmail(candidate.getStarterEmail());
        dto.setSubprocess(candidate.isSubprocess());
        dto.setParentProcessInstanceId(candidate.getParentPid());
        dto.setOpenTasksCount(candidate.getOpenTasksCount());
        dto.setOldestTaskAgeHours(candidate.getOldestTaskAgeHours());
        dto.setTaskSummaries(truncateTaskSummaries(candidate.getTasks(), 5));
        dto.setActiveActivityIds(truncate(candidate.getActiveActivityIds(), 10));
        dto.setJobCount(candidate.getJobCount());
        dto.setOverdueJobCount(candidate.getOverdueJobCount());
        dto.setTimerCount(candidate.getTimerCount());
        dto.setOverdueTimerCount(candidate.getOverdueTimerCount());
        dto.setRecommendedAction(candidate.getRecommendedAction());
        dto.setClassification(candidate.getClassification());
        return dto;
    }

    private ProcessDetailDto toDetailDto(Candidate candidate) {
        ProcessDetailDto dto = new ProcessDetailDto();
        dto.setProcessInstanceId(candidate.getProcessInstanceId());
        dto.setProcessDefinitionId(candidate.getProcessDefinitionId());
        dto.setProcessDefinitionKey(candidate.getProcessDefinitionKey());
        dto.setStartTime(candidate.getStartTime());
        dto.setHoursRunning(candidate.getHoursRunning());
        dto.setStarterUserId(candidate.getStarterUserId());
        dto.setStarterName(candidate.getStarterName());
        dto.setStarterEmail(candidate.getStarterEmail());
        dto.setSubprocess(candidate.isSubprocess());
        dto.setParentProcessInstanceId(candidate.getParentPid());
        dto.setTasks(toTaskDtos(candidate.getTasks()));
        dto.setActiveActivityIds(candidate.getActiveActivityIds());
        dto.setActiveActivityNames(resolveActivityNames(candidate));
        dto.setJobCount(candidate.getJobCount());
        dto.setOverdueJobCount(candidate.getOverdueJobCount());
        dto.setTimerCount(candidate.getTimerCount());
        dto.setOverdueTimerCount(candidate.getOverdueTimerCount());
        dto.setRecommendedAction(candidate.getRecommendedAction());
        dto.setClassification(candidate.getClassification());
        return dto;
    }

    private Map<String, String> resolveActivityNames(Candidate candidate) {
        if (candidate.getActiveActivityIds() == null) {
            return Collections.emptyMap();
        }
        Map<String, String> names = new HashMap<>();
        for (String id : candidate.getActiveActivityIds()) {
            String name = bpmnModelCache.resolveActivityName(candidate.getProcessDefinitionId(), id);
            if (name != null && !name.isEmpty()) {
                names.put(id, name);
            }
        }
        return names;
    }

    private List<TaskSummaryDto> toTaskDtos(List<TaskSummary> tasks) {
        List<TaskSummaryDto> dtos = new ArrayList<>();
        for (TaskSummary task : tasks) {
            TaskSummaryDto dto = new TaskSummaryDto();
            dto.setTaskId(task.getTaskId());
            dto.setName(task.getName());
            dto.setAssignee(task.getAssignee());
            dto.setCreateTime(task.getCreateTime());
            dto.setAgeHours(task.getAgeHours());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<String> truncateTaskSummaries(List<TaskSummary> tasks, int max) {
        List<String> summaries = new ArrayList<>();
        int count = 0;
        for (TaskSummary task : tasks) {
            if (count >= max) {
                break;
            }
            summaries.add(task.toShortString());
            count++;
        }
        if (tasks.size() > max) {
            summaries.add("...truncated");
        }
        return summaries;
    }

    private List<String> truncate(List<String> list, int max) {
        if (list == null) {
            return Collections.emptyList();
        }
        if (list.size() <= max) {
            return list;
        }
        List<String> truncated = new ArrayList<>(list.subList(0, max));
        truncated.add("...truncated");
        return truncated;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private interface ScanConsumer {
        void accept(Candidate candidate);
    }

    private static class ScanAccumulator {
        private final int page;
        private final int size;
        private final List<ProcessSummaryDto> items = new ArrayList<>();
        private long totalItems;
        private long waitCount;
        private long escalateCount;
        private long terminateCount;

        ScanAccumulator(int page, int size) {
            this.page = page;
            this.size = size;
        }
    }
}
