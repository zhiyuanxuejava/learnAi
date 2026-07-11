package org.zhiyuan.demo01.config;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zhiyuan.demo01.tools.mcp.LoggingMcpToolCallbackProvider;

@Configuration
public class McpConfiguration {

    /**
     * MCP 可用时，包装 Spring AI 自动创建的 ToolCallbackProvider，
     * 保留现有的 MCP 工具调用能力，并附加统一日志。
     */
    @Bean
    @ConditionalOnBean(SyncMcpToolCallbackProvider.class)
    public LoggingMcpToolCallbackProvider loggingMcpToolCallbackProvider(SyncMcpToolCallbackProvider delegate) {
        return new LoggingMcpToolCallbackProvider(delegate);
    }

    /**
     * MCP 未启用或客户端未成功装配时，提供一个空实现。
     * 这样控制器和 ChatClient 仍可正常注入，应用不会因为 MCP 不可用而启动失败。
     */
    @Bean
    @ConditionalOnMissingBean(LoggingMcpToolCallbackProvider.class)
    public LoggingMcpToolCallbackProvider emptyLoggingMcpToolCallbackProvider() {
        return new LoggingMcpToolCallbackProvider();
    }
}
