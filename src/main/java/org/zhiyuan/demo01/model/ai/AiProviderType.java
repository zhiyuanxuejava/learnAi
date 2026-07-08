package org.zhiyuan.demo01.model.ai;

import org.zhiyuan.demo01.exception.BadRequestException;

import java.util.Arrays;

/**
 * AI 提供商枚举。
 * 用于统一管理前端/接口层传入的 provider 编码。
 */
public enum AiProviderType {
    OPENAI("openai"),
    OLLAMA("ollama");

    private final String code;

    AiProviderType(String code) {
        this.code = code;
    }

    /**
     * 获取 provider 编码。
     *
     * @return provider 编码
     */
    public String code() {
        return code;
    }

    /**
     * 根据编码解析 provider 枚举。
     *
     * @param code provider 编码
     * @return provider 枚举值
     */
    public static AiProviderType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("AI provider 不能为空");
        }

        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("不支持的 AI provider: " + code));
    }
}
