package org.zhiyuan.demo01.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 召回接口返回结果。
 * 用于承载本次查询的整体信息和命中列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagRecallResponse {

    /**
     * 本次查询内容。
     */
    private String query;

    /**
     * 本次查询使用的 topK 值。
     */
    private int topK;

    /**
     * 实际命中的结果数量。
     */
    private int hitCount;

    /**
     * 本次召回总耗时，单位毫秒。
     */
    private long durationMs;

    /**
     * 当前使用的向量存储实现名称。
     */
    private String vectorStore;

    /**
     * 本次召回命中的结果列表。
     */
    private List<RagRecallHit> hits;
}
