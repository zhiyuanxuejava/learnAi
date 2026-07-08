package org.zhiyuan.demo01.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.zhiyuan.demo01.dto.common.ApiErrorResponse;
import org.zhiyuan.demo01.exception.BadRequestException;
import org.zhiyuan.demo01.exception.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * 全局异常处理器。
 * 这里统一把服务层异常映射成稳定的 HTTP 响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理客户端参数异常。
     *
     * @param ex 参数异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("请求参数异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BAD_REQUEST", ex.getMessage(), Instant.now()));
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
