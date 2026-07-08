package org.zhiyuan.demo01.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingConfiguration {

    /**
     * 指定默认的 EmbeddingModel。
     *
     * <p>当前项目同时引入了 OpenAI 和 Ollama 两个 Spring AI starter，
     * Spring Boot 会自动创建 openAiEmbeddingModel 和 ollamaEmbeddingModel 两个 Bean。
     * RedisVectorStore 自动配置只需要一个 EmbeddingModel，如果不指定主 Bean，
     * 启动时会因为无法判断使用哪一个模型而报错。</p>
     *
     * <p>这里明确把 Ollama Embedding 作为默认向量模型，只影响 VectorStore、RAG
     * 等需要 EmbeddingModel 的组件，不影响 ChatClient 里的 OpenAI/Ollama 聊天模型路由。</p>
     */
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(@Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        return embeddingModel;
    }
}
