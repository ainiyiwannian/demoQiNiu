package com.novel2script.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 小说 → 剧本 AI 转换器
 * <p>
 * 调用 OpenAI 兼容 API，将小说章节转换为结构化剧本数据。
 */
public class NovelConverter {

    private static final Logger LOG = Logger.getLogger(NovelConverter.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是一位专业的剧本改编专家。你的任务是将小说文本转换为结构化的剧本格式。

            请严格按以下 JSON 格式输出，不要添加任何额外解释：

            {
              "chapter_title": "章节标题",
              "scenes": [
                {
                  "scene_id": "S01",
                  "location": "场景地点",
                  "time": "时间（如：白天、夜晚、清晨）",
                  "description": "场景的视觉/氛围描述（用于舞台指示）",
                  "characters": ["出场人物1", "出场人物2"],
                  "dialogue": [
                    {
                      "speaker": "说话者姓名",
                      "line": "台词内容",
                      "emotion": "情绪/语气（如：愤怒、平静、颤抖）",
                      "direction": "表演指示（如：转身、低头、握拳）"
                    }
                  ],
                  "actions": [
                    {
                      "character": "执行动作的人物",
                      "action": "动作类型（如：走、打、跑）",
                      "description": "动作的详细描述"
                    }
                  ]
                }
              ]
            }

            转换规则：
            1. 场景划分：根据地点或时间变化切分场景。每个独立的时空环境是一个场景。
            2. 对话提取：将小说中的间接引语转换为直接台词，保留人物语气和情绪。
            3. 动作提炼：将叙述性文字转换为可执行的舞台动作描述。
            4. 人物识别：识别所有出场人物，统一命名（避免同一人物多种称呼）。
            5. 情绪标注：根据上下文为每句台词标注情绪和表演指示。
            6. 场景描述：将环境描写转换为舞台指示，保留氛围感。
            7. scene_id 格式为 S01, S02, ...，每个章节内独立编号。
            8. 如果原文是对话驱动的，保持原有对话结构；如果是叙述驱动的，创造性地转换为戏剧化场景。""";

    private static final String USER_PROMPT_TEMPLATE = """
            请将以下小说第 %d 章转换为结构化剧本：

            章节标题：%s

            %s

            请严格按照 JSON 格式输出。""";

    private final ConversionConfig config;
    private final HttpClient httpClient;

    public NovelConverter(ConversionConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    /**
     * 调用 AI 模型
     */
    private String callAi(String userPrompt) throws Exception {
        // 构建请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", config.temperature());
        body.put("max_tokens", config.maxTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        ));

        String jsonBody = MAPPER.writeValueAsString(body);

        String url = config.baseUrl().replaceAll("/$", "") + "/chat/completions";

        Exception lastException = null;
        for (int attempt = 1; attempt <= config.maxRetries(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .timeout(java.time.Duration.ofSeconds(120))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("API 返回 HTTP " + response.statusCode() + ": " + response.body());
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = MAPPER.readValue(response.body(), new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return ((String) message.get("content")).trim();

            } catch (Exception e) {
                lastException = e;
                LOG.warning("AI 调用失败 (第 " + attempt + " 次): " + e.getMessage());
                if (attempt == config.maxRetries()) {
                    throw new RuntimeException("AI 调用失败，已重试 " + config.maxRetries() + " 次", e);
                }
            }
        }
        throw lastException;
    }

    /**
     * 解析 AI 返回的 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAiResponse(String rawResponse) {
        String text = rawResponse.strip();

        // 去掉 markdown 代码块标记
        if (text.startsWith("```")) {
            String[] lines = text.split("\n");
            int start = lines[0].startsWith("```") ? 1 : 0;
            int end = lines.length;
            for (int j = lines.length - 1; j > 0; j--) {
                if (lines[j].strip().equals("```")) {
                    end = j;
                    break;
                }
            }
            text = String.join("\n", Arrays.copyOfRange(lines, start, end));
        }

        try {
            return MAPPER.readValue(text, new TypeReference<>() {});
        } catch (Exception e) {
            LOG.severe("JSON 解析失败: " + e.getMessage() + "\n原始响应: " + rawResponse.substring(0, Math.min(500, rawResponse.length())));
            throw new IllegalArgumentException("AI 返回的内容不是有效的 JSON: " + e.getMessage());
        }
    }

    /**
     * 转换单个章节
     */
    @SuppressWarnings("unchecked")
    public ScriptChapter convertChapter(Chapter chapter) {
        String content = chapter.content();
        if (content.length() > 8000) {
            content = content.substring(0, 8000);
        }

        String userPrompt = String.format(USER_PROMPT_TEMPLATE, chapter.index(), chapter.title(), content);

        LOG.info("正在转换第 " + chapter.index() + " 章: " + chapter.title());
        String rawResponse;
        try {
            rawResponse = callAi(userPrompt);
        } catch (Exception e) {
            throw new RuntimeException("第 " + chapter.index() + " 章 AI 调用失败", e);
        }

        Map<String, Object> data = parseAiResponse(rawResponse);

        // 构建 scenes
        List<ScriptScene> scenes = new ArrayList<>();
        List<Map<String, Object>> sceneList = (List<Map<String, Object>>) data.getOrDefault("scenes", List.of());
        for (Map<String, Object> sd : sceneList) {
            scenes.add(new ScriptScene(
                    (String) sd.getOrDefault("scene_id", "S01"),
                    (String) sd.getOrDefault("location", "未知"),
                    (String) sd.getOrDefault("time", "未知"),
                    (String) sd.getOrDefault("description", ""),
                    toStringList(sd.get("characters")),
                    toMapList(sd.get("dialogue")),
                    toMapList(sd.get("actions"))
            ));
        }

        String title = (String) data.getOrDefault("chapter_title", chapter.title());
        return new ScriptChapter(title, scenes);
    }

    /**
     * 转换整部小说（并行转换所有章节）
     */
    public List<ScriptChapter> convertNovel(NovelText novel) {
        List<Chapter> chapters = novel.chapters();
        int size = chapters.size();

        // 并行发起所有章节的 AI 请求
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(size, 4));
        CompletableFuture<ScriptChapter>[] futures = new CompletableFuture[size];

        for (int i = 0; i < size; i++) {
            Chapter chapter = chapters.get(i);
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    return convertChapter(chapter);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "第 " + chapter.index() + " 章转换失败", e);
                    ScriptScene placeholder = new ScriptScene(
                            "S01", "未知", "未知",
                            "本章转换失败: " + e.getMessage(),
                            List.of(), List.of(), List.of()
                    );
                    return new ScriptChapter(
                            "[转换失败] " + chapter.title(),
                            List.of(placeholder)
                    );
                }
            }, executor);
        }

        // 等待全部完成，按原始顺序收集结果
        CompletableFuture.allOf(futures).join();
        executor.shutdown();

        List<ScriptChapter> results = new ArrayList<>(size);
        for (CompletableFuture<ScriptChapter> f : futures) {
            results.add(f.join());
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> toMapList(Object obj) {
        if (obj instanceof List<?> list) {
            List<Map<String, String>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> strMap = new LinkedHashMap<>();
                    map.forEach((k, v) -> strMap.put(k.toString(), v != null ? v.toString() : ""));
                    result.add(strMap);
                }
            }
            return result;
        }
        return List.of();
    }
}
