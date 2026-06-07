# Novel2Script — AI 辅助剧本创作工具 (Java 版)

将小说文本自动转换为结构化 YAML 剧本，让作者快速获得可编辑、可进一步打磨的剧本初稿。

## 演示视频

> 如果视频无法播放，请 [点击下载](docs/demo.mp4) 后本地观看。

## 功能特性

- **智能章节识别**：自动识别多种章节标题格式（第X章、Chapter X 等）
- **场景自动切分**：根据地点/时间变化智能划分场景
- **对话提取转换**：将间接引语转换为结构化台词，标注情绪和表演指示
- **动作提炼**：将叙述性文字转换为可执行的舞台动作描述
- **YAML 输出**：人类可读、可编辑、版本控制友好的格式
- **Schema 校验**：确保输出符合标准剧本结构

## 环境要求

- Java 17+
- Maven 3.6+

## 快速开始

### 编译打包

```bash
mvn clean package
```

### 基本用法

```bash
java -jar target/novel2script-1.0.0.jar -i <小说文件> -o <输出文件> -k <API密钥>
```

### 示例

```bash
# 使用 OpenAI API
java -jar target/novel2script-1.0.0.jar -i examples/sample_novel.txt -o script.yaml -k sk-xxx

# 使用环境变量（避免在命令行暴露密钥）
export NOVEL2SCRIPT_API_KEY=sk-xxx
java -jar target/novel2script-1.0.0.jar -i examples/sample_novel.txt -o script.yaml

# 使用兼容 OpenAI 格式的第三方 API
java -jar target/novel2script-1.0.0.jar -i novel.txt -o script.yaml -k YOUR_KEY \
  --base-url https://api.example.com/v1 --model gpt-4o

# 指定剧本类型和标题
java -jar target/novel2script-1.0.0.jar -i novel.txt -o script.yaml -k KEY \
  --genre 悬疑 --title "夜行者"

# 校验已生成的 YAML 文件
java -jar target/novel2script-1.0.0.jar -i dummy -o script.yaml -k dummy --validate-only

# 运行测试
mvn test
```

### 完整参数

| 参数 | 缩写 | 必填 | 默认值 | 环境变量 | 说明 |
|------|------|------|--------|----------|------|
| `--input` | `-i` | 是 | - | - | 输入的小说文本文件 |
| `--output` | `-o` | 否 | `script.yaml` | - | 输出的 YAML 剧本文件 |
| `--api-key` | `-k` | 是 | - | `NOVEL2SCRIPT_API_KEY` | AI API 密钥 |
| `--base-url` | - | 否 | `https://api.openai.com/v1` | `NOVEL2SCRIPT_BASE_URL` | API 基础地址 |
| `--model` | - | 否 | `gpt-4o` | `NOVEL2SCRIPT_MODEL` | AI 模型名称 |
| `--genre` | - | 否 | `剧情` | - | 剧本类型 |
| `--title` | - | 否 | 从文件名提取 | - | 小说标题 |
| `--temperature` | - | 否 | `0.7` | - | 生成温度 (0-1) |
| `--validate-only` | - | 否 | - | - | 仅校验已有 YAML |
| `--verbose` | `-v` | 否 | - | - | 显示详细日志 |
| `--help` | `-h` | 否 | - | - | 显示帮助信息 |

> **优先级**：命令行参数 > 环境变量 > `config.yaml` 配置文件

## 输出格式

输出的 YAML 文件结构如下：

```yaml
metadata:
  title: 夜行者
  genre: 悬疑
  source_novel: 夜行者
  total_chapters: 3
  total_scenes: 6
  generated_at: "2026-06-05 14:30:00"
  version: "1.0"

chapters:
  - chapter_title: 第一章 雨夜来客
    scenes:
      - scene_id: S01
        location: 城区老咖啡馆
        time: 深夜，大雨
        description: |
          昏黄的灯光，雨声不断敲打着窗户。
        characters: [林远, 老板]
        dialogue:
          - speaker: 老板
            line: 先生，我们要打烊了。
            direction: 走过来，语气中带着歉意
        actions:
          - character: 林远
            action: 付账离开
            description: 从口袋掏出钞票放在桌上，拿起旧伞推门走进雨里
```

## 输入要求

- 小说文本为纯文本格式（`.txt`）
- **至少包含 3 个章节**
- 支持的章节标题格式：
  - 中文：`第X章`、`第X回`、`第X节`
  - 英文：`Chapter X`
  - 数字：`一、标题`、`1. 标题`
  - 书名号：`《标题》`

## 项目结构

```
src/main/java/com/novel2script/
├── App.java                     # CLI 入口
├── model/                       # 数据模型 (Java 17 record)
│   ├── Chapter.java             #   小说章节
│   ├── NovelText.java           #   小说文本
│   ├── ScriptScene.java         #   剧本场景
│   ├── ScriptChapter.java       #   剧本章节
│   └── ConversionConfig.java    #   AI 配置
├── parser/
│   └── NovelParser.java         # 小说文本解析器
├── converter/
│   └── NovelConverter.java      # AI 转换器 (HttpClient + Jackson)
├── builder/
│   └── YamlBuilder.java         # YAML 生成器 (SnakeYAML)
└── schema/
    └── ScriptValidator.java     # Schema 校验器

schemas/
└── script_schema.yaml           # YAML Schema 定义 (含设计说明)

examples/
├── sample_novel.txt             # 示例小说 (3 章悬疑短篇)
└── sample_output.yaml           # 示例剧本输出
```

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 17 (record, text blocks, pattern matching) |
| 构建 | Maven |
| YAML | SnakeYAML 2.2 |
| JSON | Jackson 2.17 |
| HTTP | Java 内置 HttpClient |
| 测试 | JUnit 5 |

---

## YAML Schema 设计说明

### 设计目标

本 Schema 为「小说 → 剧本」转换提供统一的结构化数据格式，满足以下核心需求：

1. **完整性**：覆盖剧本创作的所有关键要素（场景、对话、动作、情绪）
2. **可编辑性**：作者拿到 YAML 后可直接用文本编辑器修改，无需专业工具
3. **可扩展性**：支持添加注释、备注、自定义字段，适应不同创作需求
4. **机器可读**：结构化数据可被后续工具（排版、分镜、字幕软件）直接消费
5. **中文友好**：YAML 天然支持 Unicode，中文内容无需转义

### 为什么选择 YAML？

| 特性 | 优势 |
|------|------|
| 人类可读 | 比 JSON 更易读，支持注释，适合手写编辑 |
| 层级清晰 | 天然表达「章节 → 场景 → 元素」的层级结构 |
| 多行文本 | 使用 `\|` 或 `>` 轻松处理长段落台词或场景描述 |
| 生态成熟 | 几乎所有编程语言都有 YAML 解析库 |
| 版本友好 | 文本格式，适合 Git 追踪修改历史 |

### 核心设计理念

#### 1. 以「场景 (Scene)」为最小叙事单元

剧本的本质是场景序列。每个场景绑定一个固定的时空坐标（地点 + 时间），所有对话和动作都在这个坐标下发生。这与影视制作中"一场戏"的概念完全一致。

#### 2. 对话与动作分离

将 `dialogue`（台词）和 `actions`（舞台动作）作为独立数组，而非嵌套在一起。好处是：

- **导演**可以快速浏览所有台词，评估节奏
- **动作指导**可以单独查看所有需要编排的动作
- 两者可以**独立编辑**，互不干扰

#### 3. 情绪与指示附加在台词上

每句台词可选携带 `emotion`（情绪）和 `direction`（表演指示）。这是小说改编为剧本的关键——原文中的心理描写需要转化为可表演的指示。设为可选字段，避免过度标注，允许渐进式完善。

#### 4. 元数据与内容分离

顶层 `metadata` 存储作品信息和统计，`chapters` 存储内容。工具可以快速读取基本信息而无需解析全部内容。

### Schema 层级结构

```
根节点
├── metadata                    # 元数据
│   ├── title                   # 剧本标题
│   ├── genre                   # 类型/题材
│   ├── source_novel            # 原著名称
│   ├── total_chapters          # 总章节数
│   ├── total_scenes            # 总场景数
│   ├── generated_at            # 生成时间
│   └── version                 # Schema 版本
│
└── chapters[]                  # 章节数组
    ├── chapter_title            # 章节标题
    └── scenes[]                 # 场景数组
        ├── scene_id             # 场景编号 (S01, S02, ...)
        ├── location             # 地点
        ├── time                 # 时间
        ├── description          # 氛围描述
        ├── characters[]         # 出场人物
        ├── dialogue[]           # 对话
        │   ├── speaker          # 说话者
        │   ├── line             # 台词
        │   ├── emotion          # 情绪（可选）
        │   └── direction        # 表演指示（可选）
        └── actions[]            # 动作
            ├── character        # 人物
            ├── action           # 动作类型
            └── description      # 动作描述
```

完整的 Schema 定义及每个字段的设计理由，请参见 [`schemas/script_schema.yaml`](schemas/script_schema.yaml)。
