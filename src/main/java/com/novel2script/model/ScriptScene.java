package com.novel2script.model;

import java.util.List;
import java.util.Map;

/**
 * 剧本场景
 */
public record ScriptScene(
    String sceneId,
    String location,
    String time,
    String description,
    List<String> characters,
    List<Map<String, String>> dialogue,
    List<Map<String, String>> actions
) {}
