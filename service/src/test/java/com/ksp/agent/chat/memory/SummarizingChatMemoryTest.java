package com.ksp.agent.chat.memory;

import com.ksp.agent.chat.entity.ChatSessionSummary;
import com.ksp.agent.chat.repo.ChatSessionSummaryRepository;
import com.ksp.agent.chat.repo.ChatTranscriptRepository;
import com.ksp.agent.chat.usage.LlmUsageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummarizingChatMemoryTest {

    private static final String ID = "session-1";
    private static final int RECENT_WINDOW = 2;
    private static final int THRESHOLD = 4;
    private static final int CHAR_BUDGET = 24000;

    private ChatMemoryRepository transcriptRepository;
    private ChatTranscriptRepository transcriptAppendRepository;
    private ChatSessionSummaryRepository summaryRepository;
    private ConversationSummaryService summaryService;
    private SummarizingChatMemory memory;

    @BeforeEach
    void setUp() {
        transcriptRepository = mock(ChatMemoryRepository.class);
        transcriptAppendRepository = mock(ChatTranscriptRepository.class);
        summaryRepository = mock(ChatSessionSummaryRepository.class);
        summaryService = mock(ConversationSummaryService.class);
        memory = new SummarizingChatMemory(transcriptRepository, transcriptAppendRepository,
                summaryRepository, summaryService, RECENT_WINDOW, THRESHOLD, CHAR_BUDGET);
    }

    private static List<Message> transcript(int n) {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            msgs.add(i % 2 == 0 ? new UserMessage("u" + i) : new AssistantMessage("a" + i));
        }
        return msgs;
    }

    @Test
    void getReturnsFullTranscriptWhenSmallAndNoSummary() {
        List<Message> t = transcript(3);
        when(transcriptRepository.findByConversationId(ID)).thenReturn(t);
        when(summaryRepository.findBySession(ID)).thenReturn(Optional.empty());

        assertThat(memory.get(ID)).containsExactlyElementsOf(t);
    }

    @Test
    void getCompactsToSummaryPlusRecentWhenSummaryPresent() {
        List<Message> t = transcript(6); // u0,a1,u2,a3,u4,a5
        when(transcriptRepository.findByConversationId(ID)).thenReturn(t);
        when(summaryRepository.findBySession(ID))
                .thenReturn(Optional.of(new ChatSessionSummary(ID, "earlier stuff", 4, 0L)));

        List<Message> ctx = memory.get(ID);

        // [summary] + transcript[4..6] => 3 messages, summary first, then the last two verbatim.
        assertThat(ctx).hasSize(3);
        assertThat(ctx.get(0)).isInstanceOf(UserMessage.class);
        assertThat(ctx.get(0).getText()).startsWith("[Summary of earlier conversation]");
        assertThat(ctx.get(0).getText()).contains("earlier stuff");
        assertThat(ctx.get(1).getText()).isEqualTo("u4");
        assertThat(ctx.get(2).getText()).isEqualTo("a5");
    }

    @Test
    void getFallsBackToLastThresholdWhenLargeButNoSummaryYet() {
        List<Message> t = transcript(7); // async summary hasn't landed yet
        when(transcriptRepository.findByConversationId(ID)).thenReturn(t);
        when(summaryRepository.findBySession(ID)).thenReturn(Optional.empty());

        List<Message> ctx = memory.get(ID);

        assertThat(ctx).hasSize(THRESHOLD);
        assertThat(ctx.get(0).getText()).isEqualTo("a3"); // last 4: indices 3..6
        assertThat(ctx.get(THRESHOLD - 1).getText()).isEqualTo("u6");
    }

    @Test
    void getTrimsOldestNonSummaryMessagesOverCharBudget() {
        // Tight budget: only the summary + the newest message fit.
        memory = new SummarizingChatMemory(transcriptRepository, transcriptAppendRepository,
                summaryRepository, summaryService, RECENT_WINDOW, THRESHOLD, 30);
        List<Message> t = List.of(
                new UserMessage("u-old-........................."),   // 27 chars
                new AssistantMessage("a-old-...................."),   // 26 chars
                new UserMessage("u-new"));
        when(transcriptRepository.findByConversationId(ID)).thenReturn(new ArrayList<>(t));
        when(summaryRepository.findBySession(ID))
                .thenReturn(Optional.of(new ChatSessionSummary(ID, "sum", 0, 0L)));

        List<Message> ctx = memory.get(ID);

        assertThat(ctx.get(0).getText()).startsWith("[Summary of earlier conversation]");
        assertThat(ctx.get(ctx.size() - 1).getText()).isEqualTo("u-new"); // newest always kept
        assertThat(ctx).noneMatch(m -> "a-old-....................".equals(m.getText())
                || "u-old-.........................".equals(m.getText()));
    }

    @Test
    void addAppendsOnlyNewMessagesAndDoesNotRewriteTranscript() {
        when(transcriptAppendRepository.findLast(ID)).thenReturn(
                Optional.of(new ChatTranscriptRepository.LastMessage("a1", "ASSISTANT")));
        when(transcriptAppendRepository.countMessages(ID)).thenReturn(3L);
        when(summaryRepository.findBySession(ID)).thenReturn(Optional.empty());

        memory.add(ID, List.of(new UserMessage("new")));

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(transcriptAppendRepository).append(eq(ID), captor.capture());
        assertThat(captor.getValue()).hasSize(1); // ONLY the new message — never a full rewrite
        verify(transcriptRepository, never()).saveAll(eq(ID), anyList());
    }

    @Test
    void addSkipsConsecutiveDuplicateUserMessage() {
        when(transcriptAppendRepository.findLast(ID)).thenReturn(
                Optional.of(new ChatTranscriptRepository.LastMessage("same", "USER")));
        when(transcriptAppendRepository.countMessages(ID)).thenReturn(1L);
        when(summaryRepository.findBySession(ID)).thenReturn(Optional.empty());

        memory.add(ID, List.of(new UserMessage("same")));

        verify(transcriptAppendRepository, never()).append(eq(ID), anyList());
    }

    @Test
    void addTriggersSummarizationOnceUnsummarizedExceedsWindow() {
        // 5 messages, threshold 4, recent window 2, nothing summarized yet => 5 - 0 > 2 => summarize.
        when(transcriptAppendRepository.findLast(ID)).thenReturn(
                Optional.of(new ChatTranscriptRepository.LastMessage("a3", "ASSISTANT")));
        when(transcriptAppendRepository.countMessages(ID)).thenReturn(5L);
        when(summaryRepository.findBySession(ID)).thenReturn(Optional.empty());

        memory.add(ID, List.of(new UserMessage("fifth")));

        verify(summaryService, times(1)).summarizeAsync(argThat(ctx ->
                ctx != null && ID.equals(ctx.sessionId())));
    }

    @Test
    void addDoesNotTriggerSummarizationBelowThreshold() {
        when(transcriptAppendRepository.findLast(ID)).thenReturn(
                Optional.of(new ChatTranscriptRepository.LastMessage("a1", "ASSISTANT")));
        when(transcriptAppendRepository.countMessages(ID)).thenReturn(3L);
        when(summaryRepository.findBySession(ID)).thenReturn(Optional.empty());

        memory.add(ID, List.of(new UserMessage("third")));

        verify(summaryService, never()).summarizeAsync(any(LlmUsageContext.class));
    }

    @Test
    void clearRemovesTranscriptAndSummary() {
        memory.clear(ID);
        verify(transcriptRepository).deleteByConversationId(ID);
        verify(summaryRepository).deleteBySession(ID);
    }
}
