package org.zhiyuan.demo01.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.zhiyuan.demo01.exception.BadRequestException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档处理服务。
 * 这里统一负责：
 * 1. 按文件类型解析文档
 * 2. 根据文件类型生成最终入库分块
 * 3. 对 Markdown 做结构化文本整理
 */
@Service
public class RagDocumentProcessingService {

    // Markdown 元数据字段：当前块标题
    private static final String MARKDOWN_SECTION_TITLE = "sectionTitle";

    // Markdown 元数据字段：父级标题
    private static final String MARKDOWN_PARENT_SECTION_TITLE = "parentSectionTitle";

    // Markdown 元数据字段：当前块是否携带显式标题
    private static final String MARKDOWN_HAS_EXPLICIT_TITLE = "hasExplicitTitle";

    // Markdown 元数据字段：当前标题层级
    private static final String MARKDOWN_SECTION_LEVEL = "sectionLevel";

    // Markdown 块太短时，优先与相邻块合并，避免召回到孤立的一句话
    private static final int MARKDOWN_MIN_SECTION_CHARS = 180;

    // Markdown 合并后的块超过这个长度时，再交给通用切分器做二次切分
    private static final int MARKDOWN_MAX_SECTION_CHARS = 1_200;

    // Markdown 相邻块合并时的上限，避免一次拼成过大的文本块
    private static final int MARKDOWN_MAX_MERGED_SECTION_CHARS = 1_600;

    // 当前文档处理策略版本
    private static final String PIPELINE_VERSION = "v4-rag-document-processing-service";

    /**
     * Markdown 使用结构化解析器。
     * 这里显式保留代码块、引用块，并且不把水平分隔线单独拆成一个 Document。
     */
    private static final MarkdownDocumentReaderConfig MARKDOWN_READER_CONFIG =
            MarkdownDocumentReaderConfig.builder()
                    .withIncludeCodeBlock(true)
                    .withIncludeBlockquote(true)
                    .withHorizontalRuleCreateDocument(false)
                    .build();

    /**
     * 通用文本切分器。
     */
    private final TokenTextSplitter tokenTextSplitter;

    /**
     * 创建 RAG 文档处理服务。
     *
     * @param tokenTextSplitter 通用文本切分器
     */
    public RagDocumentProcessingService(TokenTextSplitter tokenTextSplitter) {
        this.tokenTextSplitter = tokenTextSplitter;
    }

    /**
     * 解析原始文档内容。
     * 这里只负责按文件类型读取文档，不负责做最终分块。
     *
     * @param filePath 文件路径
     * @return 原始解析结果
     */
    public List<Document> parseDocument(String filePath) {
        File file = new File(filePath);
        String suffix = StringUtils.getFilenameExtension(file.getName());
        if (!StringUtils.hasText(suffix)) {
            throw new BadRequestException("文件缺少扩展名: " + filePath);
        }

        suffix = suffix.toLowerCase();
        Resource resource = new FileSystemResource(file);
        DocumentReader reader = switch (suffix) {
            case "pdf", "doc", "docx", "txt", "text" -> new TikaDocumentReader(resource);
            case "md", "markdown" -> new MarkdownDocumentReader(resource, MARKDOWN_READER_CONFIG);
            default -> throw new BadRequestException("不支持的文件格式: " + suffix);
        };
        return reader.get();
    }

    /**
     * 解析文档并生成最终入库分块。
     * 对外部调用方来说，优先使用这个方法即可。
     *
     * @param filePath 文件路径
     * @return 最终可直接入库的文本块列表
     */
    public List<Document> parseAndChunkDocument(String filePath) {
        List<Document> rawDocuments = parseDocument(filePath);
        if (rawDocuments.isEmpty()) {
            return List.of();
        }
        return buildChunkDocuments(filePath, rawDocuments);
    }

    /**
     * 获取当前文档处理策略版本。
     * 当分块规则发生变化时，可以通过修改这个版本号触发重新入库。
     *
     * @return 当前文档处理策略版本
     */
    public String getPipelineVersion() {
        return PIPELINE_VERSION;
    }

    /**
     * 判断当前文件是否为 Markdown。
     * 后续入库时，Markdown 会走结构化分块逻辑，而不是直接走通用切分器。
     *
     * @param filePath 文件路径
     * @return 是否为 Markdown 文件
     */
    public boolean isMarkdownDocument(String filePath) {
        File file = new File(filePath);
        String suffix = StringUtils.getFilenameExtension(file.getName());
        if (!StringUtils.hasText(suffix)) {
            return false;
        }
        suffix = suffix.toLowerCase();
        return "md".equals(suffix) || "markdown".equals(suffix);
    }

    /**
     * 根据文件类型生成最终入库的文本块。
     * Markdown 优先保留标题结构，只在块过大时再做二次切分。
     *
     * @param filePath 文件路径
     * @param rawDocuments 原始解析结果
     * @return 最终用于向量化和入库的文本块列表
     */
    public List<Document> buildChunkDocuments(String filePath, List<Document> rawDocuments) {
        if (isMarkdownDocument(filePath)) {
            return buildMarkdownChunks(rawDocuments);
        }
        return tokenTextSplitter.apply(rawDocuments);
    }

    /**
     * 对 Markdown 文档做结构化分块。
     * 主要处理：
     * 1. 标题补入文本
     * 2. 短块合并
     * 3. 仅对超长块做二次切分
     *
     * @param rawDocuments Markdown 原始解析结果
     * @return 处理后的 Markdown 分块结果
     */
    private List<Document> buildMarkdownChunks(List<Document> rawDocuments) {
        List<Document> normalizedDocuments = new ArrayList<>();

        String currentSectionTitle = null;
        String currentHeaderOneTitle = null;
        String currentHeaderTwoTitle = null;
        String lastPreparedSectionTitle = null;

        for (Document rawDocument : rawDocuments) {
            String content = normalizeChunkText(rawDocument.getText());
            if (!StringUtils.hasText(content)) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(rawDocument.getMetadata());
            String category = normalizeMetadataText(metadata.get("category"));
            String explicitTitle = normalizeMetadataText(metadata.get("title"));
            boolean hasExplicitTitle = StringUtils.hasText(explicitTitle);
            int sectionLevel = hasExplicitTitle ? resolveSectionLevel(category, explicitTitle) : 0;

            if (hasExplicitTitle) {
                currentSectionTitle = explicitTitle;

                int headerLevel = resolveHeaderLevel(category);
                if (headerLevel == 1) {
                    currentHeaderOneTitle = explicitTitle;
                    currentHeaderTwoTitle = null;
                }
                else if (headerLevel == 2) {
                    currentHeaderTwoTitle = explicitTitle;
                }
            }

            String sectionTitle = StringUtils.hasText(explicitTitle) ? explicitTitle : currentSectionTitle;
            String parentSectionTitle = resolveParentSectionTitle(sectionTitle, category,
                    currentHeaderOneTitle, currentHeaderTwoTitle);

            if (StringUtils.hasText(sectionTitle)) {
                metadata.put(MARKDOWN_SECTION_TITLE, sectionTitle);
            }
            else {
                metadata.remove(MARKDOWN_SECTION_TITLE);
            }

            if (StringUtils.hasText(parentSectionTitle)) {
                metadata.put(MARKDOWN_PARENT_SECTION_TITLE, parentSectionTitle);
            }
            else {
                metadata.remove(MARKDOWN_PARENT_SECTION_TITLE);
            }

            metadata.put(MARKDOWN_HAS_EXPLICIT_TITLE, hasExplicitTitle);
            metadata.put(MARKDOWN_SECTION_LEVEL, sectionLevel);

            String preparedText = buildMarkdownText(sectionTitle, category, content, lastPreparedSectionTitle);
            if (!StringUtils.hasText(preparedText)) {
                continue;
            }

            normalizedDocuments.add(new Document(preparedText, metadata));
            if (StringUtils.hasText(sectionTitle)) {
                lastPreparedSectionTitle = sectionTitle;
            }
        }

        if (normalizedDocuments.isEmpty()) {
            return List.of();
        }

        List<Document> mergedDocuments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        Map<String, Object> currentMetadata = null;
        String currentSection = null;
        String currentParentSection = null;
        boolean currentHasExplicitTitle = false;
        int currentSectionLevel = 0;

        for (Document normalizedDocument : normalizedDocuments) {
            Map<String, Object> metadata = new HashMap<>(normalizedDocument.getMetadata());
            String nextText = normalizedDocument.getText();
            String nextSection = normalizeMetadataText(metadata.get(MARKDOWN_SECTION_TITLE));
            String nextParentSection = normalizeMetadataText(metadata.get(MARKDOWN_PARENT_SECTION_TITLE));
            Integer nextSectionLevelValue = asInteger(metadata.get(MARKDOWN_SECTION_LEVEL));
            boolean nextHasExplicitTitle = asBoolean(metadata.get(MARKDOWN_HAS_EXPLICIT_TITLE));
            int nextSectionLevel = nextSectionLevelValue == null ? 0 : nextSectionLevelValue;

            if (currentMetadata == null) {
                currentText.append(nextText);
                currentMetadata = metadata;
                currentSection = nextSection;
                currentParentSection = nextParentSection;
                currentHasExplicitTitle = nextHasExplicitTitle;
                currentSectionLevel = nextSectionLevel;
                continue;
            }

            if (shouldMergeMarkdownDocuments(currentSection, currentParentSection, currentText.length(),
                    currentHasExplicitTitle, currentSectionLevel,
                    nextSection, nextParentSection, nextText.length(),
                    nextHasExplicitTitle, nextSectionLevel)) {
                currentText.append("\n\n").append(nextText);
            }
            else {
                mergedDocuments.add(new Document(currentText.toString(), currentMetadata));
                currentText = new StringBuilder(nextText);
                currentMetadata = metadata;
                currentSection = nextSection;
                currentParentSection = nextParentSection;
                currentHasExplicitTitle = nextHasExplicitTitle;
                currentSectionLevel = nextSectionLevel;
            }
        }

        if (currentMetadata != null) {
            mergedDocuments.add(new Document(currentText.toString(), currentMetadata));
        }

        List<Document> finalDocuments = new ArrayList<>();
        for (Document mergedDocument : mergedDocuments) {
            if (mergedDocument.getText().length() > MARKDOWN_MAX_SECTION_CHARS) {
                finalDocuments.addAll(tokenTextSplitter.apply(List.of(mergedDocument)));
            }
            else {
                finalDocuments.add(mergedDocument);
            }
        }

        return finalDocuments;
    }

    /**
     * 把 Markdown 标题补进文本本身，让向量化时能保留章节语义。
     * 如果当前块和前一个块属于同一章节，则不重复追加标题。
     *
     * @param sectionTitle 当前章节标题
     * @param category Markdown 块类型
     * @param content 当前块内容
     * @param lastSectionTitle 上一个已处理块的章节标题
     * @return 用于向量化的文本
     */
    private String buildMarkdownText(String sectionTitle, String category, String content, String lastSectionTitle) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        if (!StringUtils.hasText(sectionTitle) || sectionTitle.equals(lastSectionTitle)) {
            return content;
        }

        int headerLevel = resolveHeaderLevel(category);
        String headingPrefix = switch (headerLevel) {
            case 1 -> "# ";
            case 2 -> "## ";
            case 3 -> "### ";
            default -> "## ";
        };
        return headingPrefix + sectionTitle + "\n" + content;
    }

    /**
     * 判断两个 Markdown 块是否应该先合并后再入库。
     * 合并规则尽量围绕同一章节或同一父章节，避免把无关内容拼在一起。
     *
     * @param currentSection 当前块标题
     * @param currentParentSection 当前块父标题
     * @param currentLength 当前块长度
     * @param currentHasExplicitTitle 当前块是否携带显式标题
     * @param currentSectionLevel 当前块标题层级
     * @param nextSection 下一个块标题
     * @param nextParentSection 下一个块父标题
     * @param nextLength 下一个块长度
     * @param nextHasExplicitTitle 下一个块是否携带显式标题
     * @param nextSectionLevel 下一个块标题层级
     * @return 是否合并
     */
    private boolean shouldMergeMarkdownDocuments(String currentSection,
                                                 String currentParentSection,
                                                 int currentLength,
                                                 boolean currentHasExplicitTitle,
                                                 int currentSectionLevel,
                                                 String nextSection,
                                                 String nextParentSection,
                                                 int nextLength,
                                                 boolean nextHasExplicitTitle,
                                                 int nextSectionLevel) {
        if (currentLength + nextLength > MARKDOWN_MAX_MERGED_SECTION_CHARS) {
            return false;
        }

        boolean sameSection = StringUtils.hasText(currentSection) && currentSection.equals(nextSection);
        if (sameSection) {
            return true;
        }

        boolean shortSection = currentLength < MARKDOWN_MIN_SECTION_CHARS || nextLength < MARKDOWN_MIN_SECTION_CHARS;
        if (!shortSection) {
            return false;
        }

        boolean bothHaveOwnTitle = currentHasExplicitTitle && nextHasExplicitTitle;
        boolean differentSection = StringUtils.hasText(currentSection)
                && StringUtils.hasText(nextSection)
                && !currentSection.equals(nextSection);
        boolean bothAreSubSection = currentSectionLevel >= 3 && nextSectionLevel >= 3;
        if (bothHaveOwnTitle && differentSection && !bothAreSubSection) {
            return false;
        }

        boolean sameParentSection = StringUtils.hasText(currentParentSection)
                && currentParentSection.equals(nextParentSection);
        if (sameParentSection) {
            return true;
        }

        return StringUtils.hasText(currentSection) && currentSection.equals(nextParentSection);
    }

    /**
     * 解析 Markdown 标题层级。
     *
     * @param category MarkdownDocumentReader 产生的块类型
     * @return 标题层级；不是标题则返回 0
     */
    private int resolveHeaderLevel(String category) {
        if (!StringUtils.hasText(category) || !category.startsWith("header_")) {
            return 0;
        }
        return Integer.parseInt(category.substring("header_".length()));
    }

    /**
     * 推断当前标题层级。
     * header_2、header_3 这类可以直接取层级；其他带显式标题的块默认按二级章节处理。
     *
     * @param category Markdown 块类型
     * @param explicitTitle 显式标题
     * @return 标题层级
     */
    private int resolveSectionLevel(String category, String explicitTitle) {
        if (!StringUtils.hasText(explicitTitle)) {
            return 0;
        }

        int headerLevel = resolveHeaderLevel(category);
        return headerLevel > 0 ? headerLevel : 2;
    }

    /**
     * 推断当前块的父级标题。
     * 主要用于把同一个大章节下过短的相邻块合并在一起。
     *
     * @param sectionTitle 当前块标题
     * @param category Markdown 块类型
     * @param currentHeaderOneTitle 当前 H1 标题
     * @param currentHeaderTwoTitle 当前 H2 标题
     * @return 父级标题
     */
    private String resolveParentSectionTitle(String sectionTitle,
                                             String category,
                                             String currentHeaderOneTitle,
                                             String currentHeaderTwoTitle) {
        int headerLevel = resolveHeaderLevel(category);
        if (headerLevel == 3) {
            return currentHeaderTwoTitle;
        }
        if (headerLevel == 2) {
            return currentHeaderOneTitle;
        }
        if (StringUtils.hasText(sectionTitle) && sectionTitle.equals(currentHeaderTwoTitle)) {
            return currentHeaderOneTitle;
        }
        return currentHeaderTwoTitle;
    }

    /**
     * 规范化文本块内容，去掉首尾空白，避免空段落进入向量库。
     *
     * @param text 原始文本
     * @return 规范化后的文本
     */
    private String normalizeChunkText(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 规范化 metadata 中的文本字段。
     *
     * @param value 元数据值
     * @return 处理后的字符串
     */
    private String normalizeMetadataText(Object value) {
        return value == null ? null : value.toString().trim();
    }

    /**
     * 把对象转换为整数。
     * 兼容 Number 和字符串两种常见情况。
     *
     * @param value 原始值
     * @return 整数结果
     */
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return null;
    }

    /**
     * 把对象转换为布尔值。
     * 兼容 Boolean 和字符串两种常见情况。
     *
     * @param value 原始值
     * @return 布尔值结果
     */
    private boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }
}
