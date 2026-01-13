package com.company.flowable.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableConfigurationProperties({OpsCleanupProperties.class, OpsSecurityProperties.class})
@Import(FlowableEngineConfig.class)
public class OpsCleanupApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsCleanupApplication.class, args);
    }
}
