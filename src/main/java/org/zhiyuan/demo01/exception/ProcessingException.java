package org.zhiyuan.demo01.exception;

/**
 * 业务处理异常。
 * 用于表示服务层在执行过程中发生了不应由客户端兜底的内部错误。
 */
public class ProcessingException extends RuntimeException {

    public ProcessingException(String message) {
        super(message);
    }

    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
