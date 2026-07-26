package com.ksp.agent.chat.guard.impl;

import com.ksp.agent.chat.guard.ScopeGuardService;
import com.ksp.agent.chat.usage.LlmUsageContext;
import com.ksp.agent.chat.usage.LlmUsageRecorder;
import com.ksp.agent.chat.usage.LlmUsageSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ScopeGuardServiceImpl implements ScopeGuardService {

    /** How many trailing messages of context to give the classifier for follow-up disambiguation. */
    private static final int HISTORY_TAIL = 4;

    private static final String DEFAULT_REDIRECT =
            "That request is outside what I'm set up to help with. Let me know if there's something "
                    + "within my area I can do for you.";
    private static final String DEFAULT_REDIRECT_KN =
            "ಆ ವಿನಂತಿಯು ನಾನು ಸಹಾಯ ಮಾಡಲು ಸಿದ್ಧಪಡಿಸಿರುವ ವ್ಯಾಪ್ತಿಯ ಹೊರಗಿದೆ. ನನ್ನ ವ್ಯಾಪ್ತಿಯೊಳಗೆ ನಾನು "
                    + "ಸಹಾಯ ಮಾಡಬಹುದಾದ ಏನಾದರೂ ಇದ್ದರೆ ತಿಳಿಸಿ.";

    private final boolean enabled;
    private final ChatClient scopeGuardChatClient;
    private final LlmUsageRecorder llmUsageRecorder;

    public ScopeGuardServiceImpl(@Value("${agent.scope-guard.enabled:true}") boolean enabled,
                                 @Qualifier("scopeGuardChatClient") ChatClient scopeGuardChatClient,
                                 LlmUsageRecorder llmUsageRecorder) {
        this.enabled = enabled;
        this.scopeGuardChatClient = scopeGuardChatClient;
        this.llmUsageRecorder = llmUsageRecorder;
    }

    @Override
    public Decision check(String assistantRole, List<String> toolSummaries, List<String> documentNames,
                          List<Message> recentHistory, String userMessage, LlmUsageContext usageContext,
                          String lang) {
        if (!enabled || assistantRole == null || assistantRole.isBlank()
                || userMessage == null || userMessage.isBlank()) {
            return Decision.allow();
        }
        boolean kannada = "kn".equalsIgnoreCase(lang);
        try {
            String prompt = buildPrompt(assistantRole, toolSummaries, documentNames, recentHistory, userMessage, kannada);
            ChatResponse response = scopeGuardChatClient.prompt().user(prompt).call().chatResponse();
            if (usageContext != null) {
                llmUsageRecorder.recordFromResponse(usageContext, LlmUsageSource.scope_guard, response);
            }
            String raw = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            return parse(raw, kannada);
        } catch (Exception e) {
            // Fail open: never break chat because the classifier hiccuped.
            log.warn("Scope guard classification failed, allowing message through: {}", e.getMessage());
            return Decision.allow();
        }
    }

    private Decision parse(String raw, boolean kannada) {
        if (raw == null || raw.isBlank()) {
            return Decision.allow();
        }
        String trimmed = raw.strip();
        if (trimmed.regionMatches(true, 0, "ALLOW", 0, "ALLOW".length())) {
            return Decision.allow();
        }
        if (!trimmed.regionMatches(true, 0, "BLOCK", 0, "BLOCK".length())) {
            // Classifier didn't follow the strict ALLOW/BLOCK contract (e.g. it hallucinated a full
            // conversational reply instead of the one-word verdict). Fail open rather than showing
            // that raw, ungoverned text to the user as if it were a scope-decline message.
            log.warn("Scope guard returned an unrecognized verdict, allowing message through: {}",
                    trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed);
            return Decision.allow();
        }
        String redirect = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            redirect = trimmed.substring(colon + 1).strip();
        }
        if (redirect.isBlank()) {
            redirect = kannada ? DEFAULT_REDIRECT_KN : DEFAULT_REDIRECT;
        }
        return Decision.block(redirect);
    }

    private String buildPrompt(String assistantRole, List<String> toolSummaries, List<String> documentNames,
                               List<Message> recentHistory, String userMessage, boolean kannada) {
        StringBuilder sb = new StringBuilder();
        sb.append("ROLE:\n").append(assistantRole.strip()).append("\n\n");

        sb.append("TOOLS:\n");
        if (toolSummaries == null || toolSummaries.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String t : toolSummaries) {
                sb.append("- ").append(t).append('\n');
            }
        }
        sb.append('\n');

        sb.append("DOCUMENTS:\n");
        if (documentNames == null || documentNames.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String d : documentNames) {
                sb.append("- ").append(d).append('\n');
            }
        }
        sb.append('\n');

        sb.append("RECENT CONVERSATION:\n");
        List<Message> tail = tail(recentHistory);
        if (tail.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Message m : tail) {
                String role = m.getMessageType() == MessageType.USER ? "User"
                        : m.getMessageType() == MessageType.ASSISTANT ? "Assistant" : null;
                if (role == null || m.getText() == null || m.getText().isBlank()) {
                    continue;
                }
                sb.append(role).append(": ").append(m.getText().strip()).append('\n');
            }
        }
        sb.append('\n');

        sb.append("LANGUAGE:\n")
                .append(kannada ? "Write the BLOCK redirect message in Kannada (ಕನ್ನಡ)."
                        : "Write the BLOCK redirect message in English.")
                .append("\n\n");

        sb.append("MESSAGE:\n").append(userMessage.strip());
        return sb.toString();
    }

    private List<Message> tail(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - HISTORY_TAIL);
        return history.subList(from, history.size());
    }
}
