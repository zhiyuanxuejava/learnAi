package org.zhiyuan.demo01.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
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
 * 这个类不是业务类，而是 Bean 配置类。
 *
 * 这里统一声明并管理：
 * 1. 聊天记忆组件 Bean
 * 2. 日志 Advisor Bean
 * 3. 默认系统提示词 Bean
 * 4. 不同模型、不同记忆策略下的 ChatClient Bean
 *
 * 当前一共定义了四个 ChatClient：
 * 1. OpenAI + 带记忆
 * 2. OpenAI + 不带记忆
 * 3. Ollama + 带记忆
 * 4. Ollama + 不带记忆
 *
 * Spring 启动时会识别当前配置类中的 @Bean 方法，
 * 并由容器负责调用这些方法，创建对应的 Bean 放入 IOC 容器中统一管理。
 *
 * 所以像 openAiChatClientWithoutMemory(...) 这类方法，
 * 虽然没有在业务代码中手动调用，
 * 但 Spring 会在创建 Bean 时自动解析方法参数并完成依赖注入。
 */
@Configuration
public class ChatClientConfiguration {

    /**
     * 默认系统提示词文件位置。
     */
    private static final String DEFAULT_SYSTEM_PROMPT_LOCATION = "classpath:/prompts/default-system-prompt.txt";

    /**
     * 聊天记忆组件 配置
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
     * 自定义的 Advisor，用于记录 模型调用日志
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
     *                              <p>
     *                              OpenAiChatModel model = 从容器里拿;
     *                              ChatMemory chatMemory = 从容器里拿;
     *                              ChatRequestLogAdvisor advisor = 从容器里拿;
     *                              String prompt = 从容器里拿名为 defaultSystemPrompt 的 Bean;
     *                              Spring 会根据名称自动注入参数。
     * @return ChatClient Bean
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model,
                                       ChatMemory chatMemory,
                                       ChatRequestLogAdvisor chatRequestLogAdvisor,
                                       @Qualifier("defaultSystemPrompt") String defaultSystemPrompt) {
        return buildChatClient(model, chatMemory, chatRequestLogAdvisor, defaultSystemPrompt, true);
    }

    /**
     * 不带会话记忆的 OpenAI ChatClient。
     * 这里主要给“严格测试当前轮 Prompt 效果”的场景使用，避免历史消息干扰结果。
     *
     * @param model                 OpenAI 聊天模型
     * @param chatMemory            聊天记忆
     * @param chatRequestLogAdvisor 日志 Advisor
     * @param defaultSystemPrompt   默认系统提示词
     * @return ChatClient Bean
     */
    @Bean("openAiChatClientWithoutMemory")
    public ChatClient openAiChatClientWithoutMemory(OpenAiChatModel model,
                                                    ChatMemory chatMemory,
                                                    ChatRequestLogAdvisor chatRequestLogAdvisor,
                                                    @Qualifier("defaultSystemPrompt") String defaultSystemPrompt) {
        return buildChatClient(model, chatMemory, chatRequestLogAdvisor, defaultSystemPrompt, false);
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
        return buildChatClient(model, chatMemory, chatRequestLogAdvisor, defaultSystemPrompt, true);
    }

    /**
     * 不带会话记忆的 Ollama ChatClient。
     * 这里主要给“严格测试当前轮 Prompt 效果”的场景使用，避免历史消息干扰结果。
     *
     * @param model                 Ollama 聊天模型
     * @param chatMemory            聊天记忆
     * @param chatRequestLogAdvisor 日志 Advisor
     * @param defaultSystemPrompt   默认系统提示词
     * @return ChatClient Bean
     */
    @Bean("ollamaChatClientWithoutMemory")
    public ChatClient ollamaChatClientWithoutMemory(OllamaChatModel model,
                                                    ChatMemory chatMemory,
                                                    ChatRequestLogAdvisor chatRequestLogAdvisor,
                                                    @Qualifier("defaultSystemPrompt") String defaultSystemPrompt) {
        return buildChatClient(model, chatMemory, chatRequestLogAdvisor, defaultSystemPrompt, false);
    }

    /**
     * 构建统一风格的 ChatClient。
     * 这里集中挂载默认系统提示词和默认 Advisor，避免不同模型配置风格不一致。
     * 同时允许按需决定是否启用会话记忆，让“调试当前轮 Prompt”和“多轮对话”两类场景都能明确区分。
     *
     * @param model                 聊天模型
     * @param chatMemory            聊天记忆
     * @param chatRequestLogAdvisor 日志 Advisor
     * @param defaultSystemPrompt   默认系统提示词
     * @param enableMemory          是否启用会话记忆
     * @return 组装完成的 ChatClient
     */
    private ChatClient buildChatClient(ChatModel model,
                                       ChatMemory chatMemory,
                                       ChatRequestLogAdvisor chatRequestLogAdvisor,
                                       String defaultSystemPrompt,
                                       boolean enableMemory) {
        Advisor[] advisors = enableMemory
                ? new Advisor[]{
                chatRequestLogAdvisor,
                MessageChatMemoryAdvisor.builder(chatMemory).build()
        }
                : new Advisor[]{chatRequestLogAdvisor};

        return ChatClient.builder(model)
                .defaultSystem(defaultSystemPrompt)
                .defaultAdvisors(advisors)
                .build();
    }
}
