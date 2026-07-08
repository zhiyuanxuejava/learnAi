package org.zhiyuan.demo01.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 通用接口错误响应。
 * 用于统一承载错误码、错误信息和发生时间。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    /**
     * 错误码。
     */
    private String code;

    /**
     * 错误信息。
     */
    private String message;

    /**
     * 错误发生时间。
     */
    private Instant timestamp;
}
