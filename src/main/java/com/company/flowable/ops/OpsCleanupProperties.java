package com.company.flowable.ops;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ops.cleanup")
public class OpsCleanupProperties {
    private boolean enabled = true;
    private boolean dryRun = true;
    private int defaultHours = 6;
    private int taskEscalationHours = 6;
    private int maxPageSize = 200;
    private int maxBulkDelete = 200;
    private int retryCount = 2;
    private long retryBackoffMillis = 500;
    private long delayBetweenDeletesMillis = 50;
    private QueryStrategy queryStrategy = QueryStrategy.API_ONLY;
    private NativeSql nativeSql = new NativeSql();
    private Audit audit = new Audit();
    private List<String> allowProcDefKeys = new ArrayList<>();
    private List<String> denyProcDefKeys = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int getDefaultHours() {
        return defaultHours;
    }

    public void setDefaultHours(int defaultHours) {
        this.defaultHours = defaultHours;
    }

    public int getTaskEscalationHours() {
        return taskEscalationHours;
    }

    public void setTaskEscalationHours(int taskEscalationHours) {
        this.taskEscalationHours = taskEscalationHours;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxBulkDelete() {
        return maxBulkDelete;
    }

    public void setMaxBulkDelete(int maxBulkDelete) {
        this.maxBulkDelete = maxBulkDelete;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public long getDelayBetweenDeletesMillis() {
        return delayBetweenDeletesMillis;
    }

    public void setDelayBetweenDeletesMillis(long delayBetweenDeletesMillis) {
        this.delayBetweenDeletesMillis = delayBetweenDeletesMillis;
    }

    public QueryStrategy getQueryStrategy() {
        return queryStrategy;
    }

    public void setQueryStrategy(QueryStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
    }

    public NativeSql getNativeSql() {
        return nativeSql;
    }

    public void setNativeSql(NativeSql nativeSql) {
        this.nativeSql = nativeSql;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    public List<String> getAllowProcDefKeys() {
        return allowProcDefKeys;
    }

    public void setAllowProcDefKeys(List<String> allowProcDefKeys) {
        this.allowProcDefKeys = allowProcDefKeys;
    }

    public List<String> getDenyProcDefKeys() {
        return denyProcDefKeys;
    }

    public void setDenyProcDefKeys(List<String> denyProcDefKeys) {
        this.denyProcDefKeys = denyProcDefKeys;
    }

    public static class Audit {
        private String file = "logs/ops-cleanup-audit.jsonl";
        private boolean dbEnabled = false;

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public boolean isDbEnabled() {
            return dbEnabled;
        }

        public void setDbEnabled(boolean dbEnabled) {
            this.dbEnabled = dbEnabled;
        }
    }

        public class NativeSql {
        private String tablePrefix = "ACT_";
        private int inClauseLimit = 1000;
        private String processInstanceIdColumn = "PROCESS_INSTANCE_ID_"; // Defaulting to the corrected name
        private String dueDateColumn = "DUEDATE_"; // Defaulting to the existing name

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }

        public int getInClauseLimit() {
            return inClauseLimit;
        }

        public void setInClauseLimit(int inClauseLimit) {
            this.inClauseLimit = inClauseLimit;
        }

        public String getProcessInstanceIdColumn() {
            return processInstanceIdColumn;
        }

        public void setProcessInstanceIdColumn(String processInstanceIdColumn) {
            this.processInstanceIdColumn = processInstanceIdColumn;
        }

        public String getDueDateColumn() {
            return dueDateColumn;
        }

        public void setDueDateColumn(String dueDateColumn) {
            this.dueDateColumn = dueDateColumn;
        }
    }

    public enum QueryStrategy {
        API_ONLY,
        NATIVE_SQL
    }
}
