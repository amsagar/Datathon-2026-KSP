package com.ksp.agent.audit.config.service;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Admin-configurable toggle: when enabled, non-admin roles (SUPERVISOR/INVESTIGATOR/ANALYST/
 * POLICYMAKER) get read-only access to the config audit feed and revision history — revert stays
 * admin-only regardless. Backs the {@code @auditAccessSettingsService.isNonAdminReadEnabled()}
 * SpEL check on {@link com.ksp.agent.audit.config.controller.ConfigAuditController}'s read
 * endpoints, which are hit on every such request, so the singleton row is cached briefly.
 *
 * <p>Note: this codebase has no existing Caffeine (or other cache-library) usage to mirror, so
 * this uses a small hand-rolled TTL cache rather than introducing a new dependency for a single
 * cached boolean.
 */
@Service
public class AuditAccessSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AuditAccessSettingsService.class);
    private static final long CACHE_TTL_MILLIS = 30_000;

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    private volatile CachedValue cache;

    public AuditAccessSettingsService(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public boolean isNonAdminReadEnabled() {
        CachedValue current = cache;
        long now = System.currentTimeMillis();
        if (current != null && now < current.expiresAtMillis()) {
            return current.value();
        }
        boolean value = fetch();
        cache = new CachedValue(value, now + CACHE_TTL_MILLIS);
        return value;
    }

    /**
     * ADMIN-only caller responsibility — enforced at the controller layer via
     * {@code @PreAuthorize("hasRole('ADMIN')")}, not here.
     */
    public void setNonAdminReadEnabled(boolean enabled, String actor) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("AUDIT_ACCESS_SETTINGS.UPDATE"),
                enabled, actor, Instant.now().getEpochSecond());
        cache = null;
        // No ResourceType fits a global setting change, so this toggle itself is deliberately not
        // recorded as a config_audit_event (see final report for this design gap/deviation).
    }

    private boolean fetch() {
        try {
            Boolean value = jdbcTemplate.queryForObject(
                    sqlQueryLoader.getQuery("AUDIT_ACCESS_SETTINGS.GET"), Boolean.class);
            return value != null && value;
        } catch (Exception e) {
            log.warn("Failed to read audit access settings, defaulting to admin-only: {}", e.getMessage());
            return false;
        }
    }

    private record CachedValue(boolean value, long expiresAtMillis) {
    }
}
