package com.primepay.api.config;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiquibaseConfig {
    private static final String MASTER_CHANGELOG = "classpath:db/changelog/db.changelog-master.xml";

    @Bean
    SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(MASTER_CHANGELOG);
        return liquibase;
    }
}
