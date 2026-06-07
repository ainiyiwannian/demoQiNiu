package com.novel2script.model;

/**
 * AI 转换配置
 */
public record ConversionConfig(
    String apiKey,
    String baseUrl,
    String model,
    double temperature,
    int maxTokens,
    int maxRetries
) {
    public ConversionConfig {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.openai.com/v1";
        if (model == null || model.isBlank()) model = "gpt-4o";
        if (temperature <= 0) temperature = 0.7;
        if (maxTokens <= 0) maxTokens = 4096;
        if (maxRetries <= 0) maxRetries = 3;
    }

    public ConversionConfig(String apiKey) {
        this(apiKey, "https://api.openai.com/v1", "gpt-4o", 0.7, 4096, 3);
    }
}
