package com.company.flowable.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableConfigurationProperties({OpsCleanupProperties.class, OpsSecurityProperties.class, OpsAlertsProperties.class})
@Import(FlowableEngineConfig.class)
public class OpsCleanupApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsCleanupApplication.class, args);
    }
}
