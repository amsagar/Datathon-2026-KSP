package com.ksp.agent.applicationconfig.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 auto-configures the Jackson 3 (tools.jackson) ObjectMapper only, while this
 * codebase injects the classic Jackson 2 (com.fasterxml) ObjectMapper throughout. That bean
 * used to arrive via the (now removed) Spring Cloud Azure autoconfiguration, so it is defined
 * explicitly here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
