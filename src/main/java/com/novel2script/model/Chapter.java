package com.novel2script.model;

/**
 * 小说章节数据
 */
public record Chapter(
    int index,
    String title,
    String content,
    String rawText
) {}
