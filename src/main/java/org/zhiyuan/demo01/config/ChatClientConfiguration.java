package org.zhiyuan.demo01.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.zhiyuan.demo01.advisor.ChatRequestLogAdvisor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * ChatClient 相关配置类。
 * 这里统一管理默认系统提示词、聊天记忆，以及不同模型对应的 ChatClient Bean。
 */
@Configuration
public class ChatClientConfiguration {

    /**
     * 默认系统提示词文件位置。
     */
    private static final String DEFAULT_SYSTEM_PROMPT_LOCATION = "classpath:/prompts/default-system-prompt.txt";

    /**
     * 聊天记忆组件。
     * 当前示例先使用内存仓库，后续如果接入生产环境，可以替换为 Redis、JDBC 等持久化仓库。
     *
     * @return 聊天记忆对象
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    /**
     * ChatClient 调用日志 Advisor。
     *
     * @return 日志 Advisor Bean
     */
    @Bean
    public ChatRequestLogAdvisor chatRequestLogAdvisor() {
        return new ChatRequestLogAdvisor();
    }

    /**
     * 读取默认系统提示词。
     * 这里把长提示词放到资源文件中，避免配置类本身被大段文本淹没。
     *
     * @param resource 提示词资源
     * @return 默认系统提示词内容
     */
    @Bean("defaultSystemPrompt")
    public String defaultSystemPrompt(@Value(DEFAULT_SYSTEM_PROMPT_LOCATION) Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取默认系统提示词失败: " + DEFAULT_SYSTEM_PROMPT_LOCATION, ex);
        }
    }

    /**
     * OpenAI ChatClient。
     *
     * @param model                 OpenAI 聊天模型
     * @param chatMemory            聊天记忆
     * @param chatRequestLogAdvisor 日志 Advisor
     * @param defaultSystemPrompt   默认系统提示词
     * @return ChatClient Bean
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model,
                                       ChatMemory chatMemory,
                                       ChatRequestLogAdvisor chatRequestLogAdvisor,
                                       @Qualifier("defaultSystemPrompt") String defaultSystemPrompt) {
        return buildChatClient(model, chatMemory, chatRequestLogAdvisor, defaultSystemPrompt);
    }

    /**
     * Ollama ChatClient。
     *
     * @param model                 Ollama 聊天模型
     * @param chatMemory            聊天记忆
     * @param chatRequestLogAdvisor 日志 Advisor
     * @param defaultSystemPrompt   默认系统提示词
     * @return ChatClient Bean
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel model,
                                       ChatMemory chatMemory,
                                       ChatRequestLogAdvisor chatRequestLogAdvisor,
                                       @Qualifier("defaultSystemPrompt") String defaultSystemPrompt) {
        return buildChatClient(model, chatMemory, chatRequestLogAdvisor, defaultSystemPrompt);
    }

    /**
     * 构建统一风格的 ChatClient。
     * 这里集中挂载默认系统提示词和默认 Advisor，避免不同模型配置风格不一致。
     *
     * @param model                 聊天模型
     * @param chatMemory            聊天记忆
     * @param chatRequestLogAdvisor 日志 Advisor
     * @param defaultSystemPrompt   默认系统提示词
     * @return 组装完成的 ChatClient
     */
    private ChatClient buildChatClient(ChatModel model,
                                       ChatMemory chatMemory,
                                       ChatRequestLogAdvisor chatRequestLogAdvisor,
                                       String defaultSystemPrompt) {
        return ChatClient
                .builder(model)
                .defaultSystem(defaultSystemPrompt)
                .defaultAdvisors(
                        chatRequestLogAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
