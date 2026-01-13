package com.company.flowable.ops;

import java.time.Instant;
import java.util.List;

public interface JobCountStrategy {
    void countJobsAndTimers(List<String> processInstanceIds, Instant now, CleanupScanner.PrefetchData data);
}
