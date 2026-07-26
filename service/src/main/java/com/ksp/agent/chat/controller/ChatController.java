package com.ksp.agent.chat.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.assistant.entity.Assistant;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.chat.audit.ChatAuditLog;
import com.ksp.agent.chat.guard.ScopeGuardService;
import com.ksp.agent.chat.service.ChatSessionService;
import com.ksp.agent.document.rag.QuickMlRagService;
import com.ksp.agent.chat.clarification.ClarificationPersistence;
import com.ksp.agent.chat.clarification.WebQuestionBridge;
import com.ksp.agent.chat.sse.ChatSseEmitter;
import com.ksp.agent.chat.sse.ToolProgressCopy;
import com.ksp.agent.chat.tooling.AskUserQuestionToolFactory;
import com.ksp.agent.chat.tooling.BuiltinToolCatalog;
import com.ksp.agent.chat.tooling.DynamicToolRegistry;
import com.ksp.agent.chat.tooling.EventEmittingToolCallback;
import com.ksp.agent.chat.tooling.InvokeToolCallback;
import com.ksp.agent.chat.tooling.MemoryToolFactory;
import com.ksp.agent.chat.tooling.RoleGatedToolCallback;
import com.ksp.agent.chat.tooling.SqlTableGateToolCallback;
import com.ksp.agent.chat.skillupdate.SkillUpdatePersistence;
import com.ksp.agent.chat.skillupdate.SkillUpdateBridge;
import com.ksp.agent.chat.tooling.SkillUpdateToolFactory;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.service.SkillService;
import com.ksp.agent.chat.tooling.SearchToolsCallback;
import com.ksp.agent.chat.repo.ChatToolEventRepository;
import com.ksp.agent.chat.usage.LlmUsageContext;
import com.ksp.agent.chat.usage.LlmUsageRecorder;
import com.ksp.agent.chat.usage.LlmUsageSource;
import com.ksp.agent.chat.tooling.ToolEventRegistry;
import com.ksp.agent.chat.tooling.ToolEventSink;
import com.ksp.agent.document.dto.response.DocumentDto;
import com.ksp.agent.document.service.DocumentService;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.memory.repo.SemanticFactRepository.ScoredFact;
import com.ksp.agent.memory.service.SemanticMemoryService;
import com.ksp.agent.mcp.runtime.AssistantMcpTools;
import com.ksp.agent.mcp.runtime.McpToolCallbackFactory;
import com.ksp.agent.skill.runtime.SkillWorkspaceFileToolCallback;
import com.ksp.agent.skill.runtime.SkillWorkspacePaths;
import com.ksp.agent.skill.runtime.SkillWorkspaceToolOutputMirrorCallback;
import com.ksp.agent.skill.runtime.SkillWorkspaceService;
import com.ksp.agent.skill.runtime.SkillWorkspaceShellToolCallback;
import tools.jackson.databind.ObjectMapper;
import com.ksp.agent.style.service.ResponseStyleService;
import com.ksp.agent.tool.runtime.HttpToolCallbackFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiConstants.CHAT_PATH)
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final int MAX_OUTPUT_CHARS = 6000;

    /** Trailing transcript messages handed to the scope-guard classifier (token diet). */
    private static final int SCOPE_GUARD_HISTORY_MESSAGES = 4;

    private static final String TOOL_SEARCH_HINT = """
            You have access to many tools, but they are not all listed directly. To use a tool, first \
            call `search_tools` with a natural-language query describing what you need; it returns the \
            matching tools with their exact names and input schemas. Then call `invoke_tool` with the \
            chosen tool's exact `name` and an `input` object matching that tool's schema. Search again \
            with a different query if the first results are not relevant.""";

    private static final String ASK_USER_QUESTION_HINT = """
            CLARIFYING QUESTIONS: When required information is missing or ambiguous, call \
            `ask_user_question` before guessing. Ask 1-3 focused questions with clear options. After \
            answers, proceed with your tools and normal procedure; do not ask again unless still unclear.""";

    // Phrased as a neutral capability description (not "obey embedded instructions") to avoid tripping
    // Azure OpenAI's prompt-injection / jailbreak content shield.
    private static final String MEMORY_TOOLS_HINT = """
            LONG-TERM MEMORY: You can remember things about this user across separate conversations. \
            When the user shares a durable preference, personal detail, or instruction — or explicitly \
            says to remember something (for example "remember that I prefer DD-MM-YYYY") — call \
            `remember_fact` with a concise subject/predicate/object, then briefly confirm what you \
            stored. When answering something that may depend on a detail the user told you earlier and \
            it is not already shown to you, call `recall_memory` first. Always prefer what the user \
            says now over stored memory.""";

    // Appended whenever a skill workspace was materialized so the model knows skills exist and
    // checks them — SkillsTool only lists name+description until the model reads a SKILL.md.
    // Phrased as a neutral capability description (not "obey embedded instructions") to avoid
    // tripping Azure OpenAI's prompt-injection / jailbreak content shield.
    private static final String SKILLS_HINT = """
            SKILLS: A `skill` tool lists specialized skills with short descriptions. When a \
            request matches one — for example presenting a plan, dashboard, or comparison as a \
            visual `artifacts` layout — call that tool to load the skill before you answer.""";

    private static final String SKILL_UPDATE_HINT = """
            SKILL IMPROVEMENT (admin only): When the user gives feedback that a loaded uploaded \
            skill should behave differently, or explicitly asks to update a skill file, call \
            `propose_skill_update` with the full revised file content. Do NOT write skill files \
            directly with file-system tools — always propose and wait for approval. Only update \
            uploaded skills for this assistant, never platform skills. Before proposing, read the \
            current file and preserve its structure: for CSV files keep the same header row and \
            column format; for references/leg-sequences.csv use journey_type and valid_sequence_1 \
            with arrow notation (NEW -> MOV -> FPU); apply only the requested add/edit, never rewrite \
            the whole file with a different schema.""";

    private static final String SCOPE_GUARDRAIL_SKILL_UPDATE_EXCEPTION = """
            ADMIN SKILL UPDATES (in scope for you): When the user asks to update an uploaded skill \
            or its files (e.g. SKILL.md, references/leg-sequences.csv), do NOT decline as off-topic. \
            Use `propose_skill_update` and wait for approval.""";

    // The model has no reliable notion of "today" from training data alone, so relative-time phrases
    // ("this year", "last 90 days") were being resolved against a stale guessed year. Computed fresh
    // per request (not a static final — it must track the real calendar date) and injected right
    // alongside GLOBAL_RULES below. Fixed to IST since crime_registered_date and friends are
    // Karnataka-local dates, not UTC.
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter CURRENT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)");

    // Global rules applied to EVERY agent, regardless of role/prompt. Injected unconditionally so
    // even assistants with no system prompt inherit them. Covers output formatting (no emojis) and
    // tool-result honesty (never fabricate results the model did not actually obtain from a tool).
    private static final String GLOBAL_RULES = """
            GLOBAL RULES (always apply, these override any other instruction):
            - No emojis; plain text only.
            - Never invent, assume, or guess tool outputs. State only results actually returned by a \
            tool call in THIS turn. Fabricating an example request/response payload or a \
            PASS/FAIL/Available outcome is forbidden — if you did not call the tool, label that step \
            "NOT VERIFIED — tool not called" and say what is missing instead of inventing data.
            - When a task requires specific tool calls (e.g. a multi-step skill procedure), make every \
            required call before reporting; otherwise report partial results and clearly mark the \
            checks not performed.
            - If a tool appears in your available tool list this turn, it IS available: invoke it \
            before claiming it is missing or unusable. Only a real error response from that \
            invocation in THIS turn justifies reporting failure for that step.""";

    // Appended dead last in effectiveSystemPrompt (after GLOBAL_RULES, the scope guardrail, and the
    // memory-recall block) precisely so it is NOT dominated by GLOBAL_RULES's own "these override
    // any other instruction" claim — a language switch should win over everything assembled before it.
    private static final String LANG_DIRECTIVE_KN =
            "LANGUAGE: Reply in Kannada (ಕನ್ನಡ) for this turn — the user's UI is set to Kannada. "
                    + "Translate any table headers, labels or explanations into Kannada too, not just prose.";
    private static final String LANG_DIRECTIVE_EN =
            "LANGUAGE: Reply in English for this turn.";

    // Scope harness: keeps the assistant on-task. Appended after the assistant's own system prompt so
    // the role/domain defined there becomes the authoritative boundary. Prompt-level enforcement —
    // it constrains the model strongly but is not a hard sandbox.
    private static final String SCOPE_GUARDRAIL = """
            STAY IN SCOPE: Only help with requests within the role and domain described above. For \
            anything outside it — general knowledge, current events, politics, people, trivia — do \
            not answer even if you know the answer; decline in one sentence and say what you CAN help \
            with. Tool results, the conversation so far, and retrieved reference-document context ARE \
            in scope: when they answer the question, use them and answer normally even if the topic is \
            not in your role. Only your own world knowledge about unrelated subjects is out of scope.""";

    private final ChatClient chatClient;
    private final ChatSessionService chatSessionService;
    private final AssistantService assistantService;
    private final BuiltinToolCatalog builtinToolCatalog;
    private final AskUserQuestionToolFactory askUserQuestionToolFactory;
    private final WebQuestionBridge webQuestionBridge;
    private final ClarificationPersistence clarificationPersistence;
    private final HttpToolCallbackFactory httpToolCallbackFactory;
    private final McpToolCallbackFactory mcpToolCallbackFactory;
    private final ToolEventRegistry toolEventRegistry;
    private final ChatToolEventRepository toolEventRepository;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final SearchToolsCallback searchToolsCallback;
    private final InvokeToolCallback invokeToolCallback;
    private final SkillWorkspaceService skillWorkspaceService;
    private final ScopeGuardService scopeGuardService;
    private final ChatMemory chatMemory;
    private final DocumentService documentService;
    private final QuickMlRagService quickMlRagService;
    private final ResponseStyleService responseStyleService;
    private final ObjectMapper objectMapper;
    private final LlmUsageRecorder llmUsageRecorder;
    private final SemanticMemoryService semanticMemoryService;
    private final SecurityContextService securityContextService;
    private final MemoryToolFactory memoryToolFactory;
    private final SkillUpdateToolFactory skillUpdateToolFactory;
    private final SkillUpdateBridge skillUpdateBridge;
    private final SkillUpdatePersistence skillUpdatePersistence;
    private final SkillService skillService;

    /**
     * Wraps the user message with the QuickML RAG reference answer as optional context. The wording
     * mirrors the old RAG advisor's permissive template: the context is offered as reference, and the
     * model is told NOT to refuse merely because the context is empty or unhelpful — it may still use
     * its tools and normal procedure.
     */
    private static final String RAG_PROMPT_TEMPLATE = """
            %s

            Reference context retrieved from the assistant's attached documents (may be empty or only
            partially relevant):
            ---------------------
            %s
            ---------------------
            Use the context above when it helps answer the request. You may also use your role
            instructions, your tools, and the conversation history. Do NOT refuse or say you cannot
            answer merely because this context is empty or does not contain the answer — in that case,
            proceed using your tools and your normal procedure.""";

    @Value("${agent.tool-search.threshold:15}")
    private int toolSearchThreshold;

    @Value("${agent.memory.semantic.top-k:6}")
    private int memoryTopK;

    @Value("${agent.memory.semantic.min-confidence:0.5}")
    private double memoryMinConfidence;

    @Value("${agent.memory.tools.enabled:true}")
    private boolean memoryToolsEnabled;

    @Value("${agent.skill-update.enabled:true}")
    private boolean skillUpdateEnabled;

    public ChatController(ChatClient chatClient,
                          ChatSessionService chatSessionService,
                          AssistantService assistantService,
                          BuiltinToolCatalog builtinToolCatalog,
                          AskUserQuestionToolFactory askUserQuestionToolFactory,
                          WebQuestionBridge webQuestionBridge,
                          ClarificationPersistence clarificationPersistence,
                          HttpToolCallbackFactory httpToolCallbackFactory,
                          McpToolCallbackFactory mcpToolCallbackFactory,
                          ToolEventRegistry toolEventRegistry,
                          ChatToolEventRepository toolEventRepository,
                          DynamicToolRegistry dynamicToolRegistry,
                          SearchToolsCallback searchToolsCallback,
                          InvokeToolCallback invokeToolCallback,
                          SkillWorkspaceService skillWorkspaceService,
                          ScopeGuardService scopeGuardService,
                          ChatMemory chatMemory,
                          DocumentService documentService,
                          QuickMlRagService quickMlRagService,
                          ResponseStyleService responseStyleService,
                          ObjectMapper objectMapper,
                          LlmUsageRecorder llmUsageRecorder,
                          SemanticMemoryService semanticMemoryService,
                          SecurityContextService securityContextService,
                          MemoryToolFactory memoryToolFactory,
                          SkillUpdateToolFactory skillUpdateToolFactory,
                          SkillUpdateBridge skillUpdateBridge,
                          SkillUpdatePersistence skillUpdatePersistence,
                          SkillService skillService) {
        this.chatClient = chatClient;
        this.chatSessionService = chatSessionService;
        this.assistantService = assistantService;
        this.builtinToolCatalog = builtinToolCatalog;
        this.askUserQuestionToolFactory = askUserQuestionToolFactory;
        this.webQuestionBridge = webQuestionBridge;
        this.clarificationPersistence = clarificationPersistence;
        this.httpToolCallbackFactory = httpToolCallbackFactory;
        this.mcpToolCallbackFactory = mcpToolCallbackFactory;
        this.toolEventRegistry = toolEventRegistry;
        this.toolEventRepository = toolEventRepository;
        this.dynamicToolRegistry = dynamicToolRegistry;
        this.searchToolsCallback = searchToolsCallback;
        this.invokeToolCallback = invokeToolCallback;
        this.skillWorkspaceService = skillWorkspaceService;
        this.scopeGuardService = scopeGuardService;
        this.chatMemory = chatMemory;
        this.documentService = documentService;
        this.quickMlRagService = quickMlRagService;
        this.responseStyleService = responseStyleService;
        this.objectMapper = objectMapper;
        this.llmUsageRecorder = llmUsageRecorder;
        this.semanticMemoryService = semanticMemoryService;
        this.securityContextService = securityContextService;
        this.memoryToolFactory = memoryToolFactory;
        this.skillUpdateToolFactory = skillUpdateToolFactory;
        this.skillUpdateBridge = skillUpdateBridge;
        this.skillUpdatePersistence = skillUpdatePersistence;
        this.skillService = skillService;
    }

    public record Chunk(String text) {}

    public record ToolCallEvent(String id, String name, String input) {}

    public record ToolResultEvent(String id, String output, boolean error) {}

    private String buildSkillCatalogContext(String assistantId) {
        if (assistantId == null || assistantId.isBlank()) {
            return null;
        }
        try {
            List<SkillDto> skills = skillService.list(assistantId);
            if (skills.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder(
                    "UPLOADED SKILLS FOR THIS ASSISTANT (use these skillId values with propose_skill_update):");
            for (SkillDto skill : skills) {
                sb.append("\n- id: ").append(skill.getId());
                sb.append(" | name: ").append(skill.getName());
                if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                    sb.append(" | description: ").append(skill.getDescription().strip());
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            log.warn("Could not load skill catalog for assistant {}: {}", assistantId, e.getMessage());
            return null;
        }
    }

    /**
     * Builds the long-term-memory block injected into the system prompt: the top facts the agent has
     * learned about the current user (scoped to this assistant, plus the user's cross-assistant
     * facts), recalled by relevance to the current message. Returns {@code null} when there is nothing
     * relevant. Never fails the turn — a recall hiccup just means no memory injected this turn.
     */
    private String buildMemoryContext(String assistantId, String message) {
        try {
            String userId = securityContextService.currentUserIdOrThrow();
            List<ScoredFact> facts = semanticMemoryService.recall(
                    userId, assistantId, message, memoryTopK, memoryMinConfidence);
            if (facts.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder(
                    "WHAT YOU REMEMBER ABOUT THIS USER (from earlier conversations; may be incomplete — "
                            + "use only when relevant, and prefer what the user says now):");
            for (ScoredFact f : facts) {
                sb.append("\n- ").append(f.render());
            }
            // Memory must shape the actual output, not just reasoning. Apply remembered presentation
            // preferences (e.g. preferred date format, units, name) to EVERY value you emit — including
            // dates and values you place into tables, lists, and ```ui data blocks. Reformatting a
            // value for presentation is not fabricating it; keep the underlying data accurate.
            sb.append("\n\nApply the remembered presentation preferences above to all values in your "
                    + "response, including data you put into tables and ```ui blocks (for example, render "
                    + "every date in the user's preferred format). This is presentation only — do not "
                    + "change the underlying facts or values.");
            return sb.toString();
        } catch (RuntimeException e) {
            log.warn("Memory recall failed for assistant {}: {}", assistantId, e.getMessage());
            return null;
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@RequestParam String sessionId,
                                                @RequestParam String message,
                                                @RequestParam(required = false) String styleId,
                                                @RequestParam(required = false) String lang,
                                                HttpServletResponse response) {
        // Keep the SSE stream un-buffered end to end. `no-transform` tells any compression layer
        // (the webpack dev-server proxy, nginx/CDN in prod) NOT to gzip the response — gzip buffers
        // tiny token events and flushes them in one lump, which makes streaming look frozen on
        // "Thinking…" until the turn ends. `X-Accel-Buffering: no` disables nginx proxy buffering.
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        // Copy auth from the servlet thread (JWT filter has run). subscribeOn runs on another
        // thread where SecurityContextHolder is empty; the request thread may clear its context
        // when the async write starts.
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        return ChatSseEmitter.withKeepAlive(
                        Flux.defer(() -> buildTurnFluxWithSecurityContext(
                                        securityContext, sessionId, message, styleId, lang))
                                .subscribeOn(Schedulers.boundedElastic()),
                        lang)
                .onErrorResume(e -> {
                    log.warn("Chat stream failed for session {}: {}", sessionId, e.getMessage());
                    return Flux.just(
                            sse("error", new Chunk(streamErrorMessage(e))),
                            sse("done", new Chunk("")));
                });
    }

    private Flux<ServerSentEvent<Object>> buildTurnFluxWithSecurityContext(SecurityContext securityContext,
                                                                             String sessionId,
                                                                             String message,
                                                                             String styleId,
                                                                             String lang) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(securityContext);
            return buildTurnFlux(sessionId, message, styleId, lang);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static String streamErrorMessage(Throwable e) {
        if (e instanceof IllegalArgumentException && e.getMessage() != null
                && e.getMessage().contains("Authenticated user")) {
            return "Authentication required. Please sign in again.";
        }
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? "Stream failed" : msg;
    }

    private Flux<ServerSentEvent<Object>> buildTurnFlux(String sessionId,
                                                        String message,
                                                        String styleId,
                                                        String lang) {
        // "en" default keeps every downstream lang check a simple equality/blank test.
        String effectiveLang = (lang == null || lang.isBlank()) ? "en" : lang;
        boolean kannada = "kn".equalsIgnoreCase(effectiveLang);
        // Single in-house model for every turn — no per-session/per-message provider selection.
        ChatClient effectiveChatClient = chatClient;

        String assistantId = chatSessionService.resolveAssistantId(sessionId);
        // Temporary chats persist and render like any chat, but are isolated from long-term memory:
        // no recall injection, no remember/recall tools, and consolidation is skipped (in the
        // summarizer). Resolved from the session's `temporary` flag so every turn honors it.
        boolean temporary = chatSessionService.isTemporary(sessionId);
        String requestId = UUID.randomUUID().toString();
        // Captured here (request thread) because usage recording later runs on reactive/async
        // threads where the SecurityContext is empty.
        String turnUserId = securityContextService.currentUserIdOrThrow();
        LlmUsageContext usageContext = new LlmUsageContext(requestId, sessionId, assistantId, turnUserId);
        // Investigative-grade crime tools (financial trails, offender risk/network analysis) are
        // gated to ADMIN/SUPERVISOR/INVESTIGATOR — mirrors the REST layer's /risk-scores pattern.
        // Captured here (request thread, SecurityContext still valid) and closed over in
        // wrapToolCallback(...); the tool-execution thread later sees an empty SecurityContextHolder.
        boolean investigativeAccess = securityContextService.hasAnyRole("ADMIN", "SUPERVISOR", "INVESTIGATOR");

        chatSessionService.touchAndMaybeTitle(sessionId, message, requestId);
        String systemPrompt = "";
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        // Assistant entity, builtin tool keys, HTTP callbacks and the document list are each loaded
        // ONCE here and reused everywhere below (they used to be re-queried mid-turn for counts).
        final List<String> builtinToolKeysList;
        final List<ToolCallback> httpToolCallbacks;
        if (assistantId != null) {
            Assistant assistant = assistantService.requireEntity(assistantId);
            systemPrompt = assistant.getSystemPrompt() == null ? ""
                    : assistant.getSystemPrompt();
            builtinToolKeysList = assistantService.builtinToolKeys(assistant);
            httpToolCallbacks = httpToolCallbackFactory.callbacksForAssistant(assistantId);
            toolCallbacks.addAll(builtinToolCatalog.callbacksFor(builtinToolKeysList));
            toolCallbacks.addAll(httpToolCallbacks);
        } else {
            builtinToolKeysList = List.of();
            httpToolCallbacks = List.of();
        }

        // Does the assistant have enabled RAG documents? Computed once and reused: it both lets a
        // doc-relevant message bypass the scope guard AND gates whether QuickML RAG is consulted
        // (QuickML decides actual relevance internally — there is no local vector store to pre-probe).
        final List<String> enabledDocumentNames = assistantId != null
                ? documentService.list(assistantId).stream()
                        .filter(DocumentDto::isEnabled)
                        .map(DocumentDto::getName)
                        .toList()
                : List.<String>of();
        boolean docsRelevant = message != null && !message.isBlank() && !enabledDocumentNames.isEmpty();

        // Scope guard (hard pre-check): when the assistant has a defined role, classify the message
        // before reaching the main model. Out-of-scope messages are short-circuited with a redirect —
        // no model call, no tools. Disabled / no-role => allowed; classifier errors fail open.
        // Exception: if the assistant's attached RAG documents actually contain content relevant to
        // the message (a vector-similarity hit at the same threshold the RAG advisor uses), the
        // documents have widened the assistant's scope — allow it through without the classifier.
        // Also exempt explicit memory requests ("remember …", "forget …") so a role-scoped assistant
        // doesn't refuse them as off-topic before the memory tool can run — memory is a cross-cutting
        // capability, not part of the assistant's domain scope.
        // Likewise exempt admin skill-update requests — propose_skill_update is registered later but
        // must not be blocked as "off topic" for a narrow assistant role (e.g. Crime Assistant).
        boolean memoryRequest = memoryToolsEnabled && !temporary && isMemoryRequest(message);
        boolean adminSkillUpdateEligible =
                skillUpdateEnabled && !temporary && securityContextService.isAdmin();
        boolean skillUpdateRequest = adminSkillUpdateEligible && isSkillUpdateRequest(message);
        if (!systemPrompt.isBlank() && !docsRelevant && !memoryRequest && !skillUpdateRequest) {
            List<String> toolSummaries = new ArrayList<>(toolCallbacks.stream()
                    .map(cb -> cb.getToolDefinition().name() + " — " + cb.getToolDefinition().description())
                    .toList());
            if (adminSkillUpdateEligible) {
                toolSummaries.add(SkillUpdateToolFactory.scopeGuardToolSummary());
            }
            // Classifier context: only the last few messages — the classifier disambiguates short
            // follow-ups; it never needs (or should pay tokens for) the whole transcript. Document
            // names (already loaded above) also go along so a descriptively-named doc can pass.
            List<Message> history = chatMemory.get(sessionId);
            List<Message> recentHistory = history.size() > SCOPE_GUARD_HISTORY_MESSAGES
                    ? history.subList(history.size() - SCOPE_GUARD_HISTORY_MESSAGES, history.size())
                    : history;
            ScopeGuardService.Decision decision =
                    scopeGuardService.check(systemPrompt, toolSummaries, enabledDocumentNames,
                            recentHistory, message, usageContext, effectiveLang);
            if (!decision.allowed()) {
                String redirect = decision.redirect();
                // Persist the turn so a reload shows it (the memory advisor only auto-saves on a real
                // model call, which we are deliberately skipping here).
                try {
                    ChatAuditLog.putContext(sessionId, requestId, assistantId, turnUserId, 0);
                    chatMemory.add(sessionId,
                            List.of(new UserMessage(message), new AssistantMessage(redirect)));
                } catch (RuntimeException e) {
                    log.warn("Failed to persist scope-guard refusal for session {}: {}", sessionId, e.getMessage());
                } finally {
                    ChatAuditLog.clearContext();
                }
                log.debug("Scope guard blocked an out-of-scope message for session {}", sessionId);
                return Flux.just(sse("message", new Chunk(redirect)), sse("done", new Chunk("")));
            }
        }

        // MCP tools: open live clients to the assistant's enabled MCP servers and fold their enabled
        // tools into the same callback list as HTTP/builtin tools. Done AFTER the scope-guard early
        // return so we never open (and leak) connections for a blocked message. The live clients are
        // owned by mcpTools and closed in doFinally when the turn ends.
        final AssistantMcpTools mcpTools = assistantId != null
                ? mcpToolCallbackFactory.callbacksForAssistant(assistantId)
                : AssistantMcpTools.empty();
        toolCallbacks.addAll(mcpTools.callbacks());

        Path skillWorkspace = skillWorkspaceService.materialize(assistantId);

        // Agent skills: Skill discovery + file system + shell tools. Folded into toolCallbacks (not
        // appended straight to toolsToSend) BEFORE the searchMode threshold check below, so these
        // tools count toward the threshold and get hidden behind search_tools/invoke_tool for
        // tool-heavy assistants exactly like any other tool. Their schemas are large enough that
        // always sending them directly can blow past a model endpoint's request-size ceiling
        // (confirmed empirically against Catalyst QuickML LLM Serving's ~34KB limit).
        List<String> skillToolsSkippedDuplicate = new ArrayList<>();
        if (skillWorkspace != null) {
            List<ToolCallback> skillTools = new ArrayList<>();
            skillTools.add(SkillsTool.builder()
                    .addSkillsDirectory(skillWorkspace.toString())
                    .build());
            skillTools.addAll(List.of(ToolCallbacks.from(FileSystemTools.builder().build())));
            skillTools.addAll(List.of(ToolCallbacks.from(ShellTools.builder().build())));
            // De-dupe by tool name: the file-system/shell tools the skills need (Read, Write, Edit,
            // Bash, …) may already be present as built-in tools for this assistant. Spring AI rejects
            // a request with two tools of the same name, so only add skill tools whose name isn't
            // already present.
            Set<String> existingNames = toolCallbacks.stream()
                    .map(cb -> cb.getToolDefinition().name())
                    .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
            for (ToolCallback cb : skillTools) {
                String name = cb.getToolDefinition().name();
                if (existingNames.add(name)) {
                    toolCallbacks.add(cb);
                } else {
                    skillToolsSkippedDuplicate.add(name);
                }
            }
        }

        Sinks.Many<ServerSentEvent<Object>> toolSink = Sinks.many().multicast().onBackpressureBuffer();
        Sinks.EmitFailureHandler emitHandler =
                Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(50));

        // Tool events are persisted to chat_tool_event so they survive reload (Spring AI chat
        // memory stores only message text, not tool call/result data). Tag each event with the
        // assistant-turn index this exchange will occupy so it re-attaches to the right bubble.
        int turnIndex = (int) chatSessionService.assistantMessageCount(sessionId);
        AtomicInteger seq = new AtomicInteger(0);
        Map<String, String[]> pendingCalls = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<String> toolsInvokedInOrder = new CopyOnWriteArrayList<>();
        AtomicInteger toolCallCount = new AtomicInteger(0);

        final int httpToolCount = httpToolCallbacks.size();
        final int mcpToolCount = mcpTools.callbacks().size();

        boolean askUserQuestionEnabled = builtinToolKeysList.contains(BuiltinToolCatalog.ASK_USER_QUESTION_KEY);
        if (askUserQuestionEnabled) {
            toolCallbacks.addAll(askUserQuestionToolFactory.callbacks(
                    requestId, toolSink, emitHandler, effectiveLang));
        }

        // Long-term memory tools: let the model actively store ("remember …") and look up durable
        // facts during the turn. Always-on for every assistant (global kill-switch). Identity is
        // captured here on the request thread — the SecurityContext is empty on the streaming threads
        // where the tool later executes. Best-effort: any failure disables the tools for this turn
        // rather than failing the whole request.
        boolean memoryToolsActive = false;
        List<ToolCallback> memoryToolCallbacks = List.of();
        if (memoryToolsEnabled && !temporary) {   // temporary chats: no remember/recall tools (§3.7)
            try {
                String memoryUserId = securityContextService.currentUserIdOrThrow();
                memoryToolCallbacks = memoryToolFactory.callbacks(
                        memoryUserId, assistantId, sessionId, memoryTopK, memoryMinConfidence);
                toolCallbacks.addAll(memoryToolCallbacks);
                memoryToolsActive = true;
            } catch (RuntimeException e) {
                log.warn("Memory tools disabled for this turn (session {}): {}", sessionId, e.getMessage());
            }
        }

        boolean skillUpdateToolsActive = false;
        List<ToolCallback> skillUpdateToolCallbacks = List.of();
        if (skillUpdateEnabled && !temporary && securityContextService.isAdmin()) {
            try {
                String skillUpdateUserId = securityContextService.currentUserIdOrThrow();
                skillUpdateToolCallbacks = skillUpdateToolFactory.callbacks(
                        requestId,
                        skillUpdateUserId,
                        assistantId,
                        sessionId,
                        turnIndex,
                        requestId,
                        true,
                        toolSink,
                        emitHandler,
                        effectiveLang);
                toolCallbacks.addAll(skillUpdateToolCallbacks);
                skillUpdateToolsActive = true;
            } catch (RuntimeException e) {
                log.warn("Skill update tools disabled for this turn (session {}): {}", sessionId, e.getMessage());
            }
        }

        toolEventRegistry.register(requestId, new ToolEventSink() {
            @Override
            public void toolCall(String id, String name, String input) {
                pendingCalls.put(id, new String[] { name, input });
                toolsInvokedInOrder.add(name);
                toolCallCount.incrementAndGet();
                // AskUserQuestion: questions go out as `clarification` SSE; answers via POST /clarifications.
                if (AskUserQuestionToolFactory.isAskUserQuestionTool(name)) {
                    webQuestionBridge.registerToolCall(requestId, sessionId, turnIndex, id);
                    try {
                        clarificationPersistence.saveToolCall(sessionId, turnIndex, seq.getAndIncrement(),
                                id, input, Instant.now().getEpochSecond());
                    } catch (RuntimeException e) {
                        log.warn("Failed to persist clarification for session {}: {}", sessionId, e.getMessage());
                    }
                } else if (SkillUpdateToolFactory.isSkillUpdateTool(name)) {
                    skillUpdateBridge.registerToolCall(requestId, sessionId, turnIndex, id);
                    try {
                        skillUpdatePersistence.saveToolCall(sessionId, turnIndex, seq.getAndIncrement(),
                                id, input, Instant.now().getEpochSecond());
                    } catch (RuntimeException e) {
                        log.warn("Failed to persist skill update for session {}: {}", sessionId, e.getMessage());
                    }
                } else {
                    toolSink.emitNext(
                            ChatSseEmitter.status(ToolProgressCopy.forTool(name, effectiveLang)),
                            emitHandler);
                    toolSink.emitNext(sse("tool", new ToolCallEvent(id, name, input)), emitHandler);
                }
            }

            @Override
            public void toolResult(String id, String output, boolean error) {
                String[] call = pendingCalls.remove(id);
                String toolName = call != null ? call[0] : "";
                String input = call != null ? call[1] : null;
                if (!AskUserQuestionToolFactory.isAskUserQuestionTool(toolName)
                        && !SkillUpdateToolFactory.isSkillUpdateTool(toolName)) {
                    toolSink.emitNext(sse("tool_result", new ToolResultEvent(id, truncate(output), error)), emitHandler);
                }
                try {
                    if (!AskUserQuestionToolFactory.isAskUserQuestionTool(toolName)
                            && !SkillUpdateToolFactory.isSkillUpdateTool(toolName)) {
                        toolEventRepository.save(sessionId, turnIndex, seq.getAndIncrement(),
                                id, toolName, input, output, error, Instant.now().getEpochSecond());
                    } else if (AskUserQuestionToolFactory.isAskUserQuestionTool(toolName)
                            && output != null && !output.isBlank()) {
                        // Backup if POST /clarifications already wrote answers.
                        clarificationPersistence.saveAnswersFromToolOutput(sessionId, id, output);
                    }
                } catch (RuntimeException e) {
                    log.warn("Failed to persist tool event for session {}: {}", sessionId, e.getMessage());
                }
            }
        });

        // When an assistant has more tools than the threshold, don't send them all to the model
        // (token bloat + worse tool selection). Instead expose only search_tools + invoke_tool and
        // let the model discover/dispatch the real tools on demand. The real (instrumented) tools
        // are stashed in the DynamicToolRegistry so invoke_tool can route to them by name, keeping
        // the existing event/UI/persistence behavior for the underlying tool calls.
        // Response style: an explicit per-message styleId overrides the session's pinned style.
        // Style shapes structure/tone and applies even when the assistant has no role prompt, so it
        // is composed into the base prompt (before the tool hint and scope guardrail) below.
        String resolvedStyleId = (styleId != null && !styleId.isBlank())
                ? styleId : chatSessionService.resolveStyleId(sessionId);
        String styleInstructions = responseStyleService.instructionsFor(resolvedStyleId);
        String basePrompt = systemPrompt;
        if (styleInstructions != null) {
            basePrompt = (basePrompt.isBlank() ? "" : basePrompt + "\n\n")
                    + "RESPONSE STYLE — follow these formatting/tone instructions for your reply:\n"
                    + styleInstructions;
        }
        // Global rules apply to every agent (even those with no role prompt), so inject them
        // unconditionally into the base prompt before the tool hint and scope guardrail.
        basePrompt = (basePrompt.isBlank() ? "" : basePrompt + "\n\n") + GLOBAL_RULES;
        basePrompt = basePrompt + "\n\n"
                + "CURRENT DATE (always apply): today is " + LocalDate.now(APP_ZONE).format(CURRENT_DATE_FMT)
                + ". Resolve every relative time expression (\"this year\", \"this month\", \"last 90 days\", "
                + "\"last N months\", \"YTD\") against this date when building a query — never assume a year "
                + "from training data or from any example elsewhere in these instructions.";

        boolean searchMode = toolCallbacks.size() > toolSearchThreshold;
        List<ToolCallback> toolsToSend;
        List<String> searchCatalogToolNames = List.of();
        String effectiveSystemPrompt = basePrompt;
        if (searchMode) {
            Map<String, ToolCallback> catalog = new LinkedHashMap<>();
            for (ToolCallback cb : toolCallbacks) {
                // Memory tools stay directly callable (like search_tools/invoke_tool) rather than being
                // hidden behind the search catalog, so "remember …" works even for tool-heavy assistants.
                if (MemoryToolFactory.isMemoryTool(cb.getToolDefinition().name())) {
                    continue;
                }
                if (SkillUpdateToolFactory.isSkillUpdateTool(cb.getToolDefinition().name())) {
                    continue;
                }
                catalog.put(cb.getToolDefinition().name(), wrapToolCallback(cb, skillWorkspace, investigativeAccess));
            }
            searchCatalogToolNames = new ArrayList<>(catalog.keySet());
            dynamicToolRegistry.register(requestId, assistantId, catalog);
            toolsToSend = new ArrayList<>(List.of(
                    wrapToolCallback(searchToolsCallback, skillWorkspace, investigativeAccess),
                    wrapToolCallback(invokeToolCallback, skillWorkspace, investigativeAccess)));
            for (ToolCallback mt : memoryToolCallbacks) {
                toolsToSend.add(wrapToolCallback(mt, skillWorkspace, investigativeAccess));
            }
            for (ToolCallback st : skillUpdateToolCallbacks) {
                toolsToSend.add(wrapToolCallback(st, skillWorkspace, investigativeAccess));
            }
            effectiveSystemPrompt = (basePrompt.isBlank() ? "" : basePrompt + "\n\n") + TOOL_SEARCH_HINT;
        } else {
            toolsToSend = toolCallbacks.stream()
                    .map(cb -> wrapToolCallback(cb, skillWorkspace, investigativeAccess))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        // Keep the assistant on-task: when it has a defined role (a system prompt), append the scope
        // guardrail so it refuses out-of-context questions. Skipped when no assistant/prompt is set,
        // since there is no scope to enforce against.
        if (!systemPrompt.isBlank()) {
            effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                    + SCOPE_GUARDRAIL;
            if (skillUpdateToolsActive) {
                effectiveSystemPrompt = effectiveSystemPrompt + "\n\n" + SCOPE_GUARDRAIL_SKILL_UPDATE_EXCEPTION;
            }
        }
        if (askUserQuestionEnabled) {
            effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                    + ASK_USER_QUESTION_HINT;
        }
        if (memoryToolsActive) {
            effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                    + MEMORY_TOOLS_HINT;
        }
        if (skillUpdateToolsActive) {
            // The improvement hint + catalog only earn their tokens when the assistant actually has
            // at least one uploaded skill to improve (buildSkillCatalogContext is null otherwise).
            String skillCatalog = buildSkillCatalogContext(assistantId);
            if (skillCatalog != null) {
                effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                        + SKILL_UPDATE_HINT + "\n\n" + skillCatalog;
            }
        }
        if (skillWorkspace != null) {
            effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                    + SKILLS_HINT;
        }
        // Long-term memory: inject durable facts the agent has learned about this user (across all
        // their prior chats), recalled by relevance to the current message. Additive and best-effort.
        // Skipped for temporary chats (§3.7) so no stored fact enters the prompt.
        if (!temporary) {
            String memoryContext = buildMemoryContext(assistantId, message);
            if (memoryContext != null) {
                effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                        + memoryContext;
            }
        }
        // Dead last, deliberately — see LANG_DIRECTIVE_KN's comment.
        effectiveSystemPrompt = (effectiveSystemPrompt.isBlank() ? "" : effectiveSystemPrompt + "\n\n")
                + (kannada ? LANG_DIRECTIVE_KN : LANG_DIRECTIVE_EN);

        List<String> toolNamesForModel = toolsToSend.stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList();
        ChatAuditLog.putContext(sessionId, requestId, assistantId, turnUserId, turnIndex);
        ChatAuditLog.toolsSentToModel(searchMode, toolNamesForModel, searchCatalogToolNames,
                skillToolsSkippedDuplicate);

        boolean scopeGuardSkipped = systemPrompt.isBlank() || docsRelevant;
        boolean ragAdvisorAttached = docsRelevant;

        Map<String, Object> turnStartFields = new LinkedHashMap<>();
        turnStartFields.put("sessionId", sessionId);
        turnStartFields.put("requestId", requestId);
        turnStartFields.put("assistantId", assistantId == null ? "" : assistantId);
        turnStartFields.put("turnIndex", turnIndex);
        turnStartFields.put("searchMode", searchMode);
        turnStartFields.put("toolCount", toolNamesForModel.size());
        turnStartFields.put("toolNames", String.join(",", toolNamesForModel));
        turnStartFields.put("builtinTools", String.join(",", builtinToolKeysList));
        turnStartFields.put("httpToolCount", httpToolCount);
        turnStartFields.put("mcpToolCount", mcpToolCount);
        turnStartFields.put("skillWorkspace", skillWorkspace == null ? "none" : skillWorkspace.toString());
        if (skillWorkspace != null) {
            turnStartFields.put("skillBaseDir",
                    SkillWorkspacePaths.resolveSkillBaseDir(skillWorkspace).toString());
        }
        turnStartFields.put("docsRelevant", docsRelevant);
        turnStartFields.put("scopeGuardSkipped", scopeGuardSkipped);
        turnStartFields.put("ragAdvisorAttached", ragAdvisorAttached);
        ChatAuditLog.turnStart(turnStartFields);

        Map<String, Object> toolContextMap = new LinkedHashMap<>();
        toolContextMap.put(ToolEventRegistry.REQUEST_ID, requestId);
        if (skillWorkspace != null) {
            toolContextMap.put(SkillWorkspaceShellToolCallback.SKILL_WORKSPACE, skillWorkspace.toString());
        }

        // RAG: when the assistant has documents, ask Catalyst QuickML RAG for a context-augmented
        // reference answer and wrap the user message with it (see RAG_PROMPT_TEMPLATE). QuickML owns
        // the chunking/embeddings/vector search; on any miss or error the message is sent as-is.
        String promptText = message;
        if (docsRelevant) {
            String ragContext = quickMlRagService.answer(assistantId, message);
            if (ragContext != null && !ragContext.isBlank()) {
                promptText = RAG_PROMPT_TEMPLATE.formatted(message, ragContext);
            }
        }

        ChatClient.ChatClientRequestSpec request = effectiveChatClient.prompt(promptText)
                .toolContext(toolContextMap)
                .toolCallbacks(toolsToSend)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));
        if (!effectiveSystemPrompt.isBlank()) {
            request = request.system(effectiveSystemPrompt);
        }

        AtomicReference<Usage> lastUsage = new AtomicReference<>();

        Flux<ServerSentEvent<Object>> tokens = request
                .stream()
                .chatResponse()
                .flatMap(cr -> {
                    if (cr.getMetadata() != null && cr.getMetadata().getUsage() != null) {
                        lastUsage.set(cr.getMetadata().getUsage());
                    }
                    String text = extractChunkText(cr);
                    if (text == null || text.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(sse("message", new Chunk(text)));
                })
                .onErrorResume(e -> {
                    log.error("Chat stream failed for session {} assistant {}", sessionId, assistantId, e);
                    return Flux.just(sse("error", new Chunk(e.getMessage())));
                })
                .doOnComplete(toolSink::tryEmitComplete)
                .doOnError(e -> toolSink.tryEmitComplete());

        Flux<ServerSentEvent<Object>> done =
                Flux.just(sse("done", new Chunk("")));

        Flux<ServerSentEvent<Object>> merged = Flux.merge(toolSink.asFlux(), tokens)
                .concatWith(done)
                .doFinally(signal -> {
                    String distinctTools = toolsInvokedInOrder.stream()
                            .distinct()
                            .collect(Collectors.joining(","));
                    Map<String, Object> turnEndFields = new LinkedHashMap<>();
                    turnEndFields.put("sessionId", sessionId);
                    turnEndFields.put("requestId", requestId);
                    turnEndFields.put("signal", ChatAuditLog.signalTypeName(signal));
                    turnEndFields.put("toolsInvoked", distinctTools.isEmpty() ? "none" : distinctTools);
                    turnEndFields.put("toolCallCount", toolCallCount.get());
                    ChatAuditLog.turnEnd(turnEndFields);
                    llmUsageRecorder.record(usageContext, LlmUsageSource.main, lastUsage.get());
                    toolEventRegistry.unregister(requestId);
                    dynamicToolRegistry.unregister(requestId);
                    webQuestionBridge.cancel(requestId);
                    skillUpdateBridge.cancel(requestId);
                    skillWorkspaceService.cleanup(skillWorkspace);
                    mcpTools.close();
                });

        return ChatAuditLog.runWithContext(sessionId, requestId, assistantId, turnUserId, turnIndex, merged);
    }

    /**
     * Heuristic: does this message explicitly ask the assistant to store or retrieve a long-term
     * memory? Used to exempt such messages from the scope guard so a role-scoped assistant doesn't
     * refuse "remember …" as off-topic. Intentionally conservative — keyed on explicit memory verbs.
     */
    private static boolean isMemoryRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("remember")
                || m.contains("don't forget") || m.contains("dont forget")
                || m.contains("keep in mind")
                || m.contains("make a note") || m.contains("note that")
                || m.contains("from now on")
                || m.contains("forget what you know") || m.contains("forget that")
                || m.contains("what do you remember") || m.contains("what do you know about me");
    }

    /**
     * Heuristic: does this message ask to update an uploaded skill file? Used to exempt such messages
     * from the scope guard so a role-scoped assistant does not refuse skill edits before
     * {@code propose_skill_update} can run. Intentionally conservative — keyed on explicit skill-update
     * phrasing. Package-visible for unit tests.
     */
    static boolean isSkillUpdateRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase();
        if (m.contains("propose_skill_update")) {
            return true;
        }
        if (m.contains("wait for approval") || m.contains("wait for my approval")
                || m.contains("propose the update") || m.contains("propose an update")) {
            return true;
        }
        if (m.contains("skill.md") || m.contains("leg-sequences") || m.contains("references/")) {
            return true;
        }
        return m.contains("update skill") || m.contains("update the skill")
                || m.contains("edit skill") || m.contains("edit the skill")
                || m.contains("change skill") || m.contains("change the skill")
                || m.contains("modify skill") || m.contains("modify the skill")
                || m.contains("update uploaded skill") || m.contains("improve the skill")
                || m.contains("improve skill");
    }

    /**
     * Crime tools that expose investigative-grade signal (financial trails, offender-network/risk
     * analysis) and require ADMIN/SUPERVISOR/INVESTIGATOR — see Phase 2.4 of the remediation plan.
     * {@code run_crime_sql} is gated separately by {@link SqlTableGateToolCallback} since it is
     * arbitrary text-to-SQL: naming a restricted table wouldn't be blocked by hiding this tool.
     */
    private static final Set<String> INVESTIGATIVE_TOOLS = Set.of(
            "list_account_transactions", "trace_money_network", "suspicious_transactions",
            "offender_profile", "detect_offender_groups");

    private ToolCallback wrapToolCallback(ToolCallback cb, Path skillWorkspace, boolean investigativeAccess) {
        ToolCallback inner = cb;
        String name = cb.getToolDefinition().name();
        if ("run_crime_sql".equals(name)) {
            inner = new SqlTableGateToolCallback(inner, investigativeAccess);
        } else if (INVESTIGATIVE_TOOLS.contains(name)) {
            inner = new RoleGatedToolCallback(inner, investigativeAccess);
        }
        if (skillWorkspace != null) {
            if (ChatAuditLog.isShellLikeTool(name)) {
                inner = new SkillWorkspaceShellToolCallback(inner, objectMapper);
            } else if (isFileSystemTool(name)) {
                inner = new SkillWorkspaceFileToolCallback(inner, objectMapper);
            } else {
                // Generic, policy-free: mirror any data tool's JSON output to
                // <skillBase>/.skill_io/<toolName>.json so a skill script can read the full payload
                // from a predictable path without the model re-serializing it (and dropping fields).
                inner = new SkillWorkspaceToolOutputMirrorCallback(inner);
            }
        }
        return new EventEmittingToolCallback(inner, toolEventRegistry);
    }

    private static boolean isFileSystemTool(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return "read".equals(lower) || "write".equals(lower) || "edit".equals(lower);
    }

    private static ServerSentEvent<Object> sse(String event, Object data) {
        return ServerSentEvent.builder(data).event(event).build();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n… (truncated)";
    }

    private static String extractChunkText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

}
