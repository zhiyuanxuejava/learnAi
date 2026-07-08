package org.zhiyuan.demo01.store.rag;

/**
 * RAG 在 Redis 中的存储结构定义。
 * 这里统一维护 key 前缀、hash 字段名，以及相关 key 的组装方式。
 */
public final class RagRedisSchema {

    /**
     * 文档状态缓存前缀。
     */
    public static final String DOCUMENT_STATE_KEY_PREFIX = "ai:rag:document-state:";

    /**
     * 文档分块文本缓存前缀。
     */
    public static final String DOCUMENT_CHUNK_TEXT_KEY_PREFIX = "ai:rag:document-chunks:";

    /**
     * 文档状态字段：内容哈希。
     */
    public static final String DOCUMENT_STATE_CONTENT_HASH = "contentHash";

    /**
     * 文档状态字段：分块数量。
     */
    public static final String DOCUMENT_STATE_CHUNK_COUNT = "chunkCount";

    /**
     * 文档状态字段：文件名。
     */
    public static final String DOCUMENT_STATE_FILE_NAME = "fileName";

    /**
     * 文档状态字段：文档标签。
     */
    public static final String DOCUMENT_STATE_LABEL = "label";

    /**
     * 文档状态字段：来源路径。
     */
    public static final String DOCUMENT_STATE_SOURCE = "source";

    /**
     * 文档状态字段：当前入库策略版本。
     */
    public static final String DOCUMENT_STATE_PIPELINE_VERSION = "pipelineVersion";

    private RagRedisSchema() {
    }

    /**
     * 生成文档状态缓存 key。
     *
     * @param documentId 文档业务唯一标识
     * @return Redis key
     */
    public static String buildDocumentStateKey(String documentId) {
        return DOCUMENT_STATE_KEY_PREFIX + documentId;
    }

    /**
     * 生成文档分块文本缓存 key。
     *
     * @param documentId 文档业务唯一标识
     * @return Redis key
     */
    public static String buildDocumentChunkTextKey(String documentId) {
        return DOCUMENT_CHUNK_TEXT_KEY_PREFIX + documentId;
    }
}
