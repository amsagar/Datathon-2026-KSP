package com.ksp.agent.applicationconfig.keepalive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Pings the deployed AppSail health URL on a fixed interval so the free-tier
 * instance stays warm. Runs in-process once the service is up; the first external
 * hit (or deploy) wakes it, then this job keeps {@code /health} exercised.
 *
 * <p>Default target:
 * {@code https://ksp-agent-service-50044089204.development.catalystappsail.in/health}
 */
@Component
@ConditionalOnProperty(prefix = "agent.keepalive", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeployedHealthKeepAliveJob {

    private static final Logger log = LoggerFactory.getLogger(DeployedHealthKeepAliveJob.class);

    private final String url;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DeployedHealthKeepAliveJob(
            @Value("${agent.keepalive.url:https://ksp-agent-service-50044089204.development.catalystappsail.in/health}")
            String url) {
        this.url = url == null || url.isBlank()
                ? "https://ksp-agent-service-50044089204.development.catalystappsail.in/health"
                : url.trim();
        log.info("Deployed health keep-alive enabled → {}", this.url);
    }

    @Scheduled(
            fixedRateString = "${agent.keepalive.interval-ms:120000}",
            initialDelayString = "${agent.keepalive.initial-delay-ms:15000}")
    public void ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "ksp-agent-keepalive/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.debug("Keep-alive OK {} → HTTP {}", url, status);
            } else {
                log.warn("Keep-alive unexpected status {} → HTTP {}", url, status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Keep-alive interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Keep-alive failed for {}: {}", url, e.getMessage());
        }
    }
}
