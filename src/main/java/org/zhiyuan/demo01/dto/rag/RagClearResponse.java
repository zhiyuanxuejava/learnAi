package org.zhiyuan.demo01.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 清库结果。
 * 用于返回本次清理操作删除了多少向量文档和辅助缓存。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagClearResponse {

    /**
     * 当前使用的 Redis Search 索引名称。
     */
    private String indexName;

    /**
     * 向量文档在 Redis 中使用的 Key 前缀。
     */
    private String vectorKeyPrefix;

    /**
     * 是否保留了索引结构。
     * 当前清理方案只删除文档数据，不删除索引 schema。
     */
    private boolean indexPreserved;

    /**
     * 删除的向量文档 key 数量。
     */
    private long deletedVectorDocumentCount;

    /**
     * 删除的文档状态 key 数量。
     */
    private long deletedDocumentStateCount;

    /**
     * 删除的文档分块缓存 key 数量。
     */
    private long deletedDocumentChunkTextCount;

    /**
     * 本次清理删除的总 key 数量。
     */
    private long totalDeletedCount;
}
