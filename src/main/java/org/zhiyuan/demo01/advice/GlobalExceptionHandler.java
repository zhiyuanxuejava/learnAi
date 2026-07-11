package org.zhiyuan.demo01.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.zhiyuan.demo01.dto.common.ApiErrorResponse;
import org.zhiyuan.demo01.exception.BadRequestException;
import org.zhiyuan.demo01.exception.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;

/**
 * 全局异常处理器。
 * 这里统一把服务层异常映射成稳定的 HTTP 响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final String maxFileSize;

    public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    /**
     * 处理客户端参数异常。
     *
     * @param ex 参数异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException ex) {
        log.warn("请求参数异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BAD_REQUEST", ex.getMessage(), Instant.now()));
    }

    /**
     * 处理上传文件超限异常。
     *
     * @param ex 上传超限异常
     * @return HTTP 413 响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超限: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse("FILE_TOO_LARGE", "上传图片过大，请控制在 " + maxFileSize + " 以内后重试", Instant.now()));
    }

    /**
     * 处理服务层业务处理异常。
     *
     * @param ex 业务处理异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler({ProcessingException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> handleProcessingException(RuntimeException ex) {
        log.error("业务处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("PROCESSING_ERROR", ex.getMessage(), Instant.now()));
    }

    /**
     * 处理静态资源不存在异常。
     * 例如浏览器自动请求 favicon.ico，但项目中未提供对应资源。
     *
     * @param ex 静态资源未找到异常
     * @return HTTP 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("静态资源不存在: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("NOT_FOUND", "请求的资源不存在", Instant.now()));
    }

    /**
     * 处理 SSE 客户端主动断开连接的情况。
     * 流式输出时，用户点击停止、刷新页面或切换网络都可能导致浏览器关闭连接。
     * 此时响应已经无法再写回客户端，因此只记录调试日志，不再尝试返回 JSON 错误体。
     *
     * @param ex 客户端连接不可用异常
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        log.debug("SSE 客户端已断开连接: {}", ex.getMessage());
    }

    /**
     * 处理兜底异常。
     *
     * @param ex 未分类异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownException(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_SERVER_ERROR", "服务处理失败", Instant.now()));
    }
}
