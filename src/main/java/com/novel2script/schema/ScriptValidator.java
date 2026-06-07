package com.novel2script.schema;

import java.util.*;

/**
 * 剧本 YAML Schema 校验器
 * <p>
 * 对解析后的 YAML 数据进行结构校验，确保符合剧本 Schema。
 */
public class ScriptValidator {

    /**
     * 校验剧本数据结构
     *
     * @param data 待校验的 YAML 数据（Map 结构）
     * @return 错误消息列表（空列表表示校验通过）
     */
    @SuppressWarnings("unchecked")
    public static List<String> validate(Map<String, Object> data) {
        List<String> errors = new ArrayList<>();

        if (data == null) {
            return List.of("根节点不能为空");
        }

        // 检查 metadata
        if (!data.containsKey("metadata")) {
            errors.add("缺少 metadata 字段");
        } else if (data.get("metadata") instanceof Map<?, ?> meta) {
            for (String key : List.of("title", "genre", "source_novel")) {
                if (!meta.containsKey(key)) {
                    errors.add("metadata 缺少 " + key + " 字段");
                }
            }
        } else {
            errors.add("metadata 必须是对象");
        }

        // 检查 chapters
        if (!data.containsKey("chapters")) {
            errors.add("缺少 chapters 字段");
        } else if (data.get("chapters") instanceof List<?> chapters) {
            for (int i = 0; i < chapters.size(); i++) {
                String prefix = "chapters[" + i + "]";
                Object chObj = chapters.get(i);

                if (!(chObj instanceof Map<?, ?> chapter)) {
                    errors.add(prefix + " 必须是对象");
                    continue;
                }

                if (!chapter.containsKey("chapter_title")) {
                    errors.add(prefix + " 缺少 chapter_title");
                }

                if (!chapter.containsKey("scenes")) {
                    errors.add(prefix + " 缺少 scenes");
                } else if (chapter.get("scenes") instanceof List<?> scenes) {
                    for (int j = 0; j < scenes.size(); j++) {
                        String scenePrefix = prefix + ".scenes[" + j + "]";
                        Object scObj = scenes.get(j);

                        if (!(scObj instanceof Map<?, ?> scene)) {
                            errors.add(scenePrefix + " 必须是对象");
                            continue;
                        }

                        for (String key : List.of("scene_id", "location", "time", "description")) {
                            if (!scene.containsKey(key)) {
                                errors.add(scenePrefix + " 缺少 " + key);
                            }
                        }

                        // 校验 dialogue 结构
                        if (scene.get("dialogue") instanceof List<?> dialogue) {
                            for (int k = 0; k < dialogue.size(); k++) {
                                if (dialogue.get(k) instanceof Map<?, ?> d) {
                                    if (!d.containsKey("speaker")) {
                                        errors.add(scenePrefix + ".dialogue[" + k + "] 缺少 speaker");
                                    }
                                    if (!d.containsKey("line")) {
                                        errors.add(scenePrefix + ".dialogue[" + k + "] 缺少 line");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            errors.add("chapters 必须是数组");
        }

        return errors;
    }
}
