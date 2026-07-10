package org.zhiyuan.demo01.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 问答测试请求。
 * 用于承载问题内容、模型提供方，以及本次召回调参信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagAskRequest {

    /**
     * 用户输入的问题内容。
     */
    private String question;

    /**
     * 本次问答使用的模型提供方。
     * 例如：ollama、openai。
     */
    private String provider;

    /**
     * 会话唯一标识。
     * 只有启用会话记忆时才会生效。
     */
    private String conversationId;

    /**
     * 本次召回使用的 TopK 数量。
     */
    private Integer topK;

    /**
     * 本次召回使用的相似度阈值。
     * 取值范围为 0 到 1。
     */
    private Double similarityThreshold;

    /**
     * 是否启用会话记忆。
     * 测试 RAG 召回时，通常建议先关闭，避免历史上下文干扰测试结果。
     */
    private boolean memoryEnabled;
}
