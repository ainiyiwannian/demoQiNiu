package com.novel2script;

import com.novel2script.builder.YamlBuilder;
import com.novel2script.converter.NovelConverter;
import com.novel2script.model.*;
import com.novel2script.parser.NovelParser;
import com.novel2script.schema.ScriptValidator;
import org.yaml.snakeyaml.Yaml;

import com.novel2script.web.WebServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Novel2Script CLI 入口
 * <p>
 * AI 辅助剧本创作工具 — 将小说文本自动转换为结构化 YAML 剧本
 */
public class App {

    private String inputFile;
    private String outputFile = "script.yaml";
    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o";
    private String genre = "剧情";
    private String title;
    private double temperature = 0.7;
    private boolean validateOnly = false;
    private boolean verbose = false;
    private boolean serverMode = false;
    private int serverPort = 8080;

    private Map<String, String> config = new HashMap<>();

    public static void main(String[] args) {
        App app = new App();
        app.loadConfig();
        try {
            app.parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("错误: " + e.getMessage());
            printUsage();
            System.exit(1);
        }

        if (app.serverMode) {
            app.startWebServer();
        } else if (app.validateOnly) {
            app.validateCommand();
        } else {
            app.convertCommand();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        // 查找顺序：当前目录 → jar 同目录 → classpath
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
                            if (v != null) config.put(k, v.toString());
                        });
                    }
                    if (verbose) System.out.println("已加载配置文件: " + path.toAbsolutePath());
                    return;
                } catch (IOException e) {
                    System.err.println("警告: 读取配置文件失败 — " + e.getMessage());
                }
            }
        }

        // classpath 兜底
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.yaml")) {
            if (is != null) {
                String content = new String(is.readAllBytes());
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(content);
                if (data != null) {
                    data.forEach((k, v) -> {
                        if (v != null) config.put(k, v.toString());
                    });
                }
            }
        } catch (IOException e) {
            // 静默忽略
        }
    }

    private void parseArgs(String[] args) {
        // 先从配置文件应用默认值
        if (config.containsKey("base-url")) baseUrl = config.get("base-url");
        if (config.containsKey("model")) model = config.get("model");
        if (config.containsKey("api-key")) apiKey = config.get("api-key");
        if (config.containsKey("genre")) genre = config.get("genre");
        if (config.containsKey("temperature")) temperature = Double.parseDouble(config.get("temperature"));

        // 环境变量覆盖配置文件（优先级：CLI > ENV > config.yaml）
        String envKey = System.getenv("NOVEL2SCRIPT_API_KEY");
        if (envKey != null && !envKey.isBlank()) apiKey = envKey;
        String envUrl = System.getenv("NOVEL2SCRIPT_BASE_URL");
        if (envUrl != null && !envUrl.isBlank()) baseUrl = envUrl;
        String envModel = System.getenv("NOVEL2SCRIPT_MODEL");
        if (envModel != null && !envModel.isBlank()) model = envModel;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    printUsage();
                    System.exit(0);
                }
                case "-i", "--input" -> inputFile = nextArg(args, ++i, "input");
                case "-o", "--output" -> outputFile = nextArg(args, ++i, "output");
                case "-k", "--api-key" -> apiKey = nextArg(args, ++i, "api-key");
                case "--base-url" -> baseUrl = nextArg(args, ++i, "base-url");
                case "--model" -> model = nextArg(args, ++i, "model");
                case "--genre" -> genre = nextArg(args, ++i, "genre");
                case "--title" -> title = nextArg(args, ++i, "title");
                case "--temperature" -> temperature = Double.parseDouble(nextArg(args, ++i, "temperature"));
                case "--validate-only" -> validateOnly = true;
                case "-v", "--verbose" -> verbose = true;
                case "--server" -> {
                    serverMode = true;
                    // 检查下一个参数是否是端口号
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        try {
                            serverPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            // 不是数字，回退
                        }
                    }
                }
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }

        // Server 模式不需要输入文件和 API Key
        if (serverMode) {
            return;
        }

        if (inputFile == null) {
            throw new IllegalArgumentException("缺少必需参数: -i/--input");
        }
        if (!validateOnly && apiKey == null) {
            throw new IllegalArgumentException("缺少必需参数: -k/--api-key");
        }
    }

    private String nextArg(String[] args, int i, String name) {
        if (i >= args.length) {
            throw new IllegalArgumentException("参数 " + name + " 缺少值");
        }
        return args[i];
    }

    /**
     * 校验已有 YAML 文件
     */
    @SuppressWarnings("unchecked")
    private void validateCommand() {
        Path path = Path.of(outputFile);
        if (!Files.exists(path)) {
            System.err.println("错误: 文件不存在 — " + outputFile);
            System.exit(1);
        }

        try {
            String content = Files.readString(path);
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(content);

            List<String> errors = ScriptValidator.validate(data);
            if (!errors.isEmpty()) {
                System.out.println("[FAIL] 校验失败，发现以下问题：");
                for (String err : errors) {
                    System.out.println("  - " + err);
                }
                System.exit(1);
            } else {
                System.out.println("[OK] YAML 文件格式正确，符合剧本 Schema。");
                Map<String, Object> meta = (Map<String, Object>) data.get("metadata");
                if (meta != null) {
                    System.out.println("  标题: " + meta.getOrDefault("title", "未知"));
                    System.out.println("  章节数: " + meta.getOrDefault("total_chapters", "?"));
                    System.out.println("  场景数: " + meta.getOrDefault("total_scenes", "?"));
                }
            }
        } catch (IOException e) {
            System.err.println("错误: 读取文件失败 — " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 执行小说 → 剧本转换
     */
    private void convertCommand() {
        Path inputPath = Path.of(inputFile);
        if (!Files.exists(inputPath)) {
            System.err.println("错误: 输入文件不存在 — " + inputFile);
            System.exit(1);
        }

        // 读取输入文件
        String text;
        try {
            text = Files.readString(inputPath);
        } catch (IOException e) {
            System.err.println("错误: 读取文件失败 — " + e.getMessage());
            System.exit(1);
            return;
        }

        // 解析小说
        System.out.println("解析小说结构...");
        String novelTitle = (title != null && !title.isBlank()) ? title : inputPath.getFileName().toString();
        NovelText novel;
        try {
            novel = NovelParser.parseNovel(text, novelTitle);
        } catch (IllegalArgumentException e) {
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("[OK] 识别到 " + novel.chapters().size() + " 个章节:");
        for (Chapter ch : novel.chapters()) {
            System.out.printf("  [%d] %s (%d 字)%n", ch.index(), ch.title(), ch.content().length());
        }

        // 配置 AI 转换器
        ConversionConfig config = new ConversionConfig(apiKey, baseUrl, model, temperature, 4096, 3);
        NovelConverter converter = new NovelConverter(config);

        // 执行转换
        System.out.println("\n开始转换（模型: " + model + "）...");
        List<ScriptChapter> scriptChapters = converter.convertNovel(novel);

        // 生成 YAML
        String yamlStr = YamlBuilder.buildScriptYaml(novel, scriptChapters, outputFile, genre);
        System.out.println("\n[OK] 剧本已生成: " + outputFile);

        // 校验输出
        try {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = yaml.load(yamlStr);
            List<String> errors = ScriptValidator.validate(data);
            if (!errors.isEmpty()) {
                System.out.println("[WARN] 输出校验发现 " + errors.size() + " 个问题:");
                for (String err : errors) {
                    System.out.println("  - " + err);
                }
            } else {
                System.out.println("[OK] 输出 YAML 通过 Schema 校验");
            }
        } catch (Exception e) {
            System.out.println("[WARN] 输出校验失败: " + e.getMessage());
        }

        // 统计
        int totalScenes = scriptChapters.stream().mapToInt(ch -> ch.scenes().size()).sum();
        int totalDialogue = scriptChapters.stream()
                .flatMap(ch -> ch.scenes().stream())
                .mapToInt(s -> s.dialogue().size())
                .sum();

        System.out.println("\n统计:");
        System.out.println("  章节数: " + scriptChapters.size());
        System.out.println("  场景数: " + totalScenes);
        System.out.println("  对话数: " + totalDialogue);
    }

    /**
     * 启动 Web 服务器模式
     */
    private void startWebServer() {
        try {
            WebServer webServer = new WebServer(serverPort);
            webServer.start();

            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n正在关闭服务器...");
                webServer.stop();
            }));

            // 保持主线程运行
            Thread.currentThread().join();
        } catch (IOException e) {
            System.err.println("错误: 启动 Web 服务器失败 — " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            // 正常退出
        }
    }

    private static void printUsage() {
        System.out.println("""
                用法: novel2script -i <小说文件> -o <输出文件> -k <API密钥> [选项]

                必需参数:
                  -i, --input <文件>       输入的小说文本文件路径
                  -k, --api-key <密钥>     AI API 密钥

                可选参数:
                  -o, --output <文件>      输出的 YAML 剧本文件 (默认: script.yaml)
                  --base-url <地址>        API 基础地址 (默认: https://api.openai.com/v1)
                  --model <模型>           AI 模型名称 (默认: gpt-4o)
                  --genre <类型>           剧本类型 (默认: 剧情)
                  --title <标题>           小说标题 (默认从文件名提取)
                  --temperature <值>       生成温度 0-1 (默认: 0.7)
                  --validate-only          仅校验已有的 YAML 文件 (不需要 API)
                  --server [端口]          启动 Web 服务器模式 (默认端口: 8080)
                  -v, --verbose            显示详细日志
                  -h, --help               显示帮助

                环境变量（优先级低于 CLI 参数，高于 config.yaml）:
                  NOVEL2SCRIPT_API_KEY    API 密钥
                  NOVEL2SCRIPT_BASE_URL   API 基础地址
                  NOVEL2SCRIPT_MODEL      模型名称

                示例:
                  java -jar novel2script.jar -i novel.txt -o script.yaml -k YOUR_API_KEY

                  # 使用环境变量（避免在命令行暴露密钥）
                  export NOVEL2SCRIPT_API_KEY=sk-xxx
                  java -jar novel2script.jar -i novel.txt -o script.yaml

                  java -jar novel2script.jar -i novel.txt -o script.yaml -k KEY \\
                    --base-url https://api.example.com/v1 --model gpt-4o

                  java -jar novel2script.jar --server          # 启动 Web 服务器 (端口 8080)
                  java -jar novel2script.jar --server 9090     # 启动 Web 服务器 (端口 9090)

                  java -jar novel2script.jar -i dummy -o script.yaml -k dummy --validate-only
                """);
    }
}
