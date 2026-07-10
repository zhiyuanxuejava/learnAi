package org.zhiyuan.demo01.controller;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zhiyuan.demo01.dto.rag.RagAskRequest;
import org.zhiyuan.demo01.dto.rag.RagAskResponse;
import org.zhiyuan.demo01.dto.rag.RagClearResponse;
import org.zhiyuan.demo01.dto.rag.RagRecallResponse;
import org.zhiyuan.demo01.service.rag.RagDocumentImportService;
import org.zhiyuan.demo01.service.rag.RagQuestionAnswerService;
import org.zhiyuan.demo01.service.rag.RagRecallService;
import reactor.core.publisher.Flux;

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

    // RAG 问答服务
    private final RagQuestionAnswerService ragQuestionAnswerService;

    /**
     * 创建 RAG 控制器。
     *
     * @param vectorStore              向量存储实现
     * @param ragDocumentImportService 文档导入服务
     * @param ragRecallService         RAG 召回服务
     * @param ragQuestionAnswerService RAG 问答服务
     */
    public MyRagController(VectorStore vectorStore,
                           RagDocumentImportService ragDocumentImportService,
                           RagRecallService ragRecallService,
                           RagQuestionAnswerService ragQuestionAnswerService) {
        this.vectorStore = vectorStore;
        this.ragDocumentImportService = ragDocumentImportService;
        this.ragRecallService = ragRecallService;
        this.ragQuestionAnswerService = ragQuestionAnswerService;
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
     * @param similarityThreshold 相似度阈值
     * @return 召回结果
     */
    @GetMapping(value = "/rag/recall", produces = "application/json;charset=UTF-8")
    public RagRecallResponse recall(@RequestParam("query") String query,
                                    @RequestParam(defaultValue = "5") int topK,
                                    @RequestParam(required = false) Double similarityThreshold) {
        return ragRecallService.recall(query, topK, similarityThreshold);
    }

    /**
     * RAG 问答测试接口。
     * 这里会返回模型答案，以及本次召回命中的参考内容，方便前端直观观察问答过程。
     */
    @PostMapping(value = "/rag/ask", produces = "application/json;charset=UTF-8")
    public RagAskResponse askRag(@RequestBody RagAskRequest request) {
        return ragQuestionAnswerService.ask(request);
    }

    /**
     * RAG 流式问答测试接口。
     * 这里用于前端页面实时展示最终答案生成过程；
     * 召回块和命中信息不在页面展示，而是统一写入后台日志。
     *
     * @param question 用户问题
     * @param provider 模型提供方
     * @param conversationId 会话 id
     * @param topK 召回数量
     * @param similarityThreshold 相似度阈值
     * @param memoryEnabled 是否启用会话记忆
     * @return SSE 流式输出
     */
    @GetMapping(value = "/rag/ask/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> askRagStream(@RequestParam("question") String question,
                                                      @RequestParam(defaultValue = "ollama") String provider,
                                                      @RequestParam(value = "conversationId", defaultValue = "rag-lab-1") String conversationId,
                                                      @RequestParam(defaultValue = "5") Integer topK,
                                                      @RequestParam(defaultValue = "0.7") Double similarityThreshold,
                                                      @RequestParam(defaultValue = "false") boolean memoryEnabled) {
        RagAskRequest request = new RagAskRequest(
                question,
                provider,
                conversationId,
                topK,
                similarityThreshold,
                memoryEnabled
        );
        return ragQuestionAnswerService.askStream(request);
    }

    /**
     * 兼容旧的 GET 测试方式。
     * 这里保留简单入口，方便直接在浏览器地址栏中快速验证回答结果。
     *
     * @param question 用户问题
     * @param provider 模型提供方
     * @param convId 会话 id
     * @return 模型答案
     */
    @GetMapping(value = "/ask_rag")
    public String askRagCompatibility(@RequestParam("question") String question,
                                      @RequestParam(defaultValue = "ollama") String provider,
                                      @RequestParam(value = "convId", defaultValue = "1") String convId) {
        RagAskRequest request = new RagAskRequest(question, provider, convId, 5, 0.7D, true);
        return ragQuestionAnswerService.ask(request).getAnswer();
    }
}
