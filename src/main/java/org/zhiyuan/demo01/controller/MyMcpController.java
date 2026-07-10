package org.zhiyuan.demo01.controller;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zhiyuan.demo01.service.ChatClientFactory;

@RestController
public class MyMcpController {

    private final ChatClientFactory chatClientFactory;

    private final SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    public MyMcpController(ChatClientFactory chatClientFactory,
                           SyncMcpToolCallbackProvider mcpToolCallbackProvider) {
        this.chatClientFactory = chatClientFactory;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }


    @RequestMapping("/mcp")
    public String aiWithMcp(@RequestParam("question") String question) {
        ChatClient chatClient = chatClientFactory.getChatClient("ollama", false);
        String result = chatClient.prompt()
                .system("用户询问天气相关的问题时候，你必须调用工具查询后再用简短中文回答.")
                .user(question)
                .tools(mcpToolCallbackProvider)
                .call()
                .content();

        System.out.println("回答结果：" + result);
        return result;
    }


}
