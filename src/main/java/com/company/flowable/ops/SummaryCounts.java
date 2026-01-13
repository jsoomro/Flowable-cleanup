package com.company.flowable.ops;

public class SummaryCounts {
    private long waitCount;
    private long escalateCount;
    private long terminateCount;

    public SummaryCounts(long waitCount, long escalateCount, long terminateCount) {
        this.waitCount = waitCount;
        this.escalateCount = escalateCount;
        this.terminateCount = terminateCount;
    }

    public long getWaitCount() {
        return waitCount;
    }

    public long getEscalateCount() {
        return escalateCount;
    }

    public long getTerminateCount() {
        return terminateCount;
    }
}
