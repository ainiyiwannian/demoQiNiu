package com.novel2script.parser;

import com.novel2script.model.Chapter;
import com.novel2script.model.NovelText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说文本解析器
 * <p>
 * 负责将原始小说文本切分为章节，支持多种章节标题格式。
 */
public class NovelParser {

    /**
     * 章节标题匹配模式（按优先级排序）
     */
    private static final List<Pattern> CHAPTER_PATTERNS = List.of(
            // 中文：第X章、第X回、第X节、第X幕、第X场
            Pattern.compile("^(第[零一二三四五六七八九十百千万\\d]+[章回节幕场]\\s*.*)$", Pattern.MULTILINE),
            // 中文带书名号
            Pattern.compile("^(《.*》)$", Pattern.MULTILINE),
            // 英文：Chapter X, CHAPTER X
            Pattern.compile("^(Chapter\\s+\\d+.*)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
            // 中文数字序号：一、标题
            Pattern.compile("^([一二三四五六七八九十]+[、.]\\s*.*)$", Pattern.MULTILINE),
            // 阿拉伯数字序号：1. 标题
            Pattern.compile("^(\\d{1,3}[、.]\\s*.*)$", Pattern.MULTILINE),
            // 分隔线风格
            Pattern.compile("^([-*_]{3,}\\s*.*)$", Pattern.MULTILINE)
    );

    /**
     * 检测文本中使用的章节标题模式
     *
     * @param text 小说文本
     * @return 匹配的 Pattern，未找到返回 null
     */
    public static Pattern detectChapterPattern(String text) {
        for (Pattern pattern : CHAPTER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            if (count >= 2) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * 将小说文本按章节切分
     *
     * @param text 完整的小说文本
     * @return 章节列表
     * @throws IllegalArgumentException 无法识别章节结构或章节数不足
     */
    public static List<Chapter> splitChapters(String text) {
        Pattern pattern = detectChapterPattern(text);

        if (pattern == null) {
            // 兜底策略：按双换行切分
            String[] blocks = text.split("\\n{2,}");
            List<String> nonEmpty = new ArrayList<>();
            for (String block : blocks) {
                String trimmed = block.trim();
                if (!trimmed.isEmpty()) {
                    nonEmpty.add(trimmed);
                }
            }
            if (nonEmpty.size() < 3) {
                throw new IllegalArgumentException(
                        "无法识别章节结构。请确保文本包含至少 3 个章节标题，" +
                        "或使用明确的章节分隔（如'第X章'、'Chapter X'）。"
                );
            }
            List<Chapter> chapters = new ArrayList<>();
            for (int i = 0; i < nonEmpty.size(); i++) {
                String block = nonEmpty.get(i);
                String[] lines = block.split("\\n", 2);
                String title = lines[0].trim();
                String content = lines.length > 1 ? lines[1].trim() : "";
                chapters.add(new Chapter(i + 1, title, content, block));
            }
            return chapters;
        }

        // 按模式切分
        Matcher matcher = pattern.matcher(text);
        List<int[]> matches = new ArrayList<>(); // [start, end] of each match
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end(), matcher.group().trim().length()});
        }

        List<Chapter> chapters = new ArrayList<>();
        int chapterIndex = 0;

        for (int i = 0; i < matches.size(); i++) {
            int[] match = matches.get(i);
            String title = text.substring(match[0], match[0] + match[2]).trim();

            // 内容是当前标题结束到下一个标题开始之间的文本
            int contentStart = match[0] + match[2];
            int contentEnd = (i + 1 < matches.size()) ? matches.get(i + 1)[0] : text.length();
            String content = text.substring(contentStart, contentEnd).trim();

            chapterIndex++;
            String rawText = title + "\n\n" + content;
            chapters.add(new Chapter(chapterIndex, title, content, rawText));
        }

        // 如果第一个标题之前有文本，作为序章
        if (!matches.isEmpty() && matches.get(0)[0] > 0) {
            String prologue = text.substring(0, matches.get(0)[0]).trim();
            if (!prologue.isEmpty()) {
                chapterIndex++;
                chapters.add(0, new Chapter(chapterIndex, "序章", prologue, prologue));
                // 重新编号
                List<Chapter> reindexed = new ArrayList<>();
                for (int i = 0; i < chapters.size(); i++) {
                    Chapter ch = chapters.get(i);
                    reindexed.add(new Chapter(i + 1, ch.title(), ch.content(), ch.rawText()));
                }
                return validateChapterCount(reindexed);
            }
        }

        return validateChapterCount(chapters);
    }

    private static List<Chapter> validateChapterCount(List<Chapter> chapters) {
        if (chapters.size() < 3) {
            throw new IllegalArgumentException(
                    "仅识别到 " + chapters.size() + " 个章节，至少需要 3 个章节。" +
                    "请检查文本格式或使用明确的章节标题。"
            );
        }
        return chapters;
    }

    /**
     * 解析完整的小说文本
     *
     * @param text  完整的小说文本
     * @param title 小说标题（null 则自动提取）
     * @return 解析后的 NovelText 对象
     */
    public static NovelText parseNovel(String text, String title) {
        // 预处理：规范化换行符
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // 自动提取标题
        if (title == null || title.isBlank()) {
            title = "未命名小说";
            String[] lines = text.strip().split("\\n");
            if (lines.length > 0) {
                String firstLine = lines[0].trim();
                if (firstLine.length() < 50 && !firstLine.startsWith("第")) {
                    title = firstLine;
                }
            }
        }

        List<Chapter> chapters = splitChapters(text);
        return new NovelText(title, chapters);
    }
}
