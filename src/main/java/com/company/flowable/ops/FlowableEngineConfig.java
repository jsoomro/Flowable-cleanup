package com.company.flowable.ops;

import javax.sql.DataSource;

import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class FlowableEngineConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowableEngineConfig.class);

    /**
     * Some environments have Flowable schema properties stamped as 6.7.2 or 6.7.2.1, but the
     * community artifacts expect the four-part version 6.7.2.0. Normalize before engine creation to
     * avoid "unknown version" startup failures.
     */
    @Bean
    public InitializingBean flowableSchemaVersionNormalizer(JdbcTemplate jdbcTemplate) {
        return () -> {
            int geRows = normalizeSchemaVersion(jdbcTemplate, "ACT_GE_PROPERTY");
            int idRows = normalizeSchemaVersion(jdbcTemplate, "ACT_ID_PROPERTY");
            int geHistoryRows = normalizeSchemaHistory(jdbcTemplate, "ACT_GE_PROPERTY");
            int idHistoryRows = normalizeSchemaHistory(jdbcTemplate, "ACT_ID_PROPERTY");
            int totalHistory = geHistoryRows + idHistoryRows;
            int total = geRows + idRows;
            if (total > 0 || totalHistory > 0) {
                LOGGER.info("Normalized Flowable schema version entries to 6.7.2.0 ({} rows) and schema history entries ({} rows)",
                        total, totalHistory);
            } else {
                LOGGER.debug("No Flowable schema version rows required normalization");
            }
        };
    }

    private int normalizeSchemaVersion(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            int patchedFromPatchLevel = jdbcTemplate.update(
                    "update " + tableName + " set VALUE_ = ? where VALUE_ = ? and NAME_ like ?",
                    "6.7.2.0", "6.7.2.1", "%schema.version%");
            int patchedFromShort = jdbcTemplate.update(
                    "update " + tableName + " set VALUE_ = ? where VALUE_ = ? and NAME_ like ?",
                    "6.7.2.0", "6.7.2", "%schema.version%");
            return patchedFromPatchLevel + patchedFromShort;
        } catch (Exception ex) {
            LOGGER.debug("Skipping schema normalization for {} (likely table absent): {}", tableName, ex.getMessage());
            return 0;
        }
    }

    private int normalizeSchemaHistory(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            // Normalize history upgrade marker to the version understood by the engine.
            return jdbcTemplate.update(
                    "update " + tableName + " set VALUE_ = ? where NAME_ = 'schema.history' and VALUE_ like ?",
                    "upgrade(6.4.1.3->6.7.2.0)", "upgrade(6.4.1.3->6.7.2.1)%");
        } catch (Exception ex) {
            LOGGER.debug("Skipping schema history normalization for {} (likely table absent): {}", tableName, ex.getMessage());
            return 0;
        }
    }

    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration(DataSource dataSource,
            PlatformTransactionManager transactionManager,
            @Value("${flowable.database-schema-update:false}") String databaseSchemaUpdate) {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setTransactionManager(transactionManager);
        config.setDatabaseSchemaUpdate(databaseSchemaUpdate);
        // Flowable 6.7.2 SpringProcessEngineConfiguration doesn't expose setJobExecutorActivate.
        config.setAsyncExecutorActivate(false);
        config.setAsyncHistoryExecutorActivate(false);
        return config;
    }

    @Bean
    @DependsOn("flowableSchemaVersionNormalizer")
    public ProcessEngineFactoryBean processEngineFactoryBean(SpringProcessEngineConfiguration config) {
        ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
        factoryBean.setProcessEngineConfiguration(config);
        return factoryBean;
    }

    @Bean
    public ProcessEngine processEngine(ProcessEngineFactoryBean factoryBean) throws Exception {
        return factoryBean.getObject();
    }

    @Bean
    public RuntimeService runtimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    @Bean
    public TaskService taskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    @Bean
    public HistoryService historyService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    @Bean
    public ManagementService managementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }

    @Bean
    public IdentityService identityService(ProcessEngine processEngine) {
        return processEngine.getIdentityService();
    }

    @Bean
    public RepositoryService repositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }
}
