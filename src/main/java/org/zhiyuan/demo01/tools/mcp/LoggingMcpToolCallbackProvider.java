package org.zhiyuan.demo01.tools.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * MCP ToolCallback 日志装饰器。
 * 这里不改变 MCP 工具原有的调用行为，
 * 只是在工具真正执行前后，把工具名、参数、耗时和返回结果打印到后台日志中。
 */
@Component
public class LoggingMcpToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingMcpToolCallbackProvider.class);

    /**
     * Spring AI 自动配置出来的 MCP ToolCallbackProvider。
     * 这里作为真正的委托对象，日志装饰器只负责包一层日志，不直接实现 MCP 调用细节。
     */
    private final SyncMcpToolCallbackProvider delegate;

    public LoggingMcpToolCallbackProvider(SyncMcpToolCallbackProvider delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取带日志能力的 MCP ToolCallback 数组。
     * 这里每次都基于底层 Provider 当前返回的工具重新包装，
     * 这样即使 MCP 服务端工具列表有变化，日志装饰器也能跟着生效。
     *
     * @return 带日志输出能力的 ToolCallback 数组
     */
    @Override
    public ToolCallback[] getToolCallbacks() {
        ToolCallback[] toolCallbacks = delegate.getToolCallbacks();
        ToolCallback[] loggingCallbacks = new ToolCallback[toolCallbacks.length];

        for (int i = 0; i < toolCallbacks.length; i++) {
            loggingCallbacks[i] = new LoggingToolCallback(delegate, toolCallbacks[i]);
        }
        return loggingCallbacks;
    }

    /**
     * 单个 ToolCallback 的日志包装器。
     * 这里保持 ToolDefinition 和 ToolMetadata 透传，
     * 重点增强 call 阶段的日志输出。
     */
    private static final class LoggingToolCallback implements ToolCallback {

        private final SyncMcpToolCallbackProvider provider;
        private final ToolCallback delegate;

        private LoggingToolCallback(SyncMcpToolCallbackProvider provider, ToolCallback delegate) {
            this.provider = provider;
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return executeWithLog(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return executeWithLog(toolInput, toolContext);
        }

        /**
         * 执行 MCP 工具并打印后台日志。
         * 这里统一记录：
         * 1. 工具名称
         * 2. 工具入参
         * 3. 执行耗时
         * 4. 工具返回结果
         * 5. 调用异常
         *
         * @param toolInput   模型传给工具的 JSON 参数
         * @param toolContext 当前工具上下文
         * @return 工具执行结果
         */
        private String executeWithLog(String toolInput, ToolContext toolContext) {
            String toolName = delegate.getToolDefinition().name();
            long start = System.currentTimeMillis();

            log.info("开始执行 MCP 工具，toolName={}，input={}", toolName, toolInput);

            try {
                String result = callDelegate(delegate, toolInput, toolContext);
                long cost = System.currentTimeMillis() - start;

                log.info("MCP 工具执行完成，toolName={}，cost={}ms，result=\n{}", toolName, cost, result);
                return result;
            }
            catch (Exception ex) {
                if (isRetryableSessionTermination(ex)) {
                    long firstCost = System.currentTimeMillis() - start;
                    log.warn("MCP 会话已终止，准备刷新工具缓存并重试一次，toolName={}，cost={}ms", toolName, firstCost);
                    try {
                        ToolCallback refreshedDelegate = resolveFreshDelegate(toolName);
                        long retryStart = System.currentTimeMillis();
                        String result = callDelegate(refreshedDelegate, toolInput, toolContext);
                        long retryCost = System.currentTimeMillis() - retryStart;
                        log.info("MCP 工具重试成功，toolName={}，retryCost={}ms，result=\n{}", toolName, retryCost, result);
                        return result;
                    }
                    catch (Exception retryEx) {
                        long totalCost = System.currentTimeMillis() - start;
                        log.error("MCP 工具重试失败，toolName={}，cost={}ms，input={}", toolName, totalCost, toolInput, retryEx);
                        throw retryEx;
                    }
                }

                long cost = System.currentTimeMillis() - start;
                log.error("MCP 工具执行失败，toolName={}，cost={}ms，input={}", toolName, cost, toolInput, ex);
                throw ex;
            }
        }

        private String callDelegate(ToolCallback currentDelegate, String toolInput, ToolContext toolContext) {
            return toolContext == null ? currentDelegate.call(toolInput) : currentDelegate.call(toolInput, toolContext);
        }

        private ToolCallback resolveFreshDelegate(String toolName) {
            provider.invalidateCache();
            return Arrays.stream(provider.getToolCallbacks())
                    .filter(callback -> callback.getToolDefinition().name().equals(toolName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("刷新 MCP 工具缓存后，仍未找到工具: " + toolName));
        }

        private boolean isRetryableSessionTermination(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                String message = current.getMessage();
                if (message != null && message.contains("MCP session with server terminated")) {
                    return true;
                }
                if (current instanceof ToolExecutionException toolExecutionException
                        && toolExecutionException.getCause() != null
                        && toolExecutionException.getCause() != current) {
                    current = toolExecutionException.getCause();
                    continue;
                }
                current = current.getCause();
            }
            return false;
        }
    }
}
