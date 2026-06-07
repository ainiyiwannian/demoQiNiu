package com.novel2script.parser;

import com.novel2script.model.Chapter;
import com.novel2script.model.NovelText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("小说解析器测试")
class NovelParserTest {

    @Nested
    @DisplayName("章节模式检测")
    class DetectChapterPattern {

        @Test
        @DisplayName("中文章节标题")
        void chineseChapterPattern() {
            String text = "第一章 开始\n内容\n第二章 发展\n内容\n第三章 高潮\n内容";
            Pattern pattern = NovelParser.detectChapterPattern(text);
            assertNotNull(pattern);
        }

        @Test
        @DisplayName("英文章节标题")
        void englishChapterPattern() {
            String text = "Chapter 1 Start\nContent\nChapter 2 Middle\nContent\nChapter 3 End\nContent";
            Pattern pattern = NovelParser.detectChapterPattern(text);
            assertNotNull(pattern);
        }

        @Test
        @DisplayName("中文数字序号")
        void numberPattern() {
            String text = "一、开始\n内容\n二、发展\n内容\n三、高潮\n内容";
            Pattern pattern = NovelParser.detectChapterPattern(text);
            assertNotNull(pattern);
        }

        @Test
        @DisplayName("无章节标题时返回 null")
        void noPatternDetected() {
            String text = "这是一段没有章节标题的普通文本。";
            Pattern pattern = NovelParser.detectChapterPattern(text);
            assertNull(pattern);
        }
    }

    @Nested
    @DisplayName("章节切分")
    class SplitChapters {

        @Test
        @DisplayName("正确切分三个章节")
        void splitThreeChapters() {
            String text = "第一章 开始\n这是第一章的内容。\n第二章 发展\n这是第二章的内容。\n第三章 高潮\n这是第三章的内容。";
            List<Chapter> chapters = NovelParser.splitChapters(text);
            assertEquals(3, chapters.size());
            assertEquals("第一章 开始", chapters.get(0).title());
            assertEquals("第二章 发展", chapters.get(1).title());
            assertEquals("第三章 高潮", chapters.get(2).title());
        }

        @Test
        @DisplayName("保留章节内容")
        void chapterContentPreserved() {
            String text = "第一章 测试\n这是一段很长的内容。\n第二行内容。\n第二章 测试2\n另一段内容。\n第三章 测试3\n第三段内容。";
            List<Chapter> chapters = NovelParser.splitChapters(text);
            assertTrue(chapters.get(0).content().contains("很长的内容"));
            assertTrue(chapters.get(1).content().contains("另一段"));
        }

        @Test
        @DisplayName("不足三章抛出异常")
        void insufficientChaptersRaises() {
            String text = "第一章 开始\n只有两章\n第二章 结束\n不够三章";
            assertThrows(IllegalArgumentException.class, () -> NovelParser.splitChapters(text));
        }

        @Test
        @DisplayName("无结构文本抛出异常")
        void noPatternRaises() {
            String text = "没有章节标题的普通文本，\n很长很长，\n但没有结构。";
            assertThrows(IllegalArgumentException.class, () -> NovelParser.splitChapters(text));
        }
    }

    @Nested
    @DisplayName("完整解析")
    class ParseNovel {

        @Test
        @DisplayName("指定标题解析")
        void parseWithTitle() {
            String text = "第一章 A\n内容A\n第二章 B\n内容B\n第三章 C\n内容C";
            NovelText novel = NovelParser.parseNovel(text, "测试小说");
            assertEquals("测试小说", novel.title());
            assertEquals(3, novel.chapters().size());
        }

        @Test
        @DisplayName("章节索引连续")
        void chapterIndexSequential() {
            String text = "第一章 X\na\n第二章 Y\nb\n第三章 Z\nc";
            NovelText novel = NovelParser.parseNovel(text, "测试");
            for (int i = 0; i < novel.chapters().size(); i++) {
                assertEquals(i + 1, novel.chapters().get(i).index());
            }
        }
    }

    @Nested
    @DisplayName("示例文件")
    class SampleNovel {

        @Test
        @DisplayName("示例小说文件可正确解析")
        void sampleNovelParses() throws IOException {
            Path samplePath = Path.of("examples/sample_novel.txt");
            if (!Files.exists(samplePath)) {
                return; // 跳过
            }

            String text = Files.readString(samplePath);
            NovelText novel = NovelParser.parseNovel(text, "夜行者");
            assertTrue(novel.chapters().size() >= 3);
            assertEquals("夜行者", novel.title());
        }
    }
}
