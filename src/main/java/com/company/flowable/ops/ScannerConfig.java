package com.company.flowable.ops;

import java.util.Set;

public interface ScannerConfig {
    int getMaxPerRun();

    Set<String> getProcDefKeyAllowList();

    Set<String> getProcDefKeyDenyList();

    boolean isIncludeSubprocesses();
}
