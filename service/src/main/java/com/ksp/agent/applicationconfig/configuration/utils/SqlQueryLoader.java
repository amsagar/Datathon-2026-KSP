package com.ksp.agent.applicationconfig.configuration.utils;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Properties;

@Component
public class SqlQueryLoader {

    private final Properties sqlQueries = new Properties();

    public SqlQueryLoader() {
        try (var inputStream = getClass().getResourceAsStream("/sql.properties")) {
            if (inputStream != null) {
                sqlQueries.load(inputStream);
            } else {
                throw new IOException("sql.properties file not found");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SQL queries", e);
        }
    }

    public String getQuery(String key) {
        return sqlQueries.getProperty(key);
    }
}
