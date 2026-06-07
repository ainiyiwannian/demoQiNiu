package com.novel2script.builder;

import com.novel2script.model.NovelText;
import com.novel2script.model.ScriptChapter;
import com.novel2script.model.ScriptScene;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * YAML 剧本生成器
 * <p>
 * 将转换后的剧本数据构建为符合 Schema 的 YAML 格式。
 */
public class YamlBuilder {

    /**
     * 构建完整的剧本 YAML
     *
     * @param novel      原始小说解析数据
     * @param chapters   转换后的剧本章节
     * @param outputPath 输出文件路径（null 则不写文件）
     * @param genre      剧本类型
     * @return YAML 字符串
     */
    public static String buildScriptYaml(NovelText novel, List<ScriptChapter> chapters,
                                         String outputPath, String genre) {
        // 构建 metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", novel.title());
        metadata.put("genre", genre);
        metadata.put("source_novel", novel.title());
        metadata.put("total_chapters", chapters.size());
        metadata.put("total_scenes", chapters.stream().mapToInt(ch -> ch.scenes().size()).sum());
        metadata.put("generated_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        metadata.put("version", "1.0");

        // 构建 chapters
        List<Map<String, Object>> chapterList = new ArrayList<>();
        for (ScriptChapter ch : chapters) {
            chapterList.add(chapterToMap(ch));
        }

        // 组装根节点
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadata", metadata);
        root.put("chapters", chapterList);

        // 配置 YAML 输出
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        options.setWidth(120);
        options.setPrettyFlow(true);

        Representer representer = new Representer(options);
        // 不输出 null 值
        representer.getPropertyUtils().setSkipMissingProperties(true);

        Yaml yaml = new Yaml(representer, options);
        StringWriter writer = new StringWriter();
        yaml.dump(root, writer);
        String yamlStr = writer.toString();

        // 写入文件
        if (outputPath != null && !outputPath.isBlank()) {
            try (FileWriter fw = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
                fw.write(yamlStr);
            } catch (IOException e) {
                throw new RuntimeException("写入文件失败: " + outputPath, e);
            }
        }

        return yamlStr;
    }

    private static Map<String, Object> chapterToMap(ScriptChapter chapter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chapter_title", chapter.chapterTitle());

        List<Map<String, Object>> sceneList = new ArrayList<>();
        for (ScriptScene scene : chapter.scenes()) {
            sceneList.add(sceneToMap(scene));
        }
        map.put("scenes", sceneList);
        return map;
    }

    private static Map<String, Object> sceneToMap(ScriptScene scene) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scene_id", scene.sceneId());
        map.put("location", scene.location());
        map.put("time", scene.time());
        map.put("description", scene.description());

        if (scene.characters() != null && !scene.characters().isEmpty()) {
            map.put("characters", scene.characters());
        }

        if (scene.dialogue() != null && !scene.dialogue().isEmpty()) {
            List<Map<String, String>> dialogueList = new ArrayList<>();
            for (Map<String, String> d : scene.dialogue()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("speaker", d.getOrDefault("speaker", ""));
                entry.put("line", d.getOrDefault("line", ""));
                if (d.containsKey("emotion") && !d.get("emotion").isEmpty()) {
                    entry.put("emotion", d.get("emotion"));
                }
                if (d.containsKey("direction") && !d.get("direction").isEmpty()) {
                    entry.put("direction", d.get("direction"));
                }
                dialogueList.add(entry);
            }
            map.put("dialogue", dialogueList);
        }

        if (scene.actions() != null && !scene.actions().isEmpty()) {
            List<Map<String, String>> actionList = new ArrayList<>();
            for (Map<String, String> a : scene.actions()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("character", a.getOrDefault("character", ""));
                entry.put("action", a.getOrDefault("action", ""));
                entry.put("description", a.getOrDefault("description", ""));
                actionList.add(entry);
            }
            map.put("actions", actionList);
        }

        return map;
    }
}
