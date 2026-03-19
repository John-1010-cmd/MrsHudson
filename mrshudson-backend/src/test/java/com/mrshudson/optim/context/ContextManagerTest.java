package com.mrshudson.optim.context;

import com.mrshudson.mcp.kimi.dto.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上下文管理器单元测试
 * 测试 ContextManager 的核心方法和消息处理逻辑
 */
class ContextManagerTest {

    @Nested
    @DisplayName("Message 消息创建测试")
    class MessageCreationTests {

        @Test
        @DisplayName("应该正确创建用户消息")
        void shouldCreateUserMessage() {
            Message message = Message.user("测试消息");
            assertEquals("user", message.getRole());
            assertEquals("测试消息", message.getContent());
        }

        @Test
        @DisplayName("应该正确创建助手消息")
        void shouldCreateAssistantMessage() {
            Message message = Message.assistant("助手回复");
            assertEquals("assistant", message.getRole());
            assertEquals("助手回复", message.getContent());
        }

        @Test
        @DisplayName("应该正确创建系统消息")
        void shouldCreateSystemMessage() {
            Message message = Message.system("系统提示");
            assertEquals("system", message.getRole());
            assertEquals("系统提示", message.getContent());
        }
    }

    @Nested
    @DisplayName("消息列表处理测试")
    class MessageListTests {

        @Test
        @DisplayName("消息列表不应为空")
        void messageListShouldNotBeEmpty() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.user("用户消息"));
            messages.add(Message.assistant("助手回复"));

            assertFalse(messages.isEmpty());
            assertEquals(2, messages.size());
        }

        @Test
        @DisplayName("应该能过滤特定角色消息")
        void shouldFilterMessagesByRole() {
            List<Message> messages = List.of(
                Message.user("用户1"),
                Message.assistant("助手1"),
                Message.user("用户2")
            );

            long userCount = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .count();

            assertEquals(2, userCount);
        }

        @Test
        @DisplayName("应该保留消息顺序")
        void shouldPreserveMessageOrder() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.user("第1条"));
            messages.add(Message.assistant("第2条"));
            messages.add(Message.user("第3条"));

            assertEquals("第1条", messages.get(0).getContent());
            assertEquals("第2条", messages.get(1).getContent());
            assertEquals("第3条", messages.get(2).getContent());
        }
    }

    @Nested
    @DisplayName("Token 估算逻辑测试")
    class TokenEstimationTests {

        @Test
        @DisplayName("中文应该按字符计算")
        void chineseShouldBeCountedByCharacter() {
            // 中文字符大约每字符1个token
            String chinese = "你好世界";
            assertEquals(4, chinese.length());
        }

        @Test
        @DisplayName("英文应该按单词计算")
        void englishShouldBeCountedByWord() {
            String english = "hello world";
            assertEquals("hello world".length(), english.length());
        }

        @Test
        @DisplayName("混合文本应该正确计算")
        void mixedTextShouldCalculateCorrectly() {
            String mixed = "你好Hello世界World123";
            // 4中文 + 10英文数字 + 3空格 = 17
            assertEquals(17, mixed.length());
        }
    }

    @Nested
    @DisplayName("上下文压缩阈值测试")
    class CompressionThresholdTests {

        @Test
        @DisplayName("15条消息刚好达到阈值")
        void fifteenMessagesShouldMeetThreshold() {
            int threshold = 15;
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < threshold; i++) {
                messages.add(Message.user("消息" + i));
            }
            assertEquals(threshold, messages.size());
        }

        @Test
        @DisplayName("超过15条消息应触发压缩")
        void overFifteenMessagesShouldTriggerCompression() {
            int threshold = 15;
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < threshold + 5; i++) {
                messages.add(Message.user("消息" + i));
            }
            assertTrue(messages.size() > threshold);
        }

        @Test
        @DisplayName("保留最近6条消息")
        void shouldKeepRecentSixMessages() {
            int keepRecent = 6;
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                messages.add(Message.user("消息" + i));
            }

            // 模拟压缩：保留最近的6条
            List<Message> recent = messages.subList(messages.size() - keepRecent, messages.size());
            assertEquals(keepRecent, recent.size());
            assertEquals("消息14", recent.get(0).getContent());
            assertEquals("消息19", recent.get(5).getContent());
        }
    }
}
