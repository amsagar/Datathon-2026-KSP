package com.ksp.agent.document.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Catalyst QuickML RAG's query endpoint (confirmed via the console's "View API"
 * panel: {@code POST <base-url><completions-path>}, headers {@code Authorization: Zoho-oauthtoken
 * <token>} + {@code CATALYST-ORG}, body {@code {"query": ..., "documents": [id, ...]}}).
 *
 * <p>There is no programmatic upload API for the knowledge base — documents are added via the
 * Zoho console (Generative AI -&gt; Knowledge Base -&gt; Add Documents), and their resulting Zoho
 * document id is recorded on the {@code agent_document} row so {@link QuickMlRagService#answer}
 * knows which documents to search.
 */
@Data
@ConfigurationProperties(prefix = "agent.rag")
public class QuickMlRagProperties {

    /** Host root of the QuickML RAG API (e.g. {@code https://api.catalyst.zoho.in}). */
    private String baseUrl;

    /** Full path appended to {@code baseUrl}, e.g. {@code /quickml/v1/project/<id>/rag/answer}. */
    private String completionsPath;

    /** Catalyst org id, sent as the {@code CATALYST-ORG} header. */
    private String catalystOrg;

    /** Static bearer token fallback. Ignored when the shared Zoho OAuth refresh is configured. */
    private String apiKey;
}
