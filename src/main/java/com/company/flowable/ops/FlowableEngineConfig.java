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
     * Some environments have Flowable schema properties stamped as 6.7.2.1, but the bundled
     * libraries only understand 6.7.2. Normalize the values before the engine is built to avoid
     * "unknown version" startup failures.
     */
    @Bean
    public InitializingBean flowableSchemaVersionNormalizer(JdbcTemplate jdbcTemplate) {
        return () -> {
            int updated = jdbcTemplate.update(
                    "update ACT_GE_PROPERTY set VALUE_ = ? where VALUE_ = ? and NAME_ like ?",
                    "6.7.2", "6.7.2.1", "%schema.version%");
            if (updated > 0) {
                LOGGER.info("Normalized Flowable schema version entries from 6.7.2.1 to 6.7.2 ({} rows)", updated);
            } else {
                LOGGER.debug("No Flowable schema version rows required normalization");
            }
        };
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
