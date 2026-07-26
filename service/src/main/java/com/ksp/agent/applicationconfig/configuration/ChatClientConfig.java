package com.ksp.agent.applicationconfig.configuration;

import com.ksp.agent.chat.memory.ConversationSummaryService;
import com.ksp.agent.chat.memory.SummarizingChatMemory;
import com.ksp.agent.chat.repo.ChatSessionSummaryRepository;
import com.ksp.agent.chat.repo.ChatTranscriptRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Chat clients built on the single in-house {@link ChatModel} (see
 * {@code com.ksp.agent.llm.LlmConfig}). There is no per-user provider registry: every client
 * uses the one configured model, with a distinct system prompt / advisor set per role.
 *
 * <p>The auxiliary clients (title, summary, scope guard, fact extraction) carry per-call
 * {@link ChatOptions} with tight {@code maxTokens} caps so these system calls never burn the main
 * chat's 4096-token output budget on a 6-word title or a one-line verdict.
 */
@Configuration
public class ChatClientConfig {

    /**
     * Summarizing memory: keeps the full transcript for the UI, but feeds the model a compacted
     * view (running summary of older turns + recent turns verbatim, capped to a character budget)
     * so long conversations keep their early context instead of having it silently dropped by a
     * sliding window.
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository,
                                 ChatTranscriptRepository chatTranscriptRepository,
                                 ChatSessionSummaryRepository summaryRepository,
                                 ConversationSummaryService summaryService,
                                 @Value("${agent.memory.recent-window:20}") int recentWindow,
                                 @Value("${agent.memory.summary-threshold:40}") int summaryThreshold,
                                 @Value("${agent.memory.char-budget:24000}") int charBudget) {
        return new SummarizingChatMemory(chatMemoryRepository, chatTranscriptRepository,
                summaryRepository, summaryService, recentWindow, summaryThreshold, charBudget);
    }

    /**
     * Bare chat client: only the conversation-memory advisor is baked in. The system
     * prompt and tool set are supplied per request from the selected assistant.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    @Qualifier("titleChatClient")
    public ChatClient titleChatClient(ChatModel chatModel,
                                      @Value("classpath:prompts/title-generator-system.md") Resource titlePrompt) {
        // Kannada titles need more tokens than English for the same 3–6 words.
        return ChatClient.builder(chatModel)
                .defaultSystem(titlePrompt)
                .defaultOptions(ChatOptions.builder().maxTokens(48).temperature(0.0).build())
                .build();
    }

    /**
     * Stateless client used to summarize aged-out conversation turns. No memory advisor — each
     * summarization is a self-contained call (prior summary + new messages in, updated summary out).
     */
    @Bean
    @Qualifier("summaryChatClient")
    public ChatClient summaryChatClient(ChatModel chatModel,
                                        @Value("classpath:prompts/summary-system.md") Resource summaryPrompt) {
        return ChatClient.builder(chatModel)
                .defaultSystem(summaryPrompt)
                .defaultOptions(ChatOptions.builder().maxTokens(512).build())
                .build();
    }

    /**
     * Stateless classifier used by the scope guard to decide whether a user message is in scope for
     * the selected assistant. No memory advisor — each classification is independent. The output
     * contract is one line (`ALLOW` or `BLOCK: <one sentence>`), so 64 tokens covers the longest
     * legal verdict + redirect sentence without truncating it.
     */
    @Bean
    @Qualifier("scopeGuardChatClient")
    public ChatClient scopeGuardChatClient(ChatModel chatModel,
                                           @Value("classpath:prompts/scope-guard-system.md") Resource scopeGuardPrompt) {
        return ChatClient.builder(chatModel)
                .defaultSystem(scopeGuardPrompt)
                // 64 was tuned for an English-only redirect sentence; Kannada (non-Latin script,
                // sparse in most tokenizer vocabularies) needs materially more tokens per
                // character for the same sentence, so a Kannada BLOCK redirect was getting cut off.
                .defaultOptions(ChatOptions.builder().maxTokens(160).temperature(0.0).build())
                .build();
    }

    /**
     * Stateless extractor used by consolidation to mine durable facts from aged-out conversation
     * turns into long-term semantic memory. No memory advisor — each extraction is self-contained
     * (a slice of turns in, a JSON array of facts out).
     */
    @Bean
    @Qualifier("factExtractionChatClient")
    public ChatClient factExtractionChatClient(ChatModel chatModel,
                                               @Value("classpath:prompts/fact-extraction-system.md") Resource factExtractionPrompt) {
        return ChatClient.builder(chatModel)
                .defaultSystem(factExtractionPrompt)
                .defaultOptions(ChatOptions.builder().maxTokens(512).build())
                .build();
    }

    /**
     * Stateless client that generates short starter-prompt suggestions for the empty chat screen from
     * an assistant's own name + system prompt (and, when personalizing, a user's memories). No memory
     * advisor — each call is self-contained. A slightly warm temperature keeps repeated regenerations
     * varied. 256 tokens looked like it covered "a small JSON array of one-line questions" for English,
     * but Kannada script needs far more tokens per character (complex conjuncts split into several BPE
     * tokens each), so real Kannada generations were silently truncated mid-array — the model ran out
     * of budget before the closing bracket, producing cut-off words and a dangling "[" that no amount
     * of response parsing can recover, since the data was simply never generated. 768 gives real
     * headroom for 6 Kannada questions plus JSON/markdown-fence overhead.
     */
    @Bean
    @Qualifier("suggestionsChatClient")
    public ChatClient suggestionsChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder().maxTokens(768).temperature(0.7).build())
                .build();
    }
}
