package com.novel2script.model;

import java.util.List;

/**
 * 解析后的小说文本
 */
public record NovelText(
    String title,
    List<Chapter> chapters
) {}
