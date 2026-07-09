package org.zhiyuan.demo01.controller;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zhiyuan.demo01.dto.rag.RagClearResponse;
import org.zhiyuan.demo01.dto.rag.RagRecallResponse;
import org.zhiyuan.demo01.service.rag.RagDocumentImportService;
import org.zhiyuan.demo01.service.rag.RagRecallService;

/**
 * RAG 文档导入和召回测试控制器。
 * 这里主要负责：
 * 1. 导入示例文档到向量库
 * 2. 调用 RAG 导入服务执行重建
 * 3. 调用 RAG 召回服务返回测试结果
 */
@RestController
public class MyRagController {

    // 向量存储数据库
    private final VectorStore vectorStore;

    // 文档导入服务
    private final RagDocumentImportService ragDocumentImportService;

    // RAG 召回服务
    private final RagRecallService ragRecallService;

    /**
     * 创建 RAG 控制器。
     *
     * @param vectorStore              向量存储实现
     * @param ragDocumentImportService 文档导入服务
     * @param ragRecallService         RAG 召回服务
     */
    public MyRagController(VectorStore vectorStore,
                           RagDocumentImportService ragDocumentImportService,
                           RagRecallService ragRecallService) {
        this.vectorStore = vectorStore;
        this.ragDocumentImportService = ragDocumentImportService;
        this.ragRecallService = ragRecallService;
    }

    /**
     * 查看当前注入的向量存储实现。
     *
     * @return 向量存储实现类全限定名
     */
    @RequestMapping("/whichStore")
    public String whichStore() {
        return vectorStore.getClass().getName();
    }

    /**
     * 批量导入示例文档到向量存储。
     *
     * @return 处理结果
     */
    @RequestMapping("/addDocs")
    public String addDocs() {
        ragDocumentImportService.importConfiguredDocuments();
        return "ok";
    }

    /**
     * 手动清空当前 RAG 使用的向量数据和辅助缓存。
     * 这里会保留 Redis Search 索引结构，只删除文档数据，方便后续重新导入。
     *
     * @return 清理结果
     */
    @RequestMapping("/rag/clear")
    public RagClearResponse clearRagData() {
        return ragDocumentImportService.clearVectorStoreData();
    }

    /**
     * 测试 RAG 召回结果。
     *
     * @param query 查询内容
     * @param topK  召回数量
     * @return 召回结果
     */
    @GetMapping(value = "/rag/recall", produces = "application/json;charset=UTF-8")
    public RagRecallResponse recall(@RequestParam("query") String query,
                                    @RequestParam(defaultValue = "5") int topK) {
        return ragRecallService.recall(query, topK);
    }

    /**
     * RAG 测试，基于用户的问题和检索的内容，调用LLM 生成答案。
     */
    @GetMapping(value = "/ask_rag")
    public String askRag(@RequestParam("question") String question,
                         @RequestParam(defaultValue = "ollama") String provider,
                         @RequestParam(value = "convId", defaultValue = "1") String convId) {
        return ragRecallService.askRag(question, provider,convId);
    }
}
