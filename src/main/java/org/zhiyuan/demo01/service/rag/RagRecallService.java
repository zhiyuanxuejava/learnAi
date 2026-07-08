package org.zhiyuan.demo01.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.zhiyuan.demo01.dto.rag.RagRecallHit;
import org.zhiyuan.demo01.dto.rag.RagRecallResponse;
import org.zhiyuan.demo01.exception.BadRequestException;
import org.zhiyuan.demo01.exception.ProcessingException;
import org.zhiyuan.demo01.service.rag.model.RagChunkLocation;
import org.zhiyuan.demo01.store.rag.RagRedisSchema;
import org.zhiyuan.demo01.store.rag.RagVectorMetadataSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG 召回服务。
 * 这里负责执行向量检索，并把召回结果组装成前端更容易观察的结构。
 */
@Service
public class RagRecallService {

    /**
     * 向量存储实现。
     */
    private final VectorStore vectorStore;

    /**
     * Redis 操作模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 RAG 召回服务。
     *
     * @param vectorStore 向量存储实现
     * @param stringRedisTemplate Redis 操作模板
     */
    public RagRecallService(VectorStore vectorStore, StringRedisTemplate stringRedisTemplate) {
        this.vectorStore = vectorStore;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 执行 RAG 召回测试。
     * 返回结果里会包含命中块、前后相邻块，以及来源信息。
     *
     * @param query 查询内容
     * @param topK 召回数量
     * @return 召回结果
     */
    public RagRecallResponse recall(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("查询内容不能为空");
        }

        int finalTopK = Math.max(1, Math.min(topK, 20));
        long startTime = System.nanoTime();

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query.trim())
                        .topK(finalTopK)
                        .build()
        );

        List<Document> sortedDocs = docs.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        Document::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        List<RagRecallHit> hits = new ArrayList<>(sortedDocs.size());
        for (int i = 0; i < sortedDocs.size(); i++) {
            hits.add(buildRecallHit(i + 1, sortedDocs.get(i), hashOperations));
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        return new RagRecallResponse(
                query.trim(),
                finalTopK,
                hits.size(),
                durationMs,
                vectorStore.getClass().getSimpleName(),
                hits
        );
    }

    /**
     * 组装单条召回结果。
     * 这里会补齐文件信息，并从 Redis 中读取前后相邻块内容。
     *
     * @param rank 当前结果排序序号
     * @param document 命中的文档块
     * @param hashOperations Redis 哈希操作对象
     * @return 单条召回结果
     */
    private RagRecallHit buildRecallHit(int rank,
                                        Document document,
                                        HashOperations<String, Object, Object> hashOperations) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        RagChunkLocation chunkLocation = resolveChunkLocation(document.getId(), metadata);
        String documentStateKey = RagRedisSchema.buildDocumentStateKey(chunkLocation.getDocumentId());
        String documentChunkTextKey = RagRedisSchema.buildDocumentChunkTextKey(chunkLocation.getDocumentId());
        Map<Object, Object> documentState = hashOperations.entries(documentStateKey);

        String previousContent = null;
        String currentContent = document.getText();
        String nextContent = null;

        if (chunkLocation.getChunkIndex() != null) {
            int index = chunkLocation.getChunkIndex();
            previousContent = asString(hashOperations.get(documentChunkTextKey, String.valueOf(index - 1)));
            String cachedCurrentContent = asString(hashOperations.get(documentChunkTextKey, String.valueOf(index)));
            nextContent = asString(hashOperations.get(documentChunkTextKey, String.valueOf(index + 1)));
            if (StringUtils.hasText(cachedCurrentContent)) {
                currentContent = cachedCurrentContent;
            }
        }

        return new RagRecallHit(
                rank,
                chunkLocation.getDocumentId(),
                document.getId(),
                document.getScore(),
                toDouble(metadata.get("vector_score")),
                toDouble(metadata.get("distance")),
                firstNonBlank(
                        asString(documentState.get(RagRedisSchema.DOCUMENT_STATE_LABEL)),
                        asString(metadata.get("label"))
                ),
                firstNonBlank(
                        asString(documentState.get(RagRedisSchema.DOCUMENT_STATE_FILE_NAME)),
                        asString(metadata.get("fileName"))
                ),
                firstNonBlank(
                        asString(documentState.get(RagRedisSchema.DOCUMENT_STATE_SOURCE)),
                        asString(metadata.get("source"))
                ),
                chunkLocation.getChunkIndex(),
                buildDisplayContent(previousContent, currentContent, nextContent),
                currentContent,
                previousContent,
                nextContent,
                metadata
        );
    }

    /**
     * 从分块文档 id 和 metadata 中解析文档定位信息。
     * 主要用于根据召回结果定位所属文档和分块序号。
     *
     * @param chunkDocumentId 文档块 id
     * @param metadata 文档元数据
     * @return 文本块定位信息
     */
    private RagChunkLocation resolveChunkLocation(String chunkDocumentId, Map<String, Object> metadata) {
        Integer chunkIndex = asInteger(metadata.get(RagVectorMetadataSchema.CHUNK_INDEX));
        String documentId = asString(metadata.get(RagVectorMetadataSchema.DOCUMENT_ID));

        if (StringUtils.hasText(chunkDocumentId)) {
            int separatorIndex = chunkDocumentId.lastIndexOf(':');
            if (separatorIndex > 0) {
                if (!StringUtils.hasText(documentId)) {
                    documentId = chunkDocumentId.substring(0, separatorIndex);
                }
                if (chunkIndex == null) {
                    chunkIndex = Integer.parseInt(chunkDocumentId.substring(separatorIndex + 1));
                }
            }
        }

        if (!StringUtils.hasText(documentId)) {
            throw new ProcessingException("召回结果缺少 documentId");
        }

        return new RagChunkLocation(documentId, chunkIndex);
    }

    /**
     * 组装前端展示用的文本内容。
     * 这里按顺序拼接前一块、命中块、后一块。
     *
     * @param previousContent 前一块内容
     * @param currentContent 当前命中块内容
     * @param nextContent 后一块内容
     * @return 展示文本
     */
    private String buildDisplayContent(String previousContent, String currentContent, String nextContent) {
        List<String> parts = new ArrayList<>(3);
        if (StringUtils.hasText(previousContent)) {
            parts.add(previousContent);
        }
        if (StringUtils.hasText(currentContent)) {
            parts.add(currentContent);
        }
        if (StringUtils.hasText(nextContent)) {
            parts.add(nextContent);
        }
        return String.join("\n\n", parts);
    }

    /**
     * 把任意对象转成字符串。
     *
     * @param value 原始值
     * @return 字符串结果
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 返回两个字符串中第一个非空白值。
     *
     * @param first 第一个值
     * @param second 第二个值
     * @return 第一个非空白值
     */
    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    /**
     * 把对象转换为整数。
     * 兼容 Number 和字符串两种常见情况。
     *
     * @param value 原始值
     * @return 整数结果
     */
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return null;
    }

    /**
     * 把对象转换为 Double。
     * 兼容 Number 和字符串两种常见情况。
     *
     * @param value 原始值
     * @return Double 结果
     */
    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Double.parseDouble(text);
        }
        return null;
    }
}
