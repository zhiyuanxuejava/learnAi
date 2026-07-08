package org.zhiyuan.demo01.service.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本块定位信息。
 * 用于标识某条召回结果来自哪个文档、哪个分块。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagChunkLocation {

    /**
     * 文档业务唯一标识。
     */
    private String documentId;

    /**
     * 当前命中的分块序号。
     */
    private Integer chunkIndex;

}
