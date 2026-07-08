package org.zhiyuan.demo01.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单条 RAG 召回结果。
 * 用于在前端展示命中的文本块、分数和来源信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagRecallHit {

    /**
     * 当前结果的排序序号。
     */
    private int rank;

    /**
     * 当前命中文档的业务唯一标识。
     */
    private String documentId;

    /**
     * 当前文档块的唯一标识。
     */
    private String chunkDocumentId;

    /**
     * Spring AI 返回的综合分数。
     */
    private Double score;

    /**
     * 向量检索原始分数。
     */
    private Double vectorScore;

    /**
     * 向量距离值。
     */
    private Double distance;

    /**
     * 文档标签。
     */
    private String label;

    /**
     * 文件名称。
     */
    private String fileName;

    /**
     * 文件来源路径。
     */
    private String source;

    /**
     * 当前命中的分块序号。
     */
    private Integer chunkIndex;

    /**
     * 用于页面整体展示的文本内容。
     * 这里通常会拼接前一块、命中块、后一块。
     */
    private String displayContent;

    /**
     * 当前真正命中的文本块内容。
     */
    private String content;

    /**
     * 当前命中块的前一块内容。
     */
    private String previousContent;

    /**
     * 当前命中块的后一块内容。
     */
    private String nextContent;

    /**
     * 当前结果附带的元数据信息。
     */
    private Map<String, Object> metadata;
}
