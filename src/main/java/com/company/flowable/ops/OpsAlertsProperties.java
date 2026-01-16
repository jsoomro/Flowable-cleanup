package com.company.flowable.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.alerts")
public class OpsAlertsProperties {
    private boolean enabled = false;
    private int thresholdHours = 24;
    private int repeatCount = 3;
    private int sampleSize = 20;
    private long intervalSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getThresholdHours() {
        return thresholdHours;
    }

    public void setThresholdHours(int thresholdHours) {
        this.thresholdHours = thresholdHours;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public long getIntervalMillis() {
        return Math.max(1, intervalSeconds) * 1000;
    }
}
