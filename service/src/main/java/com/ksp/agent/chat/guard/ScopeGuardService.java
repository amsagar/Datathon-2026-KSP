package com.ksp.agent.chat.guard;

import com.ksp.agent.chat.usage.LlmUsageContext;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Pre-flight scope check: decides whether a user message is within the selected assistant's role
 * before it reaches the main chat model. Out-of-scope messages are blocked with a short redirect.
 */
public interface ScopeGuardService {

    /** Outcome of a scope check. When {@code allowed} is false, {@code redirect} is shown to the user. */
    record Decision(boolean allowed, String redirect) {
        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision block(String redirect) {
            return new Decision(false, redirect);
        }
    }

    /**
     * @param assistantRole  the assistant's system prompt (its defined role/scope)
     * @param toolSummaries  one entry per available tool ("name — description"); may be empty
     * @param documentNames  names of the assistant's enabled RAG documents; questions answerable from
     *                       these are treated as in-scope. May be empty.
     * @param recentHistory  a short tail of prior conversation for follow-up context; may be empty
     * @param userMessage    the user's latest message
     * @param lang           UI language code ("en"/"kn"); the redirect shown on a BLOCK is written in
     *                       this language rather than always English, since it is persisted into
     *                       chat memory like any other assistant reply
     * @return a {@link Decision}; always {@code allowed} when the guard is disabled or the role is blank,
     *         and fails open (allowed) if the classifier call errors.
     */
    Decision check(String assistantRole, List<String> toolSummaries, List<String> documentNames,
                   List<Message> recentHistory, String userMessage, LlmUsageContext usageContext, String lang);
}
