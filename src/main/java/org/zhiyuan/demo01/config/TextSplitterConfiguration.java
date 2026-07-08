package org.zhiyuan.demo01.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文本分块配置类
 */
@Configuration
public class TextSplitterConfiguration {

    /**
     * 通用文本切分器。
     *
     * 适用于：
     * PDF、Word、TXT、Markdown 等普通企业知识文档。
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder()
                // 目标切片大小，单位是 Token
                .withChunkSize(500)
                // 用于避免产生过短、缺少语义的文本块
                .withMinChunkSizeChars(100)
                // 长度低于该值的文本块不进行向量化
                .withMinChunkLengthToEmbed(20)
                // 单个 Document 最多允许产生的切片数量
                .withMaxNumChunks(10_000)
                // 尽量保留句号、换行等分隔符
                .withKeepSeparator(true)
                .build();
    }
}