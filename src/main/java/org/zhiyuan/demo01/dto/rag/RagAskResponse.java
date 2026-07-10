package org.zhiyuan.demo01.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 问答测试返回结果。
 * 用于同时返回模型答案、本次召回参数、耗时和命中的参考片段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagAskResponse {

    /**
     * 本次问答使用的问题内容。
     */
    private String question;

    /**
     * 本次问答使用的模型提供方。
     */
    private String provider;

    /**
     * 会话唯一标识。
     * 当未启用会话记忆时，这里通常为空。
     */
    private String conversationId;

    /**
     * 本次问答是否启用了会话记忆。
     */
    private boolean memoryEnabled;

    /**
     * 本次召回使用的 TopK 数量。
     */
    private int topK;

    /**
     * 本次召回使用的相似度阈值。
     */
    private double similarityThreshold;

    /**
     * 本次显式召回命中的结果数量。
     */
    private int hitCount;

    /**
     * 显式召回耗时，单位毫秒。
     */
    private long recallDurationMs;

    /**
     * 大模型生成答案耗时，单位毫秒。
     */
    private long generationDurationMs;

    /**
     * 本次问答总耗时，单位毫秒。
     */
    private long totalDurationMs;

    /**
     * 当前使用的向量存储实现名称。
     */
    private String vectorStore;

    /**
     * 大模型最终生成的答案内容。
     */
    private String answer;

    /**
     * 本次问答实际使用到的召回结果列表。
     * 这里方便前端直观看到参考来源和分块内容。
     */
    private List<RagRecallHit> hits;
}
