package com.ksp.agent.chat.tooling;

import com.ksp.agent.chat.clarification.ClarificationEventDto;
import com.ksp.agent.chat.clarification.WebQuestionBridge;
import com.ksp.agent.chat.sse.ChatSseEmitter;
import com.ksp.agent.chat.sse.ToolProgressCopy;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.List;

@Component
public class AskUserQuestionToolFactory {

    /** Spring AI tool name; UI uses {@code clarification} + POST /clarifications instead of tool cards. */
    public static final String TOOL_NAME = "AskUserQuestionTool";

    private final WebQuestionBridge webQuestionBridge;

    public AskUserQuestionToolFactory(WebQuestionBridge webQuestionBridge) {
        this.webQuestionBridge = webQuestionBridge;
    }

    public List<ToolCallback> callbacks(String requestId,
                                        Sinks.Many<ServerSentEvent<Object>> toolSink,
                                        Sinks.EmitFailureHandler emitHandler,
                                        String lang) {
        AskUserQuestionTool tool = AskUserQuestionTool.builder()
                .questionHandler(questions -> webQuestionBridge.awaitAnswers(
                        requestId,
                        questions,
                        event -> emitClarification(toolSink, emitHandler, event, lang)))
                .build();
        return List.of(ToolCallbacks.from(tool));
    }

    public static boolean isAskUserQuestionTool(String name) {
        return TOOL_NAME.equals(name);
    }

    private static void emitClarification(Sinks.Many<ServerSentEvent<Object>> toolSink,
                                          Sinks.EmitFailureHandler emitHandler,
                                          ClarificationEventDto event,
                                          String lang) {
        toolSink.emitNext(ChatSseEmitter.status(ToolProgressCopy.answerQuestions(lang)), emitHandler);
        toolSink.emitNext(
                ServerSentEvent.builder((Object) event).event("clarification").build(),
                emitHandler);
    }
}
