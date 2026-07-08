package org.zhiyuan.demo01.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * ChatClient 日志 Advisor。
 * 这里同时支持阻塞式和流式调用，用于记录模型调用的开始和结束时机。
 */
public class ChatRequestLogAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ChatRequestLogAdvisor.class);


    /**
     * 阻塞式调用日志拦截。
     *
     * @param chatClientRequest 请求对象
     * @param callAdvisorChain Advisor 链
     * @return 模型响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.info("开始执行阻塞式模型调用");
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        log.info("阻塞式模型调用完成");
        return chatClientResponse;
    }

    /**
     * 流式调用日志拦截。
     * 这里重点记录整个流式会话的耗时，而不是逐片段打印内容。
     *
     * @param chatClientRequest 请求对象
     * @param streamAdvisorChain Advisor 链
     * @return 流式响应
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {

        long start = System.currentTimeMillis();

        log.info("开始执行流式模型调用");

        return streamAdvisorChain.nextStream(chatClientRequest)
                .doOnError(ex -> log.error("流式模型调用失败", ex))
                .doFinally(signalType -> {
                    long cost = System.currentTimeMillis() - start;
                    log.info("流式模型调用结束，signal={}，cost={}ms", signalType, cost);
                });
    }

    @Override
    public String getName() {
        return "chatRequestLogAdvisor";
    }

    /**
     * 设置排序
     * 值越小越先执行
     * 返回0 表示最高优先级
     * 如果有多个Advisor，Spring AI 会按顺序执行Advisor
     * @return
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
