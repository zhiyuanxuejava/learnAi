package org.zhiyuan.demo01.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zhiyuan.demo01.model.ai.AiProviderType;

/**
 * ChatClient 工厂类。
 * 这里根据外部传入的 provider 编码，返回对应的 ChatClient Bean。
 */
@Service
public class ChatClientFactory {

    private final ChatClient openAiChatClient;
    private final ChatClient ollamaChatClient;

    public ChatClientFactory(@Qualifier("openAiChatClient") ChatClient openAiChatClient,
                             @Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.openAiChatClient = openAiChatClient;
        this.ollamaChatClient = ollamaChatClient;
    }

    /**
     * 获取指定 provider 对应的 ChatClient。
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
}
