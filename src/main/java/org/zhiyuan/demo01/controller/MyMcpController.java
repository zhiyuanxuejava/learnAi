package org.zhiyuan.demo01.controller;


import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zhiyuan.demo01.service.ChatClientFactory;
import org.zhiyuan.demo01.tools.mcp.LoggingMcpToolCallbackProvider;

/**
 * MCP 调用测试控制器。
 * 这里主要用于验证：
 * 1. 模型是否能正确发现 MCP 工具
 * 2. 模型是否真的发起了工具调用
 * 3. 后台日志中是否能看到工具返回结果
 */
@RestController
public class MyMcpController {

    private static final Logger log = LoggerFactory.getLogger(MyMcpController.class);

    private final ChatClientFactory chatClientFactory;

    private final LoggingMcpToolCallbackProvider loggingMcpToolCallbackProvider;

    public MyMcpController(ChatClientFactory chatClientFactory,
                           LoggingMcpToolCallbackProvider loggingMcpToolCallbackProvider) {
        this.chatClientFactory = chatClientFactory;
        this.loggingMcpToolCallbackProvider = loggingMcpToolCallbackProvider;
    }


    /**
     * MCP 问答测试接口。
     * 当模型命中工具调用场景时，
     * MCP 工具的请求参数和返回结果会打印到后台日志中。
     *
     * @param question 用户问题
     * @return 模型最终回答
     */
    @RequestMapping("/mcp")
    public String aiWithMcp(@RequestParam("question") String question) {
        ChatClient chatClient = chatClientFactory.getChatClient("ollama", false);
        String result = chatClient.prompt()
                .system("用户询问天气相关的问题时候，你必须调用工具查询后再用简短中文回答.")
                .user(question)
                .tools(loggingMcpToolCallbackProvider)
                .call()
                .content();

        log.info("MCP 问答完成，最终回答：{}", result);
        return result;
    }


}
