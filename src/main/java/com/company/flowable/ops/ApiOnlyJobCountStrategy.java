package com.company.flowable.ops;

import java.time.Instant;
import java.util.List;

import org.flowable.engine.ManagementService;

public class ApiOnlyJobCountStrategy implements JobCountStrategy {
    private final ManagementService managementService;

    public ApiOnlyJobCountStrategy(ManagementService managementService) {
        this.managementService = managementService;
    }

    @Override
    public void countJobsAndTimers(List<String> processInstanceIds, Instant now, CleanupScanner.PrefetchData data) {
        for (String pid : processInstanceIds) {
            int jobCount = (int) managementService.createJobQuery().processInstanceId(pid).count();
            int overdueJobCount = (int) managementService.createJobQuery()
                .processInstanceId(pid)
                .duedateLowerThan(java.util.Date.from(now))
                .count();
            int timerCount = (int) managementService.createTimerJobQuery().processInstanceId(pid).count();
            int overdueTimerCount = (int) managementService.createTimerJobQuery()
                .processInstanceId(pid)
                .duedateLowerThan(java.util.Date.from(now))
                .count();
            data.jobCountByProcessId.put(pid, jobCount);
            data.overdueJobCountByProcessId.put(pid, overdueJobCount);
            data.timerCountByProcessId.put(pid, timerCount);
            data.overdueTimerCountByProcessId.put(pid, overdueTimerCount);
        }
    }
}
