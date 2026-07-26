package com.ksp.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Custom {@link ChatModel} for Catalyst QuickML LLM Serving.
 *
 * <p>QuickML's REQUEST body is close to OpenAI's chat-completions schema (model/messages/tools/
 * tool_choice/max_tokens/temperature) — but its RESPONSE is a flat, non-OpenAI shape confirmed
 * empirically against the live endpoint:
 * <pre>{@code {"response": "...", "tool_calls": [...], "usage": {...}, "model": "...", "created_time": ...}}</pre>
 * with no {@code choices} wrapper. Spring AI's built-in {@code OpenAiChatModel} expects the real
 * OpenAI response shape and cannot parse this, so this class builds the request and parses the
 * response directly, and drives the tool-calling loop via Spring AI's {@link ToolCallingManager} —
 * the same mechanism the built-in provider ChatModels use — so {@code search_tools}/
 * {@code run_crime_sql}/etc. work exactly as they did against the old OpenAI-compatible client.
 * Auth is the Zoho-specific scheme ({@code Authorization: Zoho-oauthtoken <token>} +
 * {@code CATALYST-ORG} header), refreshed via {@link QuickMlTokenService}.
 *
 * <p>Streaming: by default (flag {@code agent.quickml.streaming=false}) {@code stream()} degrades to
 * a single {@code Flux} element wrapping {@link #call(Prompt)} — the chat SSE endpoint still works,
 * it just emits the whole answer as one chunk instead of token-by-token. When the flag is enabled,
 * {@code stream()} runs the same tool loop but requests {@code stream:true} for each model call and
 * emits a {@code ChatResponse} per OpenAI-style SSE content delta. If the endpoint turns out not to
 * support streaming (4xx, or a non-{@code text/event-stream} response), it logs one WARN and falls
 * back to the blocking path for the rest of the process lifetime.
 */
@Component
@Slf4j
public class QuickMlChatModel implements ChatModel {

    /** Guards against a runaway tool-calling loop (model keeps calling tools without ever answering). */
    private static final int MAX_TOOL_ITERATIONS = 8;

    /**
     * Cap on each tool RESULT re-serialized into the model's conversation history. Mirrors the UI's
     * 6000-char display truncation (ChatController.MAX_OUTPUT_CHARS) so a huge tool payload doesn't
     * multiply prompt tokens on every subsequent loop iteration.
     */
    private static final int MAX_TOOL_RESULT_CHARS = 6000;

    private static final String UNCONFIGURED_BASE_URL = "http://llm-not-configured.invalid";

    /**
     * Content prefixes that can open a tool call flattened into the text stream (the model
     * occasionally emits Qwen-style {@code <tool_call>{...}</tool_call>} blocks, raw
     * {@code {"tool_calls": ...}} JSON, or mimics the {@code [TOOL_CALL_LOG]} history marker).
     * While streaming, leading content is buffered (whitespace-insensitively) until one of these
     * is matched or ruled out, so tool-call JSON is never shown to the user as answer text.
     */
    private static final List<String> TOOL_CALL_MARKERS =
            List.of("<tool_call", "[TOOL_CALL", "{\"tool_call");

    private static final Pattern TOOL_CALL_LOG_PATTERN =
            Pattern.compile("\\[TOOL_CALL[A-Z_]*\\]\\s*(\\S+)\\s+args=(\\{.*)", Pattern.DOTALL);

    /** Log the streaming→blocking fallback WARN only once per process. */
    private static final AtomicBoolean STREAM_FALLBACK_WARNED = new AtomicBoolean(false);

    private final LlmProperties props;
    private final QuickMlTokenService tokenService;
    private final RestClient restClient;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** {@code agent.quickml.streaming}: opt-in true token streaming from the QuickML endpoint. */
    private final boolean streamingEnabled;
    private final long readTimeoutMs;
    private final String resolvedBaseUrl;
    /** Same JDK client the RestClient uses, reused for the raw SSE streaming request. */
    private final HttpClient streamingHttpClient;
    /**
     * Set on the first streaming attempt that the endpoint rejects (4xx / non-SSE response);
     * every later request then skips straight to the blocking path until restart.
     */
    private volatile boolean streamingUnsupported = false;

    public QuickMlChatModel(LlmProperties props, QuickMlTokenService tokenService,
                            @Value("${agent.quickml.connect-timeout-ms:10000}") long connectTimeoutMs,
                            @Value("${agent.quickml.read-timeout-ms:120000}") long readTimeoutMs,
                            @Value("${agent.quickml.streaming:false}") boolean streamingEnabled) {
        this.props = props;
        this.tokenService = tokenService;
        this.streamingEnabled = streamingEnabled;
        this.readTimeoutMs = readTimeoutMs;
        String baseUrl = props.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("agent.llm.base-url (LLM_BASE_URL) is not set — chat will fail until it is "
                    + "configured to the QuickML LLM Serving endpoint.");
            baseUrl = UNCONFIGURED_BASE_URL;
        }
        this.resolvedBaseUrl = baseUrl;
        boolean oauth = tokenService.usesOAuthRefresh();
        log.info("Configuring QuickML ChatModel: baseUrl={}, completionsPath={}, model={}, auth={}, "
                        + "connectTimeoutMs={}, readTimeoutMs={}, streaming={}",
                baseUrl, props.getCompletionsPath(), props.getModel(),
                oauth ? "Zoho OAuth refresh" : "static key", connectTimeoutMs, readTimeoutMs,
                streamingEnabled);
        // Without explicit timeouts a hung QuickML connection blocks the turn (and its SSE stream)
        // forever; bound both the connect and the response read.
        this.streamingHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(streamingHttpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
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

    @Override
    public ChatResponse call(Prompt prompt) {
        Prompt currentPrompt = prompt;
        // Accumulate token usage across ALL tool-loop iterations — each intermediate round-trip
        // burns real tokens, so the final response's metadata must reflect the whole turn (that is
        // what LlmUsageRecorder persists as the `main` row).
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            ChatResponse response = callOnce(currentPrompt);
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                promptTokens += usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
                completionTokens += usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
                totalTokens += usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
            }
            if (!response.hasToolCalls()) {
                return withAccumulatedUsage(response, promptTokens, completionTokens, totalTokens);
            }
            if (!(currentPrompt.getOptions() instanceof ToolCallingChatOptions toolOptions)
                    || Boolean.FALSE.equals(toolOptions.getInternalToolExecutionEnabled())) {
                // caller wants the raw tool calls back
                return withAccumulatedUsage(response, promptTokens, completionTokens, totalTokens);
            }
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(currentPrompt, response);
            if (toolExecutionResult.returnDirect()) {
                return ChatResponse.builder()
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .metadata(ChatResponseMetadata.builder()
                                .model(props.getModel())
                                .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                                .build())
                        .build();
            }
            currentPrompt = new Prompt(toolExecutionResult.conversationHistory(), currentPrompt.getOptions());
        }
        throw new IllegalStateException(
                "Exceeded " + MAX_TOOL_ITERATIONS + " tool-calling iterations calling QuickML");
    }

    /** Rebuild the response's metadata with the usage summed across every tool-loop iteration. */
    private ChatResponse withAccumulatedUsage(ChatResponse response,
                                              int promptTokens, int completionTokens, int totalTokens) {
        String model = response.getMetadata() != null ? response.getMetadata().getModel() : props.getModel();
        return ChatResponse.builder()
                .generations(response.getResults())
                .metadata(ChatResponseMetadata.builder()
                        .model(model == null || model.isBlank() ? props.getModel() : model)
                        .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                        .build())
                .build();
    }

    /**
     * With {@code agent.quickml.streaming=false} (the default) — or after the endpoint has proven it
     * does not support streaming — this wraps the single blocking {@link #call(Prompt)} response in
     * a one-element {@code Flux}, which is what the SSE chat endpoint needs: it emits the whole
     * answer as one chunk instead of token-by-token. {@link ChatModel}'s own default {@code stream()}
     * throws {@code UnsupportedOperationException} rather than degrading, so this override is
     * required either way.
     *
     * <p>With the flag ON, the same tool loop as {@link #call(Prompt)} runs, but each model call is
     * made with {@code stream:true} and OpenAI-style SSE deltas are emitted live into the returned
     * {@code Flux} (tool-call rounds are buffered, executed, and never shown). The LAST element of
     * the Flux always carries the usage accumulated across every loop round, which is what
     * ChatController records.
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        if (!streamingEnabled || streamingUnsupported) {
            return Flux.just(call(prompt));
        }
        return Flux.<ChatResponse>create(sink -> {
            try {
                streamTurn(prompt, sink);
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Streaming twin of {@link #call(Prompt)}: the identical tool loop, but every model call is a
     * streaming request. Content deltas of the final answer round are emitted live; a round that
     * turns out to be a tool call is buffered (never emitted), its tools executed, and the loop
     * continues. Ends with a terminal empty-text {@code ChatResponse} carrying the accumulated
     * usage. If the endpoint rejects streaming (4xx / non-SSE response), degrades transparently to
     * the blocking path for the rest of THIS turn (from the current conversation state — already
     * executed tools are not re-run) and flips {@link #streamingUnsupported} for later requests.
     */
    private void streamTurn(Prompt prompt, FluxSink<ChatResponse> sink) {
        Prompt currentPrompt = prompt;
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            StreamedRound round;
            try {
                round = streamOnce(currentPrompt, sink);
            } catch (StreamingUnsupportedException e) {
                markStreamingUnsupported(e.getMessage());
                ChatResponse blocking = call(currentPrompt);
                int bp = 0;
                int bc = 0;
                int bt = 0;
                if (blocking.getMetadata() != null && blocking.getMetadata().getUsage() != null) {
                    var u = blocking.getMetadata().getUsage();
                    bp = u.getPromptTokens() == null ? 0 : u.getPromptTokens();
                    bc = u.getCompletionTokens() == null ? 0 : u.getCompletionTokens();
                    bt = u.getTotalTokens() == null ? 0 : u.getTotalTokens();
                }
                sink.next(withAccumulatedUsage(blocking,
                        promptTokens + bp, completionTokens + bc, totalTokens + bt));
                return;
            }
            if (round == null) {
                return; // subscriber cancelled mid-stream
            }
            promptTokens += round.promptTokens();
            completionTokens += round.completionTokens();
            totalTokens += round.totalTokens();
            ChatResponse response = round.response();
            if (!response.hasToolCalls()) {
                // The text was already emitted as deltas; close with an empty-text element whose
                // metadata carries the whole turn's usage (ChatController records the last usage).
                sink.next(terminalResponse(response, promptTokens, completionTokens, totalTokens));
                return;
            }
            if (!(currentPrompt.getOptions() instanceof ToolCallingChatOptions toolOptions)
                    || Boolean.FALSE.equals(toolOptions.getInternalToolExecutionEnabled())) {
                // caller wants the raw tool calls back
                sink.next(withAccumulatedUsage(response, promptTokens, completionTokens, totalTokens));
                return;
            }
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(currentPrompt, response);
            if (toolExecutionResult.returnDirect()) {
                sink.next(ChatResponse.builder()
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .metadata(ChatResponseMetadata.builder()
                                .model(props.getModel())
                                .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                                .build())
                        .build());
                return;
            }
            currentPrompt = new Prompt(toolExecutionResult.conversationHistory(), currentPrompt.getOptions());
        }
        throw new IllegalStateException(
                "Exceeded " + MAX_TOOL_ITERATIONS + " tool-calling iterations calling QuickML (streaming)");
    }

    /**
     * One streaming model call: POSTs the request with {@code stream:true} +
     * {@code stream_options.include_usage:true}, consumes the OpenAI-style SSE body
     * ({@code data: {json}} lines terminated by {@code data: [DONE]}) via the JDK HttpClient's line
     * stream, and pushes content deltas into {@code sink} (subject to tool-call sniffing — see
     * {@link StreamingRound}). Returns the round's outcome, or {@code null} if the subscriber
     * cancelled. Throws {@link StreamingUnsupportedException} when the endpoint answers 4xx or with
     * a non-{@code text/event-stream} content type.
     */
    private StreamedRound streamOnce(Prompt prompt, FluxSink<ChatResponse> sink) {
        ObjectNode request = buildRequest(prompt);
        request.put("stream", true);
        // OpenAI-style streams only report usage in the final chunk when explicitly asked.
        request.putObject("stream_options").put("include_usage", true);
        byte[] requestBytes;
        try {
            requestBytes = objectMapper.writeValueAsBytes(request);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize QuickML streaming request", e);
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(streamingUrl()))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes));
        applyAuthHeaders(requestBuilder);
        HttpResponse<Stream<String>> response;
        try {
            response = streamingHttpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new IllegalStateException("QuickML streaming call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QuickML streaming call interrupted", e);
        }
        int status = response.statusCode();
        String contentType = response.headers()
                .firstValue(HttpHeaders.CONTENT_TYPE).orElse("");
        if (status >= 400 && status < 500) {
            closeQuietly(response);
            throw new StreamingUnsupportedException("HTTP " + status);
        }
        if (status >= 500) {
            closeQuietly(response);
            throw new IllegalStateException("QuickML streaming call failed: HTTP " + status);
        }
        if (!contentType.toLowerCase().contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            closeQuietly(response);
            throw new StreamingUnsupportedException(
                    "content-type '" + contentType + "' is not text/event-stream");
        }
        StreamingRound round = new StreamingRound(sink);
        try (Stream<String> lines = response.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                if (sink.isCancelled()) {
                    return null;
                }
                String line = it.next();
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode chunk;
                try {
                    chunk = objectMapper.readTree(payload);
                } catch (Exception e) {
                    log.warn("Skipping unparseable QuickML stream chunk: {}", payload);
                    continue;
                }
                round.accept(chunk);
            }
        }
        return round.finish(promptCharEstimate(prompt));
    }

    /** Mirrors the RestClient's auth interceptor for the raw JDK streaming request. */
    private void applyAuthHeaders(HttpRequest.Builder builder) {
        if (tokenService.usesOAuthRefresh()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Zoho-oauthtoken " + tokenService.getAccessToken());
            if (props.getCatalystOrg() != null && !props.getCatalystOrg().isBlank()) {
                builder.header("CATALYST-ORG", props.getCatalystOrg());
            }
        } else if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey());
        }
    }

    private String streamingUrl() {
        String path = props.getCompletionsPath() == null ? "" : props.getCompletionsPath();
        if (path.isEmpty()) {
            return resolvedBaseUrl;
        }
        boolean baseSlash = resolvedBaseUrl.endsWith("/");
        boolean pathSlash = path.startsWith("/");
        if (baseSlash && pathSlash) {
            return resolvedBaseUrl + path.substring(1);
        }
        if (!baseSlash && !pathSlash) {
            return resolvedBaseUrl + "/" + path;
        }
        return resolvedBaseUrl + path;
    }

    private static void closeQuietly(HttpResponse<Stream<String>> response) {
        try {
            response.body().close();
        } catch (RuntimeException ignored) {
            // best-effort: releasing the connection of an already-failed exchange
        }
    }

    private void markStreamingUnsupported(String reason) {
        streamingUnsupported = true;
        if (STREAM_FALLBACK_WARNED.compareAndSet(false, true)) {
            log.warn("QuickML token streaming is unsupported by the endpoint ({}); falling back to "
                    + "blocking calls for all subsequent requests until restart", reason);
        }
    }

    /** Rough chars/4 token estimate of the request side, used when the stream omits usage. */
    private static int promptCharEstimate(Prompt prompt) {
        int chars = 0;
        for (Message message : prompt.getInstructions()) {
            String text = message.getText();
            chars += text == null ? 0 : text.length();
        }
        return chars;
    }

    /** Empty-text closer carrying the turn's accumulated usage (recorded off the LAST element). */
    private ChatResponse terminalResponse(ChatResponse lastRound,
                                          int promptTokens, int completionTokens, int totalTokens) {
        String model = lastRound.getMetadata() != null ? lastRound.getMetadata().getModel() : null;
        AssistantMessage empty = AssistantMessage.builder().content("").build();
        Generation generation = new Generation(empty,
                ChatGenerationMetadata.builder().finishReason("stop").build());
        return ChatResponse.builder()
                .generations(List.of(generation))
                .metadata(ChatResponseMetadata.builder()
                        .model(model == null || model.isBlank() ? props.getModel() : model)
                        .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                        .build())
                .build();
    }

    /** Outcome of one streamed model call: the round's response (for the tool loop) + its usage. */
    private record StreamedRound(ChatResponse response,
                                 int promptTokens, int completionTokens, int totalTokens) {
    }

    /** Streaming attempt rejected by the endpoint in a way that means "use the blocking path". */
    private static final class StreamingUnsupportedException extends RuntimeException {
        StreamingUnsupportedException(String message) {
            super(message);
        }
    }

    /**
     * Accumulates one streamed round. Content deltas are emitted live into the sink EXCEPT while
     * the round might still be a tool call: leading content is buffered until the (whitespace-
     * stripped) prefix either matches one of {@link #TOOL_CALL_MARKERS} (→ buffer the whole round,
     * emit nothing) or can no longer match any marker (→ flush and stream through). Structured
     * {@code choices[0].delta.tool_calls} fragments always make the round a silent tool round.
     */
    private final class StreamingRound {

        private final FluxSink<ChatResponse> sink;
        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder full = new StringBuilder();
        private boolean passthrough = false;
        private boolean contentToolCallSuspected = false;
        private final Map<Integer, PartialToolCall> structuredToolCalls = new TreeMap<>();
        private Integer usagePromptTokens;
        private Integer usageCompletionTokens;
        private Integer usageTotalTokens;
        private String model;

        private StreamingRound(FluxSink<ChatResponse> sink) {
            this.sink = sink;
        }

        void accept(JsonNode chunk) {
            JsonNode usage = chunk.path("usage");
            if (usage.isObject()) {
                usagePromptTokens = intOrNull(usage, "prompt_tokens");
                usageCompletionTokens = intOrNull(usage, "completion_tokens");
                usageTotalTokens = intOrNull(usage, "total_tokens");
            }
            if (chunk.hasNonNull("model")) {
                model = chunk.get("model").asText();
            }
            JsonNode delta = chunk.path("choices").path(0).path("delta");
            JsonNode deltaToolCalls = delta.path("tool_calls");
            if (deltaToolCalls.isArray() && !deltaToolCalls.isEmpty()) {
                for (JsonNode tc : deltaToolCalls) {
                    int index = tc.path("index").asInt(structuredToolCalls.size());
                    PartialToolCall partial =
                            structuredToolCalls.computeIfAbsent(index, k -> new PartialToolCall());
                    String id = tc.path("id").asText("");
                    if (!id.isEmpty()) {
                        partial.id = id;
                    }
                    JsonNode fn = tc.path("function");
                    String name = fn.path("name").asText("");
                    if (!name.isEmpty()) {
                        partial.name = name;
                    }
                    if (fn.hasNonNull("arguments")) {
                        partial.arguments.append(fn.get("arguments").asText());
                    }
                }
            }
            String content = "";
            if (delta.hasNonNull("content")) {
                content = delta.get("content").asText("");
            } else if (!chunk.has("choices") && chunk.hasNonNull("response")) {
                // tolerate QuickML's flat {"response": "..."} shape in stream chunks too
                content = chunk.get("response").asText("");
            }
            if (content.isEmpty()) {
                return;
            }
            full.append(content);
            if (!structuredToolCalls.isEmpty() || contentToolCallSuspected) {
                return; // silent tool round — never show its content
            }
            if (passthrough) {
                emitDelta(content);
                return;
            }
            pending.append(content);
            String probe = stripWhitespace(pending);
            for (String marker : TOOL_CALL_MARKERS) {
                if (probe.startsWith(marker)) {
                    contentToolCallSuspected = true;
                    return;
                }
            }
            boolean couldStillMatch = false;
            for (String marker : TOOL_CALL_MARKERS) {
                if (marker.startsWith(probe)) {
                    couldStillMatch = true;
                    break;
                }
            }
            if (couldStillMatch) {
                return; // too early to tell — keep buffering (markers are < 64 chars)
            }
            passthrough = true;
            emitDelta(pending.toString());
            pending.setLength(0);
        }

        StreamedRound finish(int promptCharsEstimate) {
            List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            if (!structuredToolCalls.isEmpty()) {
                for (PartialToolCall partial : structuredToolCalls.values()) {
                    toolCalls.add(new AssistantMessage.ToolCall(
                            partial.id != null ? partial.id : "call_" + UUID.randomUUID(),
                            "function",
                            partial.name == null ? "" : partial.name,
                            partial.arguments.isEmpty() ? "{}" : partial.arguments.toString()));
                }
            } else if (contentToolCallSuspected) {
                toolCalls = parseToolCallsFromText(full.toString());
            }
            String text = full.toString();
            if (toolCalls.isEmpty() && !passthrough && !text.isEmpty()) {
                // Short answer that never left the sniff buffer, or a false-positive marker that
                // did not parse into tool calls — nothing was emitted yet, so emit it all now.
                emitDelta(text);
            }
            AssistantMessage assistantMessage = AssistantMessage.builder()
                    // tool-call rounds keep structured content out of the assistant text
                    .content(toolCalls.isEmpty() ? text : (contentToolCallSuspected ? "" : text))
                    .toolCalls(toolCalls)
                    .build();
            Generation generation = new Generation(assistantMessage, ChatGenerationMetadata.builder()
                    .finishReason(toolCalls.isEmpty() ? "stop" : "tool_calls")
                    .build());
            int p;
            int c;
            int t;
            if (usagePromptTokens != null || usageCompletionTokens != null || usageTotalTokens != null) {
                p = usagePromptTokens == null ? 0 : usagePromptTokens;
                c = usageCompletionTokens == null ? 0 : usageCompletionTokens;
                t = usageTotalTokens == null ? p + c : usageTotalTokens;
            } else {
                // QuickML omitted usage on the stream — estimate (chars/4) so the usage recorder
                // still gets a meaningful row.
                int completionChars = full.length();
                for (AssistantMessage.ToolCall tc : toolCalls) {
                    completionChars += tc.arguments() == null ? 0 : tc.arguments().length();
                }
                p = Math.max(1, promptCharsEstimate / 4);
                c = Math.max(1, completionChars / 4);
                t = p + c;
            }
            ChatResponse response = ChatResponse.builder()
                    .generations(List.of(generation))
                    .metadata(ChatResponseMetadata.builder()
                            .model(model == null || model.isBlank() ? props.getModel() : model)
                            .usage(new DefaultUsage(p, c, t))
                            .build())
                    .build();
            return new StreamedRound(response, p, c, t);
        }

        private void emitDelta(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            AssistantMessage message = AssistantMessage.builder().content(text).build();
            Generation generation = new Generation(message, ChatGenerationMetadata.builder().build());
            sink.next(ChatResponse.builder()
                    .generations(List.of(generation))
                    .metadata(ChatResponseMetadata.builder()
                            .model(model == null || model.isBlank() ? props.getModel() : model)
                            .build())
                    .build());
        }
    }

    private static final class PartialToolCall {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    /** Whitespace-insensitive view of the sniff buffer so {@code { "tool_calls"} still matches. */
    private static String stripWhitespace(CharSequence s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!Character.isWhitespace(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Best-effort extraction of tool calls flattened into streamed TEXT (the round was flagged by
     * the marker sniff). Understands (a) a raw JSON object with a {@code tool_calls} array (same
     * shape as the blocking response), (b) Qwen-style {@code <tool_call>{"name":…,"arguments":…}
     * </tool_call>} blocks, and (c) a mimicked {@code [TOOL_CALL_LOG] name args={…}} history
     * marker. Returns an empty list when nothing parses — the caller then treats the round as
     * ordinary text so no content is lost.
     */
    private List<AssistantMessage.ToolCall> parseToolCallsFromText(String text) {
        String trimmed = text.strip();
        try {
            if (trimmed.startsWith("{")) {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.path("tool_calls").isArray()) {
                    return toolCallsFrom(node.path("tool_calls"));
                }
                if (node.hasNonNull("name")) {
                    return List.of(contentToolCall(node));
                }
                return List.of();
            }
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            int from = 0;
            while (true) {
                int start = text.indexOf("<tool_call>", from);
                if (start < 0) {
                    break;
                }
                int end = text.indexOf("</tool_call>", start);
                if (end < 0) {
                    break;
                }
                String json = text.substring(start + "<tool_call>".length(), end).strip();
                JsonNode node = objectMapper.readTree(json);
                if (node.hasNonNull("name")) {
                    calls.add(contentToolCall(node));
                }
                from = end + "</tool_call>".length();
            }
            if (!calls.isEmpty()) {
                return calls;
            }
            Matcher matcher = TOOL_CALL_LOG_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                String args = matcher.group(2).strip();
                objectMapper.readTree(args); // must be valid JSON to count as a tool call
                return List.of(new AssistantMessage.ToolCall(
                        "call_" + UUID.randomUUID(), "function", matcher.group(1), args));
            }
        } catch (Exception e) {
            log.debug("Streamed content looked like a tool call but did not parse ({}); "
                    + "treating it as plain text", e.getMessage());
        }
        return List.of();
    }

    /** {@code {"name": "...", "arguments": {...}}} → ToolCall (arguments re-serialized as JSON). */
    private AssistantMessage.ToolCall contentToolCall(JsonNode node) {
        JsonNode arguments = node.path("arguments");
        String args;
        if (arguments.isMissingNode() || arguments.isNull()) {
            args = "{}";
        } else if (arguments.isTextual()) {
            args = arguments.asText();
        } else {
            args = arguments.toString();
        }
        return new AssistantMessage.ToolCall(
                "call_" + UUID.randomUUID(), "function", node.get("name").asText(), args);
    }

    private ChatResponse callOnce(Prompt prompt) {
        ObjectNode request = buildRequest(prompt);
        log.debug("QuickML request body: {}", request);
        byte[] requestBytes;
        try {
            requestBytes = objectMapper.writeValueAsBytes(request);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize QuickML request", e);
        }
        String path = props.getCompletionsPath() == null ? "" : props.getCompletionsPath();
        String responseBody = postWithRetry(path, requestBytes);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("QuickML returned unparseable JSON: " + responseBody, e);
        }
        return parseResponse(response);
    }

    /**
     * One retry, only for transient failures (connection errors / 5xx) — a request that reached the
     * model and failed for any other reason is surfaced immediately.
     */
    private String postWithRetry(String path, byte[] requestBytes) {
        try {
            return postOnce(path, requestBytes);
        } catch (RuntimeException e) {
            if (!isRetryable(e)) {
                throw e;
            }
            log.warn("QuickML call failed transiently ({}); retrying once", e.getMessage());
            return postOnce(path, requestBytes);
        }
    }

    private String postOnce(String path, byte[] requestBytes) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBytes)
                .retrieve()
                .body(String.class);
    }

    private static boolean isRetryable(RuntimeException e) {
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        if (e instanceof ResourceAccessException) {
            Throwable cause = e.getCause();
            return cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException;
        }
        return false;
    }

    private ObjectNode buildRequest(Prompt prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", props.getModel());

        ChatOptions options = prompt.getOptions();
        Integer maxTokens = options != null && options.getMaxTokens() != null
                ? options.getMaxTokens() : props.getMaxTokens();
        Double temperature = options != null && options.getTemperature() != null
                ? options.getTemperature() : props.getTemperature();
        if (maxTokens != null) {
            root.put("max_tokens", maxTokens);
        }
        if (temperature != null) {
            root.put("temperature", temperature);
        }
        root.put("stream", false);
        // Suppresses the model's raw chain-of-thought from the "response" text (confirmed
        // empirically: enable_thinking=true dumps reasoning steps into the answer shown to users).
        root.putObject("chat_template_kwargs").put("enable_thinking", false);

        ArrayNode messages = root.putArray("messages");
        for (Message message : prompt.getInstructions()) {
            appendMessage(messages, message);
        }

        List<ToolDefinition> toolDefinitions = resolveToolDefinitions(options);
        if (!toolDefinitions.isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition td : toolDefinitions) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", td.name());
                function.put("description", td.description() == null ? "" : td.description());
                try {
                    function.set("parameters", objectMapper.readTree(td.inputSchema()));
                } catch (Exception e) {
                    function.putObject("parameters").put("type", "object");
                }
            }
            root.put("tool_choice", "auto");
        }
        return root;
    }

    private List<ToolDefinition> resolveToolDefinitions(ChatOptions options) {
        if (options instanceof ToolCallingChatOptions toolOptions
                && toolOptions.getToolCallbacks() != null && !toolOptions.getToolCallbacks().isEmpty()) {
            return toolCallingManager.resolveToolDefinitions(toolOptions);
        }
        return List.of();
    }

    /**
     * QuickML's endpoint rejects ANY message carrying a {@code tool_calls} field once it appears in
     * conversation history — confirmed empirically: a bare assistant message with {@code tool_calls}
     * and no follow-up at all still triggers {@code EXTRA_KEY_FOUND_IN_JSON}, while the identical
     * history with that field stripped succeeds. So the model CAN return a single tool call in a
     * fresh response, but the standard multi-turn protocol of feeding that call + its result back as
     * structured {@code tool_calls}/{@code role:"tool"} history is not supported by this endpoint.
     *
     * <p>Workaround: flatten tool calls/results into plain {@code assistant}/{@code user} text when
     * re-serializing history for the next call. Spring AI's {@link ToolCallingManager} still executes
     * tools and drives the loop entirely in-process — only the wire representation changes, so
     * run_crime_sql/etc. still get a grounded follow-up answer, just without QuickML seeing the
     * rejected structured fields.
     */
    private void appendMessage(ArrayNode messages, Message message) {
        switch (message.getMessageType()) {
            case SYSTEM -> messages.addObject().put("role", "system").put("content", nullToEmpty(message.getText()));
            case USER -> messages.addObject().put("role", "user").put("content", nullToEmpty(message.getText()));
            case ASSISTANT -> {
                // Keep the assistant's own prose (if any) as a genuine assistant turn, but log tool
                // calls as a SEPARATE "system" message in a clearly tagged, non-conversational
                // format — NOT as assistant-role prose. Putting "(Calling tool X)"-style narration in
                // the assistant's own voice caused it to pattern-match its prior turns and mimic that
                // exact phrasing as its next plain-text response instead of emitting a real tool
                // call (confirmed empirically). A system-role log line doesn't invite imitation.
                AssistantMessage am = (AssistantMessage) message;
                String text = nullToEmpty(am.getText());
                if (!text.isBlank()) {
                    messages.addObject().put("role", "assistant").put("content", text);
                }
                if (am.hasToolCalls()) {
                    StringBuilder log = new StringBuilder();
                    for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                        if (!log.isEmpty()) {
                            log.append('\n');
                        }
                        log.append("[TOOL_CALL_LOG] ").append(tc.name())
                                .append(" args=").append(tc.arguments());
                    }
                    messages.addObject().put("role", "system").put("content", log.toString());
                }
            }
            case TOOL -> {
                ToolResponseMessage tm = (ToolResponseMessage) message;
                StringBuilder content = new StringBuilder();
                for (ToolResponseMessage.ToolResponse tr : tm.getResponses()) {
                    if (!content.isEmpty()) {
                        content.append('\n');
                    }
                    content.append("[TOOL_RESULT_LOG] ").append(tr.name()).append(" result=")
                            .append(truncateToolResult(tr.responseData()));
                }
                messages.addObject().put("role", "user").put("content", content.toString());
            }
            default -> log.warn("Unhandled message type {} sent to QuickML", message.getMessageType());
        }
    }

    private ChatResponse parseResponse(JsonNode node) {
        if (node == null) {
            throw new IllegalStateException("QuickML returned an empty response body");
        }
        String text = node.path("response").asText("");
        List<AssistantMessage.ToolCall> toolCalls = toolCallsFrom(node.path("tool_calls"));
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(text)
                .toolCalls(toolCalls)
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(toolCalls.isEmpty() ? "stop" : "tool_calls")
                .build();
        Generation generation = new Generation(assistantMessage, generationMetadata);

        JsonNode usageNode = node.path("usage");
        DefaultUsage usage = new DefaultUsage(
                intOrNull(usageNode, "prompt_tokens"),
                intOrNull(usageNode, "completion_tokens"),
                intOrNull(usageNode, "total_tokens"));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(node.path("model").asText(props.getModel()))
                .usage(usage)
                .build();
        return ChatResponse.builder().generations(List.of(generation)).metadata(metadata).build();
    }

    /** OpenAI/QuickML {@code tool_calls} array → Spring AI tool calls (shared by both paths). */
    private static List<AssistantMessage.ToolCall> toolCallsFrom(JsonNode toolCallsNode) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tc : toolCallsNode) {
            JsonNode fn = tc.path("function");
            toolCalls.add(new AssistantMessage.ToolCall(
                    tc.path("id").asText(""),
                    tc.path("type").asText("function"),
                    fn.path("name").asText(""),
                    fn.path("arguments").asText("{}")));
        }
        return toolCalls;
    }

    private static Integer intOrNull(JsonNode usageNode, String field) {
        JsonNode value = usageNode.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Same cap the UI applies to displayed tool output (6000 chars) — applied to what re-enters the
     * model's context so one oversized tool payload doesn't inflate every later loop iteration.
     */
    private static String truncateToolResult(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_TOOL_RESULT_CHARS) {
            return s;
        }
        return s.substring(0, MAX_TOOL_RESULT_CHARS) + "\n… [truncated]";
    }
}
