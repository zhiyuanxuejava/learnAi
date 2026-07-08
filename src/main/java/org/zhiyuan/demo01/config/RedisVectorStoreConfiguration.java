package org.zhiyuan.demo01.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.util.StringUtils;
import org.zhiyuan.demo01.store.rag.RagVectorMetadataSchema;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

/**
 * Redis 向量存储配置类。
 * 这里负责创建 RedisVectorStore 和底层 RedisClient。
 */
@Configuration
public class RedisVectorStoreConfiguration {

    /**
     * 创建 Redis 向量存储
     *Spring AI 的默认 RedisVectorStore 自动配置就会退让，不会再创建第二个同类型 Bean。
     * 手动配置的主要目的，是增加默认自动配置不方便声明的字段：
     * @param embeddingModel 向量模型
     * @param properties Redis 向量存储配置
     * @param redisClient Redis 客户端
     * @param observationRegistryProvider 观测注册中心
     * @param observationConventionProvider 观测规则提供者
     * @param batchingStrategy 批处理策略
     * @return Redis 向量存储
     */
    @Bean
    public RedisVectorStore vectorStore(EmbeddingModel embeddingModel,
                                        RedisVectorStoreProperties properties,
                                        RedisClient redisClient,
                                        ObjectProvider<ObservationRegistry> observationRegistryProvider,
                                        ObjectProvider<VectorStoreObservationConvention> observationConventionProvider,
                                        BatchingStrategy batchingStrategy) {

        // 补充可过滤的 metadata 字段，便于后续按文件或内容版本做过滤查询。
        // 如果当前 Redis 索引是旧版本且没有这些字段，需要先重建索引或做版本迁移。
        RedisVectorStore.Builder builder = RedisVectorStore.builder(redisClient, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                //给 RedisVectorStore 开启可观测性。 不会改变检索结果，只影响： 指标采集； 链路追踪； 监控统计。
                .observationRegistry(observationRegistryProvider.getIfUnique(() -> ObservationRegistry.NOOP))
                //配置自定义观测标签规则。
                .customObservationConvention(observationConventionProvider.getIfAvailable())
                //调用 EmbeddingModel 前，应该如何把文档拆成批次。
                .batchingStrategy(batchingStrategy)
                //指定 Redis Search 索引名称。  这个是配置文件配置的
                .indexName(properties.getIndexName())
                //指定向量文档在 Redis 中的 Key 前缀。 也是配置文件配置的
                .prefix(properties.getPrefix())
                .metadataFields(
                        RedisVectorStore.MetadataField.tag(RagVectorMetadataSchema.DOCUMENT_ID),
                        RedisVectorStore.MetadataField.tag(RagVectorMetadataSchema.CONTENT_HASH),
                        RedisVectorStore.MetadataField.numeric(RagVectorMetadataSchema.CHUNK_INDEX)
                );

        if (properties.getHnsw().getM() != null) {
            builder.hnswM(properties.getHnsw().getM());
        }
        if (properties.getHnsw().getEfConstruction() != null) {
            builder.hnswEfConstruction(properties.getHnsw().getEfConstruction());
        }
        if (properties.getHnsw().getEfRuntime() != null) {
            builder.hnswEfRuntime(properties.getHnsw().getEfRuntime());
        }

        return builder.build();
    }

    /**
     * 创建 Redis 客户端
     *
     * @param jedisConnectionFactory Redis 连接工厂
     * @return Redis 客户端
     */
    @Bean(destroyMethod = "close")
    public RedisClient redisClient(JedisConnectionFactory jedisConnectionFactory) {
        // RedisClient 交给 Spring 管理生命周期，容器关闭时自动释放连接资源
        // 复用 Spring Boot 已解析的 Redis 连接参数
        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                .ssl(jedisConnectionFactory.isUseSsl())
                .timeoutMillis(jedisConnectionFactory.getTimeout());

        if (StringUtils.hasText(jedisConnectionFactory.getClientName())) {
            configBuilder.clientName(jedisConnectionFactory.getClientName());
        }
        if (StringUtils.hasText(jedisConnectionFactory.getPassword())) {
            configBuilder.password(jedisConnectionFactory.getPassword());
        }

        return RedisClient.builder()
                .hostAndPort(jedisConnectionFactory.getHostName(), jedisConnectionFactory.getPort())
                .clientConfig(configBuilder.build())
                .build();
    }
}
