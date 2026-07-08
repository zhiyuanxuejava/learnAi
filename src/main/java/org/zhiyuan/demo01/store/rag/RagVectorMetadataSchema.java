package org.zhiyuan.demo01.store.rag;

/**
 * 向量库中文档 metadata 字段定义。
 * 这里统一维护向量检索和删除时依赖的字段名，避免散落在配置类和服务类里。
 */
public final class RagVectorMetadataSchema {

    /**
     * 文档业务唯一标识字段。
     */
    public static final String DOCUMENT_ID = "documentId";

    /**
     * 文档内容哈希字段。
     */
    public static final String CONTENT_HASH = "contentHash";

    /**
     * 文档分块序号字段。
     */
    public static final String CHUNK_INDEX = "chunkIndex";

    private RagVectorMetadataSchema() {
    }
}
