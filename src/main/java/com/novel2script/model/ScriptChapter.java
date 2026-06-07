package com.novel2script.model;

import java.util.List;

/**
 * 剧本章节
 */
public record ScriptChapter(
    String chapterTitle,
    List<ScriptScene> scenes
) {}
