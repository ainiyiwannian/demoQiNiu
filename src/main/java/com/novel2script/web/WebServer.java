package com.novel2script.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.builder.YamlBuilder;
import com.novel2script.converter.NovelConverter;
import com.novel2script.model.*;
import com.novel2script.parser.NovelParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * 嵌入式 Web 服务器
 * <p>
 * 提供前端页面和转换 API，无需额外依赖。
 */
public class WebServer {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int port;
    private HttpServer server;

    public WebServer(int port) {
        this.port = port;
    }

    /**
     * 启动 Web 服务器
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // 静态页面
        server.createContext("/", new StaticPageHandler());

        // API 接口
        server.createContext("/api/convert", new ConvertHandler());

        // 健康检查
        server.createContext("/api/health", exchange -> {
            String response = "{\"status\":\"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // 配置读取接口
        server.createContext("/api/config", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            try {
                Map<String, Object> config = loadConfig();
                // 不返回 api-key 的完整值，只返回是否已配置
                boolean keySet = false;
                // 检查环境变量
                String envKey = System.getenv("NOVEL2SCRIPT_API_KEY");
                if (envKey != null && !envKey.isBlank()) {
                    keySet = true;
                }
                // 检查配置文件
                if (!keySet && config.containsKey("api-key")) {
                    String key = (String) config.get("api-key");
                    if (key != null && !key.isBlank() && !key.contains("填写")) {
                        keySet = true;
                    }
                }
                config.put("apiKeySet", keySet);
                config.remove("api-key"); // 不向前端暴露完整 key
                String json = MAPPER.writeValueAsString(config);
                exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                String json = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.sendResponseHeaders(500, json.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         Novel2Script Web 服务器已启动               ║");
        System.out.println("║                                                     ║");
        System.out.println("║   请在浏览器中打开: http://localhost:" + port + "            ║");
        System.out.println("║                                                     ║");
        System.out.println("║   按 Ctrl+C 停止服务                                ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 读取配置文件
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 查找配置文件
        Path[] candidates = {
                Path.of("config.yaml"),
                Path.of(System.getProperty("user.dir"), "config.yaml")
        };

        for (Path path : candidates) {
            if (Files.exists(path)) {
                try {
                    String content = Files.readString(path);
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(content);
                    if (data != null) {
                        data.forEach((k, v) -> {
                            if (v != null) result.put(k, v.toString());
                        });
                    }
                    return result;
                } catch (IOException e) {
                    LOG.warning("读取配置文件失败: " + e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 静态页面处理器
     */
    private static class StaticPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // 默认返回 index.html
            if ("/".equals(path)) {
                path = "/index.html";
            }

            // 从 classpath 加载静态资源（去掉开头的 /）
            String resourcePath = "static" + path;
            if (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }

            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);

            if (is == null) {
                // 尝试用 ClassLoader 再找一次
                is = WebServer.class.getClassLoader().getResourceAsStream(resourcePath);
            }

            // classpath 找不到时，尝试从文件系统读取（如 examples/ 目录）
            if (is == null) {
                Path filePath = Path.of(path.substring(1)); // 去掉开头的 /
                if (Files.exists(filePath)) {
                    is = Files.newInputStream(filePath);
                }
            }

            if (is == null) {
                String msg = "404 Not Found: " + path;
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, msg.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            // 确定 Content-Type
            String contentType = "text/html; charset=utf-8";
            if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=utf-8";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            byte[] data = is.readAllBytes();
            is.close();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }

    /**
     * 转换 API 处理器
     */
    private static class ConvertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS 支持
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("success", false, "error", "仅支持 POST 请求"));
                return;
            }

            try {
                // 读取请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> request = MAPPER.readValue(body, Map.class);

                // 读取配置文件默认值
                Map<String, Object> fileConfig = loadConfig();

                // 提取参数（优先使用请求参数，其次配置文件）
                String title = (String) request.getOrDefault("title", "");
                String genre = (String) request.getOrDefault("genre",
                        fileConfig.getOrDefault("genre", "剧情").toString());
                String text = (String) request.getOrDefault("text", "");
                String apiKey = (String) request.getOrDefault("apiKey", "");
                String baseUrl = (String) request.getOrDefault("baseUrl",
                        fileConfig.getOrDefault("base-url", "https://api.openai.com/v1").toString());
                String model = (String) request.getOrDefault("model",
                        fileConfig.getOrDefault("model", "gpt-4o").toString());
                double temperature = request.containsKey("temperature")
                        ? Double.parseDouble(request.get("temperature").toString())
                        : Double.parseDouble(fileConfig.getOrDefault("temperature", "0.7").toString());

                // 如果请求中没有 API Key，尝试从环境变量或配置文件读取
                if (apiKey == null || apiKey.isBlank()) {
                    String envKey = System.getenv("NOVEL2SCRIPT_API_KEY");
                    if (envKey != null && !envKey.isBlank()) {
                        apiKey = envKey;
                    } else {
                        String configKey = (String) fileConfig.get("api-key");
                        if (configKey != null && !configKey.isBlank() && !configKey.contains("填写")) {
                            apiKey = configKey;
                        }
                    }
                }

                // 参数校验
                if (text == null || text.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "error", "请输入小说文本"));
                    return;
                }
                if (apiKey == null || apiKey.isBlank()) {
                    sendJson(exchange, 400, Map.of("success", false, "error", "请输入 API Key"));
                    return;
                }

                // 解析小说
                NovelText novel;
                try {
                    novel = NovelParser.parseNovel(text, title);
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, Map.of("success", false, "error", "解析失败: " + e.getMessage()));
                    return;
                }

                // 配置 AI 转换器
                ConversionConfig config = new ConversionConfig(apiKey, baseUrl, model, temperature, 4096, 3);
                NovelConverter converter = new NovelConverter(config);

                // 执行转换
                List<ScriptChapter> scriptChapters = converter.convertNovel(novel);

                // 生成 YAML
                String yamlStr = YamlBuilder.buildScriptYaml(novel, scriptChapters, null, genre);

                // 统计
                int totalScenes = scriptChapters.stream().mapToInt(ch -> ch.scenes().size()).sum();
                int totalDialogue = scriptChapters.stream()
                        .flatMap(ch -> ch.scenes().stream())
                        .mapToInt(s -> s.dialogue().size())
                        .sum();

                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("chapters", scriptChapters.size());
                stats.put("scenes", totalScenes);
                stats.put("dialogue", totalDialogue);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("yaml", yamlStr);
                result.put("stats", stats);

                sendJson(exchange, 200, result);

            } catch (Exception e) {
                LOG.severe("转换失败: " + e.getMessage());
                sendJson(exchange, 500, Map.of("success", false, "error", "服务器内部错误: " + e.getMessage()));
            }
        }

        private void sendJson(HttpExchange exchange, int code, Map<String, Object> data) throws IOException {
            String json = MAPPER.writeValueAsString(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
