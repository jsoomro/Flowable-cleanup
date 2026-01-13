package com.company.flowable.ops;

import org.flowable.engine.ManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OpsCleanupStrategyConfig {

    @Bean
    public JobCountStrategy jobCountStrategy(OpsCleanupProperties props,
                                             ManagementService managementService,
                                             JdbcTemplate jdbcTemplate) {
        if (props.getQueryStrategy() == OpsCleanupProperties.QueryStrategy.NATIVE_SQL) {
            return new NativeSqlJobCountStrategy(jdbcTemplate, props.getNativeSql());
        }
        return new ApiOnlyJobCountStrategy(managementService);
    }
}
