package com.ksp.agent.chat.sse;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * SSE helpers for {@code /api/chat/stream}: immediate first bytes, periodic comment keep-alives
 * (proxies treat silence as timeout), and {@code status} events for the UI during slow prep/tools.
 */
public final class ChatSseEmitter {

    /** Frequent enough to beat a 30s App Gateway default until infra timeout is raised. */
    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(8);

    private ChatSseEmitter() {}

    public record StatusEvent(String text) {}

    /**
     * Merges comment keep-alives with the turn flux. Emits a status line immediately so gateways
     * see bytes before slow prep (scope guard, RAG, MCP, title) finishes.
     */
    public static Flux<ServerSentEvent<Object>> withKeepAlive(Flux<ServerSentEvent<Object>> turn) {
        return withKeepAlive(turn, "en");
    }

    /** As {@link #withKeepAlive(Flux)}, but localizes the bootstrap "Preparing…" status line. */
    public static Flux<ServerSentEvent<Object>> withKeepAlive(Flux<ServerSentEvent<Object>> turn, String lang) {
        Flux<ServerSentEvent<Object>> bootstrap = Flux.just(
                keepAliveComment(),
                event("status", new StatusEvent(preparingResponseText(lang)))
        );
        // Subscribe `turn` EXACTLY ONCE. `turn` is a cold, deferred Flux (Flux.defer in
        // ChatController) that re-runs the whole turn — model call, tool loop AND chat-memory
        // persistence — on every subscription. The previous form referenced `turn` twice
        // (takeUntilOther(turn) + concat(..., turn)), so it was subscribed twice: every turn ran
        // twice, doubling LLM cost, executing tools like run_crime_sql twice, and persisting two
        // (differently-worded, temp>0) assistant messages that rendered as duplicate bubbles.
        //
        // Here `turn` is referenced only inside `content`. The infinite keep-alive interval is
        // stopped by the content's own terminal SSE event ("done"/"error"), which ChatController
        // always emits last — no second subscription to `turn` is needed to know when it ended.
        Flux<ServerSentEvent<Object>> content = Flux.concat(bootstrap, turn);
        Flux<ServerSentEvent<Object>> keepAlive = Flux.interval(KEEP_ALIVE_INTERVAL)
                .map(tick -> keepAliveComment());
        return content.mergeWith(keepAlive)
                .takeUntil(sse -> "done".equals(sse.event()) || "error".equals(sse.event()));
    }

    public static ServerSentEvent<Object> keepAliveComment() {
        return ServerSentEvent.builder().comment("keep-alive").build();
    }

    public static ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    public static ServerSentEvent<Object> status(String text) {
        return event("status", new StatusEvent(text));
    }

    private static String preparingResponseText(String lang) {
        return "kn".equalsIgnoreCase(lang) ? "ಪ್ರತಿಕ್ರಿಯೆ ಸಿದ್ಧಪಡಿಸಲಾಗುತ್ತಿದೆ…" : "Preparing response…";
    }
}
