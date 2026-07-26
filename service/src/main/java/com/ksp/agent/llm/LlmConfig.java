package com.ksp.agent.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LlmProperties}. The actual {@link org.springframework.ai.chat.model.ChatModel}
 * bean is {@link QuickMlChatModel} (a {@code @Component}), which builds QuickML's request and
 * parses its non-OpenAI response shape directly — see its Javadoc for why Spring AI's built-in
 * OpenAI-compatible client can't be reused here.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {
}
