package com.company.flowable.ops;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

public class NativeSqlJobCountStrategy implements JobCountStrategy {
    private final JdbcTemplate jdbcTemplate;
    private final OpsCleanupProperties.NativeSql nativeSql;

    public NativeSqlJobCountStrategy(JdbcTemplate jdbcTemplate, OpsCleanupProperties.NativeSql nativeSql) {
        this.jdbcTemplate = jdbcTemplate;
        this.nativeSql = nativeSql;
    }

    @Override
    public void countJobsAndTimers(List<String> processInstanceIds, Instant now, CleanupScanner.PrefetchData data) {
        int batchSize = Math.max(1, nativeSql.getInClauseLimit());
        for (int i = 0; i < processInstanceIds.size(); i += batchSize) {
            List<String> batch = processInstanceIds.subList(i, Math.min(i + batchSize, processInstanceIds.size()));
            countJobs(batch, now, data);
            countTimers(batch, now, data);
        }
    }

    private void countJobs(List<String> ids, Instant now, CleanupScanner.PrefetchData data) {
        String table = nativeSql.getTablePrefix() + "RU_JOB";
        String inClause = buildInClause(ids.size());
        String sql = "SELECT PROCESS_INSTANCE_ID_, DUEDATE_ FROM " + table + " WHERE PROCESS_INSTANCE_ID_ IN " + inClause;
        Timestamp nowTs = Timestamp.from(now);
        jdbcTemplate.query(sql, new PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                int index = 1;
                for (String id : ids) {
                    ps.setString(index++, id);
                }
            }
        }, rs -> {
            String pid = rs.getString("PROCESS_INSTANCE_ID_");
            data.jobCountByProcessId.put(pid, data.jobCountByProcessId.getOrDefault(pid, 0) + 1);
            Timestamp due = rs.getTimestamp("DUEDATE_");
            if (due != null && due.before(nowTs)) {
                data.overdueJobCountByProcessId.put(pid, data.overdueJobCountByProcessId.getOrDefault(pid, 0) + 1);
            }
        });
    }

    private void countTimers(List<String> ids, Instant now, CleanupScanner.PrefetchData data) {
        String table = nativeSql.getTablePrefix() + "RU_TIMER_JOB";
        String inClause = buildInClause(ids.size());
        String sql = "SELECT PROCESS_INSTANCE_ID_, DUEDATE_ FROM " + table + " WHERE PROCESS_INSTANCE_ID_ IN " + inClause;
        Timestamp nowTs = Timestamp.from(now);
        jdbcTemplate.query(sql, new PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                int index = 1;
                for (String id : ids) {
                    ps.setString(index++, id);
                }
            }
        }, rs -> {
            String pid = rs.getString("PROCESS_INSTANCE_ID_");
            data.timerCountByProcessId.put(pid, data.timerCountByProcessId.getOrDefault(pid, 0) + 1);
            Timestamp due = rs.getTimestamp("DUEDATE_");
            if (due != null && due.before(nowTs)) {
                data.overdueTimerCountByProcessId.put(pid, data.overdueTimerCountByProcessId.getOrDefault(pid, 0) + 1);
            }
        });
    }

    private String buildInClause(int size) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }
}
