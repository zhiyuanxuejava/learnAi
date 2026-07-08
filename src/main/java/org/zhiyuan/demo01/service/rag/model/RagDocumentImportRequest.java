package org.zhiyuan.demo01.service.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 文档导入请求对象。
 * 用于把文档唯一标识、文件路径和展示名称打包传给导入服务。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentImportRequest {

    /**
     * 文档业务唯一标识。
     */
    private String documentId;

    /**
     * 文档文件路径。
     */
    private String filePath;

    /**
     * 文档展示名称。
     */
    private String label;
}
