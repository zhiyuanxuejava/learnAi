package org.zhiyuan.demo01.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zhiyuan.demo01.model.ai.AiProviderType;

/**
 * ChatClient 工厂类。
 * 这里不负责创建 ChatClient，而是负责根据上游传入的条件，
 * 从 Spring 容器中选择并返回对应的 ChatClient Bean。
 *
 * 当前主要根据以下两个条件进行路由：
 * 1. providerCode：使用哪个模型提供方，例如 openai、ollama
 * 2. memoryEnabled：是否启用会话记忆
 */
@Service
public class ChatClientFactory {

    private final ChatClient openAiChatClient;
    private final ChatClient openAiChatClientWithoutMemory;
    private final ChatClient ollamaChatClient;
    private final ChatClient ollamaChatClientWithoutMemory;


    /**
     * 构造函数，注入 ChatClient Bean。
     * @param openAiChatClient
     * @param openAiChatClientWithoutMemory
     * @param ollamaChatClient
     * @param ollamaChatClientWithoutMemory
     */
    public ChatClientFactory(@Qualifier("openAiChatClient") ChatClient openAiChatClient,
                             @Qualifier("openAiChatClientWithoutMemory") ChatClient openAiChatClientWithoutMemory,
                             @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
                             @Qualifier("ollamaChatClientWithoutMemory") ChatClient ollamaChatClientWithoutMemory) {
        this.openAiChatClient = openAiChatClient;
        this.openAiChatClientWithoutMemory = openAiChatClientWithoutMemory;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaChatClientWithoutMemory = ollamaChatClientWithoutMemory;
    }

    /**
     * 获取指定 provider 对应的 ChatClient。
     * 默认使用带记忆的 ChatClient
     *
     * @param providerCode provider 编码，例如 openai、ollama
     * @return 匹配到的 ChatClient
     */
    public ChatClient getChatClient(String providerCode) {
        AiProviderType providerType = AiProviderType.fromCode(providerCode);
        return switch (providerType) {
            case OPENAI -> openAiChatClient;
            case OLLAMA -> ollamaChatClient;
        };
    }

    /**
     * 获取指定 provider 对应的 ChatClient，并明确指定是否启用会话记忆。
     * 当需要严格测试当前轮 Prompt 时，应选择不带记忆的客户端，避免历史消息干扰结果。
     *
     * @param providerCode provider 编码，例如 openai、ollama
     * @param serverMemoryEnabled 是否启用服务端会话记忆
     * @return 匹配到的 ChatClient
     */
    public ChatClient getChatClient(String providerCode, boolean serverMemoryEnabled) {
        //启用服务端会话记忆
        if (serverMemoryEnabled) {
            return getChatClient(providerCode);
        }

        //不启用服务端会话记忆，就返回 ChatClientWithoutMemory
        AiProviderType providerType = AiProviderType.fromCode(providerCode);
        return switch (providerType) {
            case OPENAI -> openAiChatClientWithoutMemory;
            case OLLAMA -> ollamaChatClientWithoutMemory;
        };
    }
}
