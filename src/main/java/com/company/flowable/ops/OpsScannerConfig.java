package com.company.flowable.ops;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class OpsScannerConfig implements ScannerConfig {
    private final OpsCleanupProperties props;

    public OpsScannerConfig(OpsCleanupProperties props) {
        this.props = props;
    }

    @Override
    public int getMaxPerRun() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Set<String> getProcDefKeyAllowList() {
        return new HashSet<>(props.getAllowProcDefKeys());
    }

    @Override
    public Set<String> getProcDefKeyDenyList() {
        return new HashSet<>(props.getDenyProcDefKeys());
    }

    @Override
    public boolean isIncludeSubprocesses() {
        return true;
    }
}
