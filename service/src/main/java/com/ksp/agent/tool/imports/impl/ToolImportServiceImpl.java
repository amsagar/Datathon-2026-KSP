package com.ksp.agent.tool.imports.impl;

import tools.jackson.databind.ObjectMapper;
import com.ksp.agent.tool.dto.response.AgentToolDto;
import com.ksp.agent.tool.dto.response.ToolGroupDto;
import com.ksp.agent.tool.entity.AgentTool;
import com.ksp.agent.tool.imports.ToolImportService;
import com.ksp.agent.tool.imports.dto.ImportRequest;
import com.ksp.agent.tool.imports.dto.ImportResult;
import com.ksp.agent.tool.service.AgentToolService;
import com.ksp.agent.tool.service.ToolGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ToolImportServiceImpl implements ToolImportService {

    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}/]+)}");
    private static final Pattern CURL_URL_FLAG =
            Pattern.compile("(?:^|\\s)(?:--url)\\s+('([^']*)'|\"([^\"]*)\"|(\\S+))");
    private static final Pattern CURL_DIRECT_URL =
            Pattern.compile("(^|\\s)('https?://[^'\\s]+'|\"https?://[^\"\\s]+\"|https?://\\S+)");

    private final ObjectMapper objectMapper;
    private final AgentToolService toolService;
    private final ToolGroupService groupService;

    public ToolImportServiceImpl(ObjectMapper objectMapper, AgentToolService toolService,
                                 ToolGroupService groupService) {
        this.objectMapper = objectMapper;
        this.toolService = toolService;
        this.groupService = groupService;
    }

    @Override
    public ImportResult importByKind(String kind, String assistantId, ImportRequest request) {
        if (assistantId == null || assistantId.isBlank()) {
            throw new IllegalArgumentException("assistantId is required to import tools");
        }
        String normalizedKind = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        String content = resolveImportContent(normalizedKind, request);
        String host = request == null ? null : request.getHost();
        List<AgentTool> parsed = switch (normalizedKind) {
            case "curl" -> List.of(parseCurl(content));
            case "openapi" -> parseOpenApi(content, host);
            case "postman" -> parsePostman(content);
            default -> throw new IllegalArgumentException("Unsupported import kind: " + kind);
        };
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("No tools found in import source");
        }

        String groupId = null;
        String groupName = null;
        if ("openapi".equals(normalizedKind) || "postman".equals(normalizedKind)) {
            groupName = resolveImportGroupName(normalizedKind, content, request);
            String sourceType = "openapi".equals(normalizedKind) ? "openapi_import" : "postman_import";
            String description = "openapi".equals(normalizedKind)
                    ? "Imported from OpenAPI spec"
                    : "Imported from Postman collection";
            ToolGroupDto group = groupService.createImported(assistantId, groupName, description, sourceType);
            groupId = group.getId();
            groupName = group.getName();
        }

        List<AgentToolDto> saved = new ArrayList<>();
        for (AgentTool tool : parsed) {
            if (host != null && !host.isBlank() && (tool.getHost() == null || tool.getHost().isBlank())) {
                tool.setHost(host.trim());
            }
            if (groupId != null) {
                tool.setGroupId(groupId);
            }
            saved.add(toolService.persistImported(assistantId, tool));
        }
        log.info("Imported {} tool(s) via {} into group {}", saved.size(), kind, groupId);
        return new ImportResult(saved.size(), saved, groupId, groupName);
    }

    private String resolveImportGroupName(String kind, String content, ImportRequest request) {
        if (request != null && request.getGroupName() != null && !request.getGroupName().isBlank()) {
            return sanitizeName(request.getGroupName().trim());
        }
        if ("openapi".equals(kind)) {
            return extractOpenApiGroupName(content);
        }
        if ("postman".equals(kind)) {
            return extractPostmanGroupName(content);
        }
        return "Imported tools";
    }

    @SuppressWarnings("unchecked")
    private String extractOpenApiGroupName(String spec) {
        try {
            Map<String, Object> root = objectMapper.readValue(spec, Map.class);
            Object infoObj = root.get("info");
            if (infoObj instanceof Map<?, ?> info) {
                Object title = info.get("title");
                if (title != null && !String.valueOf(title).isBlank()) {
                    return sanitizeName(String.valueOf(title));
                }
            }
        } catch (Exception ignored) {
            // Fall through to default.
        }
        return "OpenAPI import";
    }

    @SuppressWarnings("unchecked")
    private String extractPostmanGroupName(String collectionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(collectionJson, Map.class);
            Object infoObj = root.get("info");
            if (infoObj instanceof Map<?, ?> info) {
                Object name = info.get("name");
                if (name != null && !String.valueOf(name).isBlank()) {
                    return sanitizeName(String.valueOf(name));
                }
            }
        } catch (Exception ignored) {
            // Fall through to default.
        }
        return "Postman import";
    }

    private String resolveImportContent(String kind, ImportRequest request) {
        if ("openapi".equals(kind) && request != null) {
            String specUrl = request.getSpecUrl();
            if (specUrl != null && !specUrl.isBlank()) {
                return fetchSpecFromUrl(specUrl);
            }
        }
        String content = request == null ? null : request.getContent();
        if (content == null || content.isBlank()) {
            if ("openapi".equals(kind)) {
                throw new IllegalArgumentException("Upload an OpenAPI JSON file or provide a spec URL");
            }
            if ("postman".equals(kind)) {
                throw new IllegalArgumentException("Upload a Postman collection JSON file");
            }
            throw new IllegalArgumentException("Import content is required");
        }
        return content;
    }

    private String fetchSpecFromUrl(String specUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(specUrl.trim()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .header("Accept", "application/json, application/yaml, text/plain, */*")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("Failed to fetch OpenAPI spec: HTTP " + response.statusCode());
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("OpenAPI spec URL returned empty content");
            }
            return body;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to fetch OpenAPI spec from URL: " + e.getMessage(), e);
        }
    }

    // ---- cURL -------------------------------------------------------------

    private AgentTool parseCurl(String curl) {
        String method = extractCurlMethod(curl);
        String url = extractCurlUrl(curl);
        String body = extractCurlBody(curl);
        UrlParts parts = splitUrl(url);

        AgentTool tool = new AgentTool();
        tool.setName(method.toLowerCase(Locale.ROOT) + "_imported_tool");
        tool.setDescription("Imported from cURL command");
        tool.setSourceType("curl_import");
        tool.setMethod(method);
        tool.setHost(parts.host());
        tool.setEndpoint(parts.endpoint());
        tool.setRequestSchema(toJson(buildInputSchema(parts.endpoint(), body)));
        return tool;
    }

    private String extractCurlMethod(String curl) {
        Matcher m = Pattern.compile("(?:-X|--request)\\s+([A-Za-z]+)").matcher(curl);
        if (m.find()) {
            String method = m.group(1).toUpperCase(Locale.ROOT);
            if (SUPPORTED_METHODS.contains(method)) return method;
        }
        return curl.matches("(?s).*(?:--data|-d|--data-raw)\\s.*") ? "POST" : "GET";
    }

    private String extractCurlUrl(String curl) {
        Matcher flag = CURL_URL_FLAG.matcher(curl);
        if (flag.find()) {
            String token = firstNonBlank(flag.group(2), flag.group(3), flag.group(4));
            if (token != null) return token.trim();
        }
        Matcher direct = CURL_DIRECT_URL.matcher(curl);
        if (direct.find()) {
            String token = direct.group(2);
            if (token != null) return token.replace("'", "").replace("\"", "").trim();
        }
        return "/";
    }

    private String extractCurlBody(String curl) {
        Matcher m = Pattern.compile("(?:--data-raw|--data|-d)\\s+('([^']*)'|\"([^\"]*)\"|(\\S+))").matcher(curl);
        if (m.find()) {
            return firstNonBlank(m.group(2), m.group(3), m.group(4));
        }
        return null;
    }

    // ---- OpenAPI 3 --------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<AgentTool> parseOpenApi(String spec, String hostOverride) {
        List<AgentTool> imported = new ArrayList<>();
        try {
            Map<String, Object> root = objectMapper.readValue(spec, Map.class);
            Object pathsObj = root.get("paths");
            if (!(pathsObj instanceof Map<?, ?> pathsRaw) || pathsRaw.isEmpty()) {
                return imported;
            }
            String baseHost = (hostOverride != null && !hostOverride.isBlank())
                    ? hostOverride.trim()
                    : resolveOpenApiHost(root);

            Map<String, Object> paths = (Map<String, Object>) pathsRaw;
            paths.forEach((path, methodsObj) -> {
                if (!(methodsObj instanceof Map<?, ?> methods)) return;
                methods.forEach((methodRaw, operationObj) -> {
                    String method = String.valueOf(methodRaw).toUpperCase(Locale.ROOT);
                    if (!SUPPORTED_METHODS.contains(method)) return;
                    Map<String, Object> operation = operationObj instanceof Map<?, ?> op
                            ? (Map<String, Object>) op
                            : Map.of();
                    String opId = String.valueOf(operation.getOrDefault("operationId", ""));
                    String summary = String.valueOf(operation.getOrDefault("summary", ""));
                    String name = (opId.isBlank())
                            ? method.toLowerCase(Locale.ROOT) + "_" + path.replaceAll("[^a-zA-Z0-9]+", "_")
                            : opId;

                    AgentTool tool = new AgentTool();
                    tool.setName(sanitizeName(name));
                    tool.setDescription(summary.isBlank() ? "Imported from OpenAPI " + method + " " + path : summary);
                    tool.setSourceType("openapi_import");
                    tool.setMethod(method);
                    tool.setHost(baseHost);
                    tool.setEndpoint(normalizePath(path));
                    tool.setRequestSchema(toJson(buildOpenApiInputSchema(path, operation)));
                    imported.add(tool);
                });
            });
            return imported;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OpenAPI spec: " + e.getMessage(), e);
        }
    }

    private String resolveOpenApiHost(Map<String, Object> root) {
        Object serversObj = root.get("servers");
        if (serversObj instanceof List<?> servers && !servers.isEmpty()
                && servers.get(0) instanceof Map<?, ?> server) {
            Object url = server.get("url");
            if (url != null && !String.valueOf(url).isBlank()) {
                return splitUrl(String.valueOf(url).trim()).host();
            }
        }
        Object hostObj = root.get("host");
        if (hostObj != null && !String.valueOf(hostObj).isBlank()) {
            return "https://" + String.valueOf(hostObj).trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildOpenApiInputSchema(String path, Map<String, Object> operation) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Matcher matcher = PLACEHOLDER.matcher(path == null ? "" : path);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key != null && !key.isBlank()) {
                properties.put(key, Map.of("type", "string"));
                required.add(key);
            }
        }

        Object paramsObj = operation.get("parameters");
        if (paramsObj instanceof List<?> params) {
            for (Object paramObj : params) {
                if (!(paramObj instanceof Map<?, ?> param)) continue;
                String name = asText(param.get("name"));
                if (name == null || name.isBlank() || properties.containsKey(name)) continue;
                Object paramSchema = param.get("schema");
                Map<String, Object> propSchema = paramSchema instanceof Map<?, ?> ps
                        ? new LinkedHashMap<>((Map<String, Object>) ps)
                        : new LinkedHashMap<>(Map.of("type", "string"));
                properties.put(name, propSchema);
                if (Boolean.TRUE.equals(param.get("required"))) required.add(name);
            }
        }

        Map<String, Object> bodyProps = extractRequestBodyProperties(operation.get("requestBody"));
        bodyProps.forEach(properties::putIfAbsent);

        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required.stream().distinct().toList());
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRequestBodyProperties(Object requestBodyObj) {
        if (!(requestBodyObj instanceof Map<?, ?> requestBody)) return Map.of();
        Object contentObj = requestBody.get("content");
        if (!(contentObj instanceof Map<?, ?> content) || content.isEmpty()) return Map.of();
        Object media = content.get("application/json");
        if (media == null) media = content.values().iterator().next();
        if (!(media instanceof Map<?, ?> mediaMap)) return Map.of();
        Object schemaObj = mediaMap.get("schema");
        if (!(schemaObj instanceof Map<?, ?> schema)) return Map.of();
        Object props = schema.get("properties");
        return props instanceof Map<?, ?> propsMap ? (Map<String, Object>) propsMap : Map.of();
    }

    // ---- Postman ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<AgentTool> parsePostman(String collectionJson) {
        List<AgentTool> imported = new ArrayList<>();
        try {
            Map<String, Object> root = objectMapper.readValue(collectionJson, Map.class);
            Object itemObj = root.get("item");
            if (!(itemObj instanceof List<?> items) || items.isEmpty()) {
                return imported;
            }
            for (Map<String, Object> item : flattenPostmanItems(items)) {
                AgentTool tool = toToolFromPostmanItem(item);
                if (tool != null) imported.add(tool);
            }
            return imported;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Postman collection: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenPostmanItems(List<?> source) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : source) {
            if (!(raw instanceof Map<?, ?> mapRaw)) continue;
            Map<String, Object> item = (Map<String, Object>) mapRaw;
            if (item.get("request") instanceof Map<?, ?>) out.add(item);
            if (item.get("item") instanceof List<?> nested && !nested.isEmpty()) {
                out.addAll(flattenPostmanItems(nested));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private AgentTool toToolFromPostmanItem(Map<String, Object> item) {
        if (!(item.get("request") instanceof Map<?, ?> requestRaw)) return null;
        Map<String, Object> request = (Map<String, Object>) requestRaw;
        String method = asText(request.get("method"));
        method = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(method)) return null;

        UrlParts parts = postmanUrlParts(request.get("url"));
        if (parts == null) return null;

        String itemName = asText(item.get("name"));
        String name = sanitizeName(itemName == null || itemName.isBlank()
                ? method.toLowerCase(Locale.ROOT) + "_postman_tool" : itemName);
        String description = asText(request.get("description"));

        AgentTool tool = new AgentTool();
        tool.setName(name);
        tool.setDescription(description == null || description.isBlank()
                ? "Imported from Postman" : description);
        tool.setSourceType("postman_import");
        tool.setMethod(method);
        tool.setHost(parts.host());
        tool.setEndpoint(parts.endpoint());
        tool.setRequestSchema(toJson(buildInputSchema(parts.endpoint(), null)));
        return tool;
    }

    @SuppressWarnings("unchecked")
    private UrlParts postmanUrlParts(Object urlObj) {
        if (urlObj == null) return null;
        if (urlObj instanceof String raw) return splitUrl(raw);
        if (!(urlObj instanceof Map<?, ?> urlRaw)) return null;
        Map<String, Object> url = (Map<String, Object>) urlRaw;
        String raw = asText(url.get("raw"));
        if (raw != null && !raw.isBlank()) return splitUrl(raw);
        String protocol = asText(url.get("protocol"));
        String host = joinPathish(url.get("host"), ".");
        String path = "/" + joinPathish(url.get("path"), "/");
        if (protocol != null && host != null && !host.isBlank()) {
            return new UrlParts(protocol + "://" + host, normalizePath(path));
        }
        return new UrlParts(null, normalizePath(path));
    }

    @SuppressWarnings("unchecked")
    private String joinPathish(Object value, String separator) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        if (value instanceof Collection<?> values) {
            List<String> parts = new ArrayList<>();
            for (Object row : values) {
                if (row != null) parts.add(String.valueOf(row));
            }
            return String.join(separator, parts);
        }
        return String.valueOf(value);
    }

    // ---- shared helpers ---------------------------------------------------

    private Map<String, Object> buildInputSchema(String endpoint, String body) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (endpoint != null) {
            Matcher matcher = PLACEHOLDER.matcher(endpoint);
            while (matcher.find()) {
                String key = matcher.group(1);
                if (key == null || key.isBlank()) continue;
                properties.put(key, Map.of("type", "string"));
                required.add(key);
            }
        }

        if (body != null && !body.isBlank()) {
            try {
                Object parsed = objectMapper.readValue(body, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (key.isBlank() || properties.containsKey(key)) continue;
                        properties.put(key, Map.of("type", jsonSchemaTypeOf(entry.getValue())));
                    }
                }
            } catch (Exception ignored) {
                // Non-JSON body (form-encoded etc.) — path-only schema.
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required.stream().distinct().toList());
        return schema;
    }

    private String jsonSchemaTypeOf(Object value) {
        if (value == null) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Integer || value instanceof Long || value instanceof Short) return "integer";
        if (value instanceof Number) return "number";
        if (value instanceof List<?>) return "array";
        if (value instanceof Map<?, ?>) return "object";
        return "string";
    }

    private UrlParts splitUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return new UrlParts(null, "/");
        String cleaned = normalizePostmanPlaceholders(rawUrl.trim().replace("'", "").replace("\"", ""));
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            try {
                URI parsed = URI.create(cleaned);
                int port = parsed.getPort();
                String host = parsed.getScheme() + "://" + parsed.getHost() + (port > 0 ? ":" + port : "");
                String endpoint = parsed.getRawPath();
                if (endpoint == null || endpoint.isBlank()) endpoint = "/";
                return new UrlParts(host, endpoint);
            } catch (Exception ignored) {
                Matcher m = Pattern.compile("^(https?://[^/\\s]+)(/.*)?$").matcher(cleaned);
                if (m.matches()) {
                    String endpoint = m.group(2);
                    return new UrlParts(m.group(1), endpoint == null || endpoint.isBlank() ? "/" : endpoint);
                }
            }
        }
        return new UrlParts(null, normalizePath(cleaned));
    }

    private String normalizePostmanPlaceholders(String value) {
        if (value == null || value.isBlank()) return value;
        return value.replaceAll("\\{\\{\\s*([A-Za-z0-9_\\-]+)\\s*}}", "{$1}");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String cleaned = normalizePostmanPlaceholders(path.trim());
        return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
    }

    private String sanitizeName(String value) {
        if (value == null || value.isBlank()) return "imported_tool";
        String cleaned = value.trim().replaceAll("[^a-zA-Z0-9_\\-]+", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private record UrlParts(String host, String endpoint) {}
}
