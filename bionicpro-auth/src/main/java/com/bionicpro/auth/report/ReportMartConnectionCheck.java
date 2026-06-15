package com.bionicpro.auth.report;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ReportMartConnectionCheck {

    @Bean
    ApplicationRunner verifyReportMartConnection(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }
}
