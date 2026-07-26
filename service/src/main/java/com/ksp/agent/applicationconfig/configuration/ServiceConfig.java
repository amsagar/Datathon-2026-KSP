package com.ksp.agent.applicationconfig.configuration;

import com.ksp.agent.chat.usage.UsagePricingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;

/**
 * Two independent datasources:
 * <ul>
 *   <li><b>app</b> ({@code spring.datasource}, {@link Primary}) — all of our application data:
 *       chat sessions, assistants, tools, skills, memory, users, usage, etc. Spring Boot's
 *       {@code spring.sql.init} and the Spring AI JDBC chat-memory repository bind to this
 *       (the primary) datasource, and every repository that injects a bare {@link JdbcTemplate}
 *       resolves {@code appJdbcTemplate}.</li>
 *   <li><b>crime</b> ({@code crime.datasource}) — the read-only Karnataka Police FIR database.
 *       Only {@code CrimeDatabaseTools} talks to it, via {@code crimeJdbcTemplate}.</li>
 * </ul>
 * The FIR schema is initialised against the crime datasource here (Boot's {@code spring.sql.init}
 * only targets the primary/app datasource); the bulk FIR dataset is still loaded manually from
 * {@code scripts/fir-seed.sql}.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(UsagePricingProperties.class)
public class ServiceConfig {

    /**
     * Bounded executor backing every {@code @Async} method (summarization, fact extraction, title
     * generation). Without this bean, {@code @EnableAsync} falls back to an unbounded
     * {@code SimpleAsyncTaskExecutor} that spawns a new thread per task.
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        return executor;
    }

    // ---------- App datasource (primary) ----------

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource appDataSource(@Qualifier("appDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("appDataSource") DataSource appDataSource) {
        return new JdbcTemplate(appDataSource);
    }

    // ---------- Crime datasource (FIR, read-only) ----------

    @Bean
    @ConfigurationProperties("crime.datasource")
    public DataSourceProperties crimeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("crime.datasource.hikari")
    public DataSource crimeDataSource(@Qualifier("crimeDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate crimeJdbcTemplate(@Qualifier("crimeDataSource") DataSource crimeDataSource) {
        return new JdbcTemplate(crimeDataSource);
    }

    /**
     * Creates/updates the FIR schema on the crime datasource on boot (idempotent — DDL uses
     * {@code CREATE TABLE IF NOT EXISTS}, views use {@code CREATE OR REPLACE}/{@code DROP IF
     * EXISTS}, data backfills guard on {@code WHERE NOT EXISTS}). The bulk dataset in
     * {@code scripts/fir-seed.sql} is loaded manually, not here — everything here can run against
     * either an empty crime DB (tests) or an already-seeded one (prod) without error or duplication.
     */
    @Bean
    public DataSourceInitializer crimeDataSourceInitializer(
            @Qualifier("crimeDataSource") DataSource crimeDataSource,
            @Value("classpath:sql-models/fir-schema.sql") Resource firSchema,
            @Value("classpath:sql-models/entity-resolution.sql") Resource entityResolution,
            @Value("classpath:sql-models/official-tables-backfill.sql") Resource officialTablesBackfill,
            @Value("classpath:sql-models/financial-crime-model.sql") Resource financialSchema,
            @Value("classpath:sql-models/post-reseed-features.sql") Resource postReseedFeatures) {
        // Order matters: entityResolution needs accused/case_master (firSchema) and redefines
        // offender_risk_score before financialSchema's seed step queries it; officialTablesBackfill
        // needs arrest_surrender/case_master data (a no-op on an empty DB, populates on a seeded one);
        // postReseedFeatures' financial_transaction_risk view needs financial_transaction to exist.
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                firSchema, entityResolution, officialTablesBackfill, financialSchema, postReseedFeatures);
        populator.setContinueOnError(false);
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(crimeDataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
