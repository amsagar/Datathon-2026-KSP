package com.ksp.agent.document.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.document.repo.AgentDocumentRepository;
import com.ksp.agent.llm.QuickMlTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for Catalyst QuickML RAG's query endpoint, confirmed via the console's "View API" panel
 * (Generative AI -&gt; RAG -&gt; View API):
 * <pre>{@code
 * POST <base-url><completions-path>
 * Headers: Authorization: Zoho-oauthtoken <token>, CATALYST-ORG: <org>
 * Body:    {"query": "...", "documents": ["<zoho-doc-id>", ...]}
 * Response: {"status": "success", "response": "...", "retrieved_nodes": [...]}
 * }</pre>
 *
 * <p>There is no programmatic upload/delete API for the knowledge base — documents are added via
 * the Zoho console (Generative AI -&gt; Knowledge Base -&gt; Add Documents); an admin then records
 * the resulting Zoho document id on the {@code agent_document} row (PATCH the document with
 * {@code zohoDocumentId}) so {@link #answer} knows which documents to include in the query scope.
 * Reuses the same Zoho OAuth refresh token as the chat model ({@link QuickMlTokenService}) — that
 * token must additionally have the {@code QuickML.rag.READ} scope granted.
 */
@Service
@Slf4j
public class QuickMlRagService {

    private final QuickMlRagProperties props;
    private final QuickMlTokenService tokenService;
    private final AgentDocumentRepository documentRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuickMlRagService(QuickMlRagProperties props, QuickMlTokenService tokenService,
                             AgentDocumentRepository documentRepository,
                             @Value("${agent.quickml.connect-timeout-ms:10000}") long connectTimeoutMs,
                             @Value("${agent.quickml.read-timeout-ms:120000}") long readTimeoutMs) {
        this.props = props;
        this.tokenService = tokenService;
        this.documentRepository = documentRepository;
        String baseUrl = props.getBaseUrl();
        // Bound connect + read so a hung RAG query can never stall a chat turn indefinitely.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = (baseUrl == null || baseUrl.isBlank())
                ? null
                : RestClient.builder()
                        .baseUrl(baseUrl)
                        .requestFactory(requestFactory)
                        .requestInterceptor((request, body, execution) -> {
                            if (tokenService.usesOAuthRefresh()) {
                                request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                                        "Zoho-oauthtoken " + tokenService.getAccessToken());
                                if (props.getCatalystOrg() != null && !props.getCatalystOrg().isBlank()) {
                                    request.getHeaders().set("CATALYST-ORG", props.getCatalystOrg());
                                }
                            } else if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
                                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey());
                            }
                            return execution.execute(request, body);
                        })
                        .build();
    }

    public boolean isConfigured() {
        return restClient != null;
    }

    /**
     * QuickML's knowledge base has no delete API — cleanup after an assistant/document delete must
     * be done manually in the Zoho console. Kept as a no-op so existing call sites don't need
     * special-casing.
     */
    public void purgeAssistant(String assistantId) {
        log.debug("QuickML RAG has no delete API; remove documents for assistant {} manually in "
                + "the Zoho console if needed", assistantId);
    }

    /**
     * Context-augmented reference answer for {@code query}, scoped to the assistant's linked Zoho
     * document ids. Returns {@code null} if RAG is unconfigured, the assistant has no linked
     * documents, or the call fails/errors — callers treat that as "no reference context available."
     */
    public String answer(String assistantId, String query) {
        if (!isConfigured() || query == null || query.isBlank()) {
            return null;
        }
        List<String> documentIds = documentRepository.findZohoDocumentIdsByAssistant(assistantId);
        if (documentIds.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> request = Map.of("query", query, "documents", documentIds);
            String path = props.getCompletionsPath() == null ? "" : props.getCompletionsPath();
            String responseBody = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            String status = response.path("status").asText("");
            if (!status.isBlank() && !"success".equalsIgnoreCase(status)) {
                log.warn("QuickML RAG query returned non-success status for assistant {}: {}", assistantId, status);
                return null;
            }
            String answer = response.path("response").asText(null);
            return answer == null || answer.isBlank() ? null : answer;
        } catch (Exception e) {
            log.warn("QuickML RAG query failed for assistant {}: {}", assistantId, e.getMessage());
            return null;
        }
    }

    /** Registers {@link QuickMlRagProperties} without needing a component scan annotation on it. */
    @Configuration
    @EnableConfigurationProperties(QuickMlRagProperties.class)
    static class RagPropertiesConfig {
    }
}
