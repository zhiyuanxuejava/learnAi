package org.zhiyuan.demo01.exception;

/**
 * 请求参数异常。
 * 用于表示客户端传入的数据不合法，最终会映射为 HTTP 400。
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
