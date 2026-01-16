package com.company.flowable.ops;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StuckProcessMonitor {
    private static final Logger logger = LoggerFactory.getLogger(StuckProcessMonitor.class);

    private final OpsCleanupService cleanupService;
    private final OpsCleanupProperties cleanupProperties;
    private final OpsAlertsProperties alertsProperties;
    private final Map<String, Seen> seenMap = new ConcurrentHashMap<>();

    public StuckProcessMonitor(OpsCleanupService cleanupService,
                               OpsCleanupProperties cleanupProperties,
                               OpsAlertsProperties alertsProperties) {
        this.cleanupService = cleanupService;
        this.cleanupProperties = cleanupProperties;
        this.alertsProperties = alertsProperties;
    }

    @Scheduled(fixedDelayString = "#{@opsAlertsProperties.intervalMillis}")
    public void poll() {
        if (!alertsProperties.isEnabled()) {
            return;
        }
        try {
            checkForStuck();
        } catch (Exception ex) {
            logger.warn("Stuck process monitor failed", ex);
        }
    }

    private void checkForStuck() {
        int thresholdHours = alertsProperties.getThresholdHours() > 0
            ? alertsProperties.getThresholdHours()
            : cleanupProperties.getDefaultHours();
        FilterCriteria criteria = new FilterCriteria();
        criteria.setHours(thresholdHours);
        criteria.setAction("TERMINATE");
        criteria.setPage(0);
        criteria.setSize(Math.min(alertsProperties.getSampleSize(), cleanupProperties.getMaxPageSize()));

        PageResult<ProcessSummaryDto> page = cleanupService.findCandidates(criteria);
        Instant now = Instant.now();
        Set<String> current = new HashSet<>();
        for (ProcessSummaryDto dto : page.getItems()) {
            current.add(dto.getProcessInstanceId());
            Seen seen = seenMap.computeIfAbsent(dto.getProcessInstanceId(),
                k -> new Seen(dto.getProcessInstanceId(), dto.getProcessDefinitionKey()));
            seen.count++;
            seen.lastSeen = now;
            seen.hoursRunning = dto.getHoursRunning();
        }

        // remove entries that disappeared (resolved)
        seenMap.keySet().removeIf(pid -> !current.contains(pid));

        List<Seen> stuck = new ArrayList<>();
        for (Seen entry : seenMap.values()) {
            if (entry.count >= alertsProperties.getRepeatCount()) {
                stuck.add(entry);
            }
        }
        stuck.sort(Comparator.comparingLong((Seen s) -> s.hoursRunning).reversed());
        if (!stuck.isEmpty()) {
            int sampleSize = Math.min(alertsProperties.getSampleSize(), stuck.size());
            List<Seen> sample = stuck.subList(0, sampleSize);
            logger.warn("Stuck backlog detected: total={}, thresholdHours={}, repeatCount={}, sample={}",
                page.getPage() == null ? stuck.size() : page.getPage().getTotalItems(),
                thresholdHours,
                alertsProperties.getRepeatCount(),
                formatSample(sample));
        }
    }

    private String formatSample(List<Seen> sample) {
        List<String> parts = new ArrayList<>();
        for (Seen s : sample) {
            parts.add(s.processInstanceId + "|" + s.processDefinitionKey + "|" + s.hoursRunning + "h");
        }
        return parts.toString();
    }

    public List<StuckProcessInfo> getStuckProcesses() {
        List<StuckProcessInfo> stuck = new ArrayList<>();
        for (Seen entry : seenMap.values()) {
            if (entry.count >= alertsProperties.getRepeatCount()) {
                stuck.add(new StuckProcessInfo(entry.processInstanceId, entry.processDefinitionKey, entry.hoursRunning, entry.count, entry.lastSeen));
            }
        }
        stuck.sort(Comparator.comparingLong(StuckProcessInfo::getHoursRunning).reversed());
        int limit = Math.min(alertsProperties.getSampleSize(), stuck.size());
        return new ArrayList<>(stuck.subList(0, limit));
    }

    private static class Seen {
        final String processInstanceId;
        final String processDefinitionKey;
        long count = 0;
        long hoursRunning = 0;
        Instant lastSeen;

        Seen(String pid, String procDefKey) {
            this.processInstanceId = pid;
            this.processDefinitionKey = procDefKey;
        }
    }

    public static class StuckProcessInfo {
        private final String processInstanceId;
        private final String processDefinitionKey;
        private final long hoursRunning;
        private final long seenCount;
        private final Instant lastSeen;

        public StuckProcessInfo(String processInstanceId, String processDefinitionKey, long hoursRunning, long seenCount, Instant lastSeen) {
            this.processInstanceId = processInstanceId;
            this.processDefinitionKey = processDefinitionKey;
            this.hoursRunning = hoursRunning;
            this.seenCount = seenCount;
            this.lastSeen = lastSeen;
        }

        public String getProcessInstanceId() {
            return processInstanceId;
        }

        public String getProcessDefinitionKey() {
            return processDefinitionKey;
        }

        public long getHoursRunning() {
            return hoursRunning;
        }

        public long getSeenCount() {
            return seenCount;
        }

        public Instant getLastSeen() {
            return lastSeen;
        }
    }
}
