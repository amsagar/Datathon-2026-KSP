package com.ksp.agent.tool.runtime;

import tools.jackson.databind.ObjectMapper;
import com.ksp.agent.chat.audit.ChatAuditLog;
import com.ksp.agent.tool.auth.service.HttpToolAuthService;
import com.ksp.agent.tool.entity.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes an {@link AgentTool} as an outbound HTTP request. Path placeholders
 * ({@code {name}}) in the endpoint are filled from the JSON input; remaining
 * fields become query params (GET/DELETE) or a JSON body (POST/PUT/PATCH).
 * Auth (inline or via a reusable profile) is resolved by {@link HttpToolAuthService}.
 */
@Component
@Slf4j
public class HttpToolExecutor {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}/]+)}");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpToolAuthService authService;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public HttpToolExecutor(
            ObjectMapper objectMapper,
            HttpToolAuthService authService,
            @Value("${agent.http-tool.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${agent.http-tool.read-timeout-ms:30000}") long readTimeoutMs,
            @Value("${agent.http-tool.max-attempts:2}") int maxAttempts,
            @Value("${agent.http-tool.retry-backoff-ms:1000}") long retryBackoffMs) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public String execute(AgentTool tool, String jsonInput) {
        Map<String, Object> input = parseInput(jsonInput);
        Map<String, Object> remaining = new LinkedHashMap<>(input);

        String endpoint = substitutePathParams(tool.getEndpoint(), remaining);
        String baseUrl = trimTrailingSlash(tool.getHost()) + ensureLeadingSlash(endpoint);
        HttpMethod method = HttpMethod.valueOf(
                (tool.getMethod() == null ? "GET" : tool.getMethod()).trim().toUpperCase());

        HttpToolAuthService.ResolvedAuth auth = authService.resolve(tool);
        Map<String, String> authHeaders = auth.headers();
        Map<String, String> authQuery = auth.queryParams();

        boolean hasBody = method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;

        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl);
        authQuery.forEach(uri::queryParam);
        if (!hasBody) {
            remaining.forEach((k, v) -> uri.queryParam(k, String.valueOf(v)));
        }
        var targetUri = uri.build(true).toUri();

        String bodyJson = null;
        if (hasBody && !remaining.isEmpty()) {
            try {
                bodyJson = objectMapper.writeValueAsString(remaining);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize request body: " + e.getMessage(), e);
            }
        }
        final String body = bodyJson;

        String resolvedUrl = targetUri.toString();
        return sendWithRetry(tool.getName(), method.name(), resolvedUrl, () -> {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(targetUri)
                    .headers(h -> authHeaders.forEach(h::set));
            String response;
            if (body != null) {
                response = spec.header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(body)
                        .retrieve().body(String.class);
            } else {
                response = spec.retrieve().body(String.class);
            }
            return response == null ? "" : response;
        });
    }

    /**
     * Runs the HTTP call, retrying on transient failures — I/O errors (timeouts,
     * connection resets) and 5xx server errors — up to {@code maxAttempts} with a
     * linear backoff. 4xx client errors (e.g. 401/403) are not transient and fail
     * fast so the model sees the real cause immediately.
     */
    private String sendWithRetry(String toolName, String method, String url, Supplier<String> call) {
        Exception lastTransient = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startNanos = System.nanoTime();
            try {
                String response = call.get();
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                ChatAuditLog.httpToolOk(toolName, method, url, durationMs, response.length());
                return response;
            } catch (HttpClientErrorException e) {
                log.warn("HTTP tool '{}' call failed: {}", toolName, e.getMessage());
                throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                lastTransient = e;
                log.warn("HTTP tool '{}' attempt {}/{} failed (transient): {}",
                        toolName, attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleepQuietly(retryBackoffMs * attempt);
                }
            } catch (Exception e) {
                log.warn("HTTP tool '{}' call failed: {}", toolName, e.getMessage());
                throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
            }
        }
        String message = lastTransient == null ? "unknown error" : lastTransient.getMessage();
        log.warn("HTTP tool '{}' failed after {} attempts: {}", toolName, maxAttempts, message);
        throw new RuntimeException(
                "HTTP request failed after " + maxAttempts + " attempts: " + message, lastTransient);
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String substitutePathParams(String endpoint, Map<String, Object> remaining) {
        if (endpoint == null) {
            return "";
        }
        Matcher matcher = PATH_PARAM.matcher(endpoint);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = remaining.remove(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private Map<String, Object> parseInput(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String ensureLeadingSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.startsWith("/") ? s : "/" + s;
    }
}
