package org.zhiyuan.demo01.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.zhiyuan.demo01.config.properties.RagProperties;
import org.zhiyuan.demo01.exception.BadRequestException;
import org.zhiyuan.demo01.exception.ProcessingException;
import org.zhiyuan.demo01.service.rag.model.RagDocumentImportRequest;
import org.zhiyuan.demo01.store.rag.RagRedisSchema;
import org.zhiyuan.demo01.store.rag.RagVectorMetadataSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档导入服务。
 * 这里负责文件校验、分块生成、向量库重建，以及 Redis 中的文档状态维护。
 */
@Service
public class RagDocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentImportService.class);

    /**
     * 向量存储实现。
     */
    private final VectorStore vectorStore;

    /**
     * 文档解析与分块工具。
     */
    private final RagDocumentProcessingService ragDocumentProcessingService;

    /**
     * Redis 操作模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * RAG 业务配置。
     */
    private final RagProperties ragProperties;

    /**
     * 创建 RAG 文档导入服务。
     *
     * @param vectorStore 向量存储实现
     * @param ragDocumentProcessingService 文档处理服务
     * @param stringRedisTemplate Redis 操作模板
     * @param ragProperties RAG 业务配置
     */
    public RagDocumentImportService(VectorStore vectorStore,
                                    RagDocumentProcessingService ragDocumentProcessingService,
                                    StringRedisTemplate stringRedisTemplate,
                                    RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragDocumentProcessingService = ragDocumentProcessingService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ragProperties = ragProperties;
    }

    /**
     * 导入当前项目中的示例文档。
     * 这里不再在代码里写死文件路径，而是统一读取配置项。
     */
    public void importConfiguredDocuments() {
        if (ragProperties.getSampleDocuments() == null || ragProperties.getSampleDocuments().isEmpty()) {
            log.warn("未配置任何 RAG 示例文档，跳过导入");
            return;
        }

        for (RagProperties.SampleDocument sampleDocument : ragProperties.getSampleDocuments()) {
            importDocument(buildImportRequest(sampleDocument));
        }
    }

    /**
     * 导入单个文档到向量库。
     * 如果文件内容和当前处理策略都没有变化，则直接跳过本次重建。
     *
     * @param request 文档导入请求
     */
    public void importDocument(RagDocumentImportRequest request) {
        try {
            validateImportRequest(request);

            Path path = Path.of(request.getFilePath()).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new BadRequestException("文件不存在: " + request.getFilePath());
            }
            if (!Files.isRegularFile(path)) {
                throw new BadRequestException("不是有效文件: " + request.getFilePath());
            }
            if (!Files.isReadable(path)) {
                throw new BadRequestException("文件不可读: " + request.getFilePath());
            }
            if (Files.size(path) <= 0) {
                throw new BadRequestException("文件内容为空: " + request.getFilePath());
            }

            // 优先使用外部传入的稳定业务 ID。
            // 只有在没有配置 documentId 时，才退回到“基于路径生成哈希”的兼容策略。
            String documentId = resolveDocumentId(request, path);
            String documentStateKey = RagRedisSchema.buildDocumentStateKey(documentId);
            String documentChunkTextKey = RagRedisSchema.buildDocumentChunkTextKey(documentId);

            String contentHash;
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream =
                         new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-256"))) {

                digestInputStream.transferTo(OutputStream.nullOutputStream());
                contentHash = HexFormat.of()
                        .formatHex(digestInputStream.getMessageDigest().digest());
            }

            HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
            Map<Object, Object> documentState = hashOperations.entries(documentStateKey);
            String existingContentHash = documentState.get(RagRedisSchema.DOCUMENT_STATE_CONTENT_HASH) == null
                    ? null : documentState.get(RagRedisSchema.DOCUMENT_STATE_CONTENT_HASH).toString();
            String existingChunkCountValue = documentState.get(RagRedisSchema.DOCUMENT_STATE_CHUNK_COUNT) == null
                    ? null : documentState.get(RagRedisSchema.DOCUMENT_STATE_CHUNK_COUNT).toString();
            String existingPipelineVersion = documentState.get(RagRedisSchema.DOCUMENT_STATE_PIPELINE_VERSION) == null
                    ? null : documentState.get(RagRedisSchema.DOCUMENT_STATE_PIPELINE_VERSION).toString();
            long cachedChunkTextCount = hashOperations.size(documentChunkTextKey);
            boolean hasChunkTextCache = cachedChunkTextCount > 0;
            boolean hasExtendedDocumentState =
                    StringUtils.hasText(documentState.get(RagRedisSchema.DOCUMENT_STATE_FILE_NAME) == null
                            ? null : documentState.get(RagRedisSchema.DOCUMENT_STATE_FILE_NAME).toString())
                            && StringUtils.hasText(documentState.get(RagRedisSchema.DOCUMENT_STATE_LABEL) == null
                            ? null : documentState.get(RagRedisSchema.DOCUMENT_STATE_LABEL).toString())
                            && StringUtils.hasText(documentState.get(RagRedisSchema.DOCUMENT_STATE_SOURCE) == null
                            ? null : documentState.get(RagRedisSchema.DOCUMENT_STATE_SOURCE).toString());

            // 文件内容和入库策略都没有变化时，直接跳过本次导入
            if (contentHash.equals(existingContentHash)
                    && ragDocumentProcessingService.getPipelineVersion().equals(existingPipelineVersion)
                    && StringUtils.hasText(existingChunkCountValue)
                    && hasChunkTextCache
                    && hasExtendedDocumentState
                    && Integer.parseInt(existingChunkCountValue) == cachedChunkTextCount) {
                log.info("{}，文件内容未变化，跳过入库", request.getLabel());
                return;
            }

            List<Document> chunks = ragDocumentProcessingService.parseAndChunkDocument(path.toString());
            if (chunks.isEmpty()) {
                log.warn("{}，文档分块结果为空，保留原有向量数据", request.getLabel());
                return;
            }

            List<Document> rebuiltChunks = new ArrayList<>(chunks.size());
            Map<String, String> chunkTextCache = new HashMap<>(chunks.size());

            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("source", path.toString());
                metadata.put("label", request.getLabel());
                metadata.put("fileName", path.getFileName().toString());
                metadata.put(RagVectorMetadataSchema.DOCUMENT_ID, documentId);
                metadata.put(RagVectorMetadataSchema.CONTENT_HASH, contentHash);
                metadata.put(RagVectorMetadataSchema.CHUNK_INDEX, i);

                // 使用稳定的 chunk id，后面按文件整体重建时更容易定位旧数据
                rebuiltChunks.add(new Document(documentId + ":" + i, chunk.getText(), metadata));
                chunkTextCache.put(String.valueOf(i), chunk.getText());
            }

            // 大量 chunk 分批写入，避免一次性向量化和写库压力过大
            int batchSize = Math.max(this.ragProperties.getWriteBatchSize(), 1);
            for (int start = 0; start < rebuiltChunks.size(); start += batchSize) {
                int end = Math.min(start + batchSize, rebuiltChunks.size());
                vectorStore.add(rebuiltChunks.subList(start, end));
            }

            // 新版本写入成功后，再清理旧版本多余的尾部分块
            int oldChunkCount = StringUtils.hasText(existingChunkCountValue)
                    ? Integer.parseInt(existingChunkCountValue)
                    : 0;
            if (oldChunkCount > rebuiltChunks.size()) {
                List<String> expiredChunkIds = new ArrayList<>(oldChunkCount - rebuiltChunks.size());
                for (int i = rebuiltChunks.size(); i < oldChunkCount; i++) {
                    expiredChunkIds.add(documentId + ":" + i);
                }
                vectorStore.delete(expiredChunkIds);
                hashOperations.delete(documentChunkTextKey, expiredChunkIds.stream()
                        .map(id -> id.substring(id.lastIndexOf(':') + 1))
                        .toArray());
            }

            // 新版本写入和旧尾部清理都完成后，再更新文件状态和文本缓存
            Map<String, String> updatedDocumentState = new HashMap<>();
            updatedDocumentState.put(RagRedisSchema.DOCUMENT_STATE_CONTENT_HASH, contentHash);
            updatedDocumentState.put(RagRedisSchema.DOCUMENT_STATE_CHUNK_COUNT, String.valueOf(rebuiltChunks.size()));
            updatedDocumentState.put(RagRedisSchema.DOCUMENT_STATE_FILE_NAME, path.getFileName().toString());
            updatedDocumentState.put(RagRedisSchema.DOCUMENT_STATE_LABEL, request.getLabel());
            updatedDocumentState.put(RagRedisSchema.DOCUMENT_STATE_SOURCE, path.toString());
            updatedDocumentState.put(RagRedisSchema.DOCUMENT_STATE_PIPELINE_VERSION,
                    ragDocumentProcessingService.getPipelineVersion());
            hashOperations.putAll(documentChunkTextKey, chunkTextCache);
            hashOperations.putAll(documentStateKey, updatedDocumentState);
            log.info("{}，向量存储重建完成！(共 {} 块)", request.getLabel(), rebuiltChunks.size());
        }
        catch (IOException ex) {
            throw new ProcessingException("读取文件失败: " + request.getFilePath(), ex);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new ProcessingException("当前运行环境不支持 SHA-256", ex);
        }
    }

    /**
     * 把配置文件中的文档项转换成导入请求对象。
     *
     * @param sampleDocument 配置中的文档定义
     * @return 导入请求对象
     */
    private RagDocumentImportRequest buildImportRequest(RagProperties.SampleDocument sampleDocument) {
        return new RagDocumentImportRequest(
                sampleDocument.getDocumentId(),
                sampleDocument.getFilePath(),
                sampleDocument.getLabel()
        );
    }

    /**
     * 校验导入请求的关键字段。
     * 这里先校验“请求本身是否完整”，再继续做文件系统层面的校验。
     *
     * @param request 导入请求
     */
    private void validateImportRequest(RagDocumentImportRequest request) {
        if (request == null) {
            throw new BadRequestException("导入请求不能为空");
        }
        if (!StringUtils.hasText(request.getFilePath())) {
            throw new BadRequestException("文件路径不能为空");
        }
        if (!StringUtils.hasText(request.getLabel())) {
            throw new BadRequestException("文档标签不能为空");
        }
    }

    /**
     * 解析最终使用的文档业务唯一标识。
     * 生产环境应优先使用稳定的业务主键；当前为了兼容未配置 documentId 的场景，保留路径哈希兜底。
     *
     * @param request 导入请求
     * @param path 文件绝对路径
     * @return 最终文档业务唯一标识
     * @throws NoSuchAlgorithmException 当运行环境不支持 SHA-256 时抛出
     */
    private String resolveDocumentId(RagDocumentImportRequest request, Path path) throws NoSuchAlgorithmException {
        if (StringUtils.hasText(request.getDocumentId())) {
            return request.getDocumentId().trim();
        }

        MessageDigest pathDigest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of()
                .formatHex(pathDigest.digest(path.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
