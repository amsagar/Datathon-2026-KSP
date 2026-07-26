package com.ksp.agent.chat.memory;

import com.ksp.agent.chat.audit.ChatAuditLog;
import com.ksp.agent.chat.entity.ChatSessionSummary;
import com.ksp.agent.chat.repo.ChatSessionSummaryRepository;
import com.ksp.agent.chat.repo.ChatTranscriptRepository;
import com.ksp.agent.chat.usage.LlmUsageContext;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link ChatMemory} that preserves long-conversation context via summarization instead of the
 * lossy sliding window of {@code MessageWindowChatMemory}.
 *
 * <p>The full transcript is always persisted (so the UI can render every turn). The view returned to
 * the model from {@link #get(String)} is compacted: a running summary of aged-out turns followed by
 * the most recent turns verbatim, capped to a configurable character budget. Summarization itself
 * runs asynchronously in {@link ConversationSummaryService} so no turn pays an extra model
 * round-trip.
 *
 * <p>Writes are append-only: Spring AI's JDBC {@code saveAll} deletes and re-inserts the whole
 * conversation on every add (O(n²) row writes over a session), so new messages are appended
 * directly via {@link ChatTranscriptRepository} in the same wire format instead.
 */
public class SummarizingChatMemory implements ChatMemory {

    private static final String SUMMARY_PREFIX = "[Summary of earlier conversation]\n";

    private final ChatMemoryRepository transcriptRepository;
    private final ChatTranscriptRepository transcriptAppendRepository;
    private final ChatSessionSummaryRepository summaryRepository;
    private final ConversationSummaryService summaryService;
    private final int recentWindow;
    private final int summaryThreshold;
    private final int charBudget;

    public SummarizingChatMemory(ChatMemoryRepository transcriptRepository,
                                 ChatTranscriptRepository transcriptAppendRepository,
                                 ChatSessionSummaryRepository summaryRepository,
                                 ConversationSummaryService summaryService,
                                 int recentWindow,
                                 int summaryThreshold,
                                 int charBudget) {
        this.transcriptRepository = transcriptRepository;
        this.transcriptAppendRepository = transcriptAppendRepository;
        this.summaryRepository = summaryRepository;
        this.summaryService = summaryService;
        this.recentWindow = recentWindow;
        this.summaryThreshold = summaryThreshold;
        this.charBudget = charBudget;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        // Append ONLY the new messages — never rewrite the transcript. Consecutive-duplicate user
        // messages are still skipped (same rule as before), using the last persisted row instead of
        // loading the whole transcript. Nothing is ever trimmed here — the transcript stays complete.
        ChatTranscriptRepository.LastMessage last =
                transcriptAppendRepository.findLast(conversationId).orElse(null);
        String lastType = last == null ? null : last.type();
        String lastText = last == null ? null : last.content();
        List<Message> toAppend = new ArrayList<>(messages.size());
        for (Message incoming : messages) {
            if (incoming instanceof UserMessage user
                    && MessageType.USER.name().equals(lastType)
                    && user.getText() != null && user.getText().equals(lastText)) {
                continue;
            }
            // Never persist a blank assistant message: the tool-loop's terminal empty-text response
            // (and any intermediate filler) carries no content and would surface as an empty or
            // duplicate assistant bubble when the transcript is reloaded.
            if (incoming.getMessageType() == MessageType.ASSISTANT
                    && (incoming.getText() == null || incoming.getText().isBlank())) {
                continue;
            }
            toAppend.add(incoming);
            lastType = incoming.getMessageType().name();
            lastText = incoming.getText();
        }
        if (!toAppend.isEmpty()) {
            transcriptAppendRepository.append(conversationId, toAppend);
        }
        maybeSummarize(conversationId, (int) transcriptAppendRepository.countMessages(conversationId));
    }

    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        List<Message> transcript = transcriptRepository.findByConversationId(conversationId);
        int size = transcript.size();

        ChatSessionSummary summary = summaryRepository.findBySession(conversationId).orElse(null);
        if (summary != null && summary.getSummary() != null && !summary.getSummary().isBlank()) {
            // Everything before `summarizedThrough` is captured by the summary; everything from there
            // on is sent verbatim. Guarantees no message is dropped: it is either summarized or recent.
            int from = Math.max(0, Math.min(summary.getSummarizedThroughCount(), size));
            List<Message> result = new ArrayList<>(size - from + 1);
            result.add(new UserMessage(SUMMARY_PREFIX + summary.getSummary()));
            result.addAll(transcript.subList(from, size));
            return applyCharBudget(result, true);
        }

        // No summary yet. While the transcript is still small, send it whole. If it has already grown
        // past the threshold (the async summary has not landed yet), fall back to the last
        // `summaryThreshold` messages as a transient safety bound.
        if (size <= summaryThreshold) {
            return applyCharBudget(transcript, false);
        }
        return applyCharBudget(
                new ArrayList<>(transcript.subList(size - summaryThreshold, size)), false);
    }

    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        transcriptRepository.deleteByConversationId(conversationId);
        summaryRepository.deleteBySession(conversationId);
    }

    /**
     * Cap the model-facing history to {@code charBudget} total characters, dropping the OLDEST
     * non-summary messages first. The summary (when present) and the most recent message are always
     * kept. Disabled when the budget is {@code <= 0}.
     */
    private List<Message> applyCharBudget(List<Message> messages, boolean firstIsSummary) {
        if (charBudget <= 0 || messages.size() <= 1) {
            return messages;
        }
        long total = 0;
        for (Message m : messages) {
            total += m.getText() == null ? 0 : m.getText().length();
        }
        if (total <= charBudget) {
            return messages;
        }
        List<Message> result = messages instanceof ArrayList<Message> list
                ? list : new ArrayList<>(messages);
        int trimFrom = firstIsSummary ? 1 : 0;
        while (total > charBudget && result.size() - trimFrom > 1) {
            Message removed = result.remove(trimFrom);
            total -= removed.getText() == null ? 0 : removed.getText().length();
        }
        return result;
    }

    private void maybeSummarize(String conversationId, int transcriptSize) {
        if (transcriptSize <= summaryThreshold) {
            return;
        }
        int summarizedThrough = summaryRepository.findBySession(conversationId)
                .map(ChatSessionSummary::getSummarizedThroughCount)
                .orElse(0);
        // Only summarize once there are more un-summarized messages than the verbatim window.
        if (transcriptSize - summarizedThrough > recentWindow) {
            String requestId = ChatAuditLog.requestIdFromMdc();
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            String assistantId = ChatAuditLog.assistantIdFromMdc();
            String userId = ChatAuditLog.userIdFromMdc();
            summaryService.summarizeAsync(new LlmUsageContext(requestId, conversationId, assistantId, userId));
        }
    }
}
