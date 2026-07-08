package org.zhiyuan.demo01.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 相关业务配置。
 * 这里集中管理写入批次大小，以及用于演示导入的文档清单。
 */
@Data
@ConfigurationProperties(prefix = "app.ai.rag")
public class RagProperties {

    /**
     * 批量写入向量库时的单批大小。
     */
    private int writeBatchSize = 100;

    /**
     * 当前项目预置的示例文档列表。
     */
    private List<SampleDocument> sampleDocuments = new ArrayList<>();

    /**
     * 单个示例文档配置。
     */
    @Data
    public static class SampleDocument {

        /**
         * 文档业务唯一标识。
         * 生产环境优先使用稳定业务主键，而不是文件路径。
         */
        private String documentId;

        /**
         * 文档文件路径。
         * 这里既可以写相对路径，也可以写绝对路径。
         */
        private String filePath;

        /**
         * 文档展示名称。
         */
        private String label;
    }
}
