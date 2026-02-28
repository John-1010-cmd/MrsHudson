<template>
  <div class="chat-room">
    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']"
      >
        <div class="message-avatar">
          <el-avatar v-if="msg.role === 'assistant'" :size="40" :style="{ background: '#409eff' }">
            🤖
          </el-avatar>
          <el-avatar v-else :size="40" :icon="UserFilled" />
        </div>
        <div class="message-content">
          <div class="message-bubble" v-html="formatMessage(msg.content)"></div>
          <!-- 工具调用显示 -->
          <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="tool-calls">
            <div v-for="(tool, index) in msg.toolCalls" :key="index" class="tool-call">
              <el-tag size="small" type="info">🔧 {{ tool.name }}</el-tag>
            </div>
          </div>
          <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="message ai-message">
        <div class="message-avatar">
          <el-avatar :size="40" :style="{ background: '#409eff' }">🤖</el-avatar>
        </div>
        <div class="message-content">
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-area">
      <div class="input-box">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="2"
          placeholder="输入消息，按Enter发送..."
          @keyup.enter.exact="sendMessage"
          :disabled="loading"
        />
        <div class="input-actions">
          <el-button
            type="primary"
            :loading="loading"
            @click="sendMessage"
            :disabled="!inputMessage.trim()"
          >
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled, Promotion } from '@element-plus/icons-vue'
import * as chatApi from '../api/chat'

interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  toolCalls?: chatApi.ToolCallInfo[]
}

const messages = ref<Message[]>([
  {
    id: 'welcome',
    role: 'assistant',
    content: '您好！我是MrsHudson，您的私人管家助手。\n\n我可以帮您：\n🌤️ 查询天气\n📅 管理日程\n✅ 记录待办\n\n请问有什么可以帮您的吗？',
    createdAt: new Date().toISOString()
  }
])
const inputMessage = ref('')
const loading = ref(false)
const messageListRef = ref<HTMLElement>()

// 格式化消息内容（简单的Markdown样式）
const formatMessage = (content: string) => {
  return content
    .replace(/\n/g, '<br>')
    .replace(/🔧\s*(\w+)/g, '<el-tag size="small" type="info">🔧 $1</el-tag>')
}

const formatTime = (time: string) => {
  return new Date(time).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// 加载历史消息
const loadHistory = async () => {
  try {
    const res = await chatApi.getChatHistory(20)
    if (res.code === 200 && res.data.messages.length > 0) {
      // 将历史消息合并到当前消息列表
      const historyMessages = res.data.messages.map(msg => ({
        id: msg.id,
        role: msg.role as 'user' | 'assistant' | 'system',
        content: msg.content,
        createdAt: msg.createdAt
      }))
      // 保留欢迎消息，追加历史消息
      messages.value = [messages.value[0], ...historyMessages]
      scrollToBottom()
    }
  } catch (error) {
    console.error('加载历史消息失败:', error)
  }
}

const sendMessage = async () => {
  const content = inputMessage.value.trim()
  if (!content || loading.value) return

  // 添加用户消息
  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content: content,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMsg)
  inputMessage.value = ''
  scrollToBottom()

  // 调用API
  loading.value = true
  try {
    const res = await chatApi.sendMessage({ message: content })
    if (res.code === 200) {
      const aiMsg: Message = {
        id: res.data.messageId,
        role: 'assistant',
        content: res.data.content,
        createdAt: res.data.createdAt,
        toolCalls: res.data.functionCalls
      }
      messages.value.push(aiMsg)
    } else {
      ElMessage.error(res.message || '发送失败')
      // 添加错误提示
      messages.value.push({
        id: 'error-' + Date.now(),
        role: 'assistant',
        content: '抱歉，服务暂时不可用，请稍后重试。',
        createdAt: new Date().toISOString()
      })
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('网络错误，请检查连接')
    messages.value.push({
      id: 'error-' + Date.now(),
      role: 'assistant',
      content: '抱歉，网络连接异常，请稍后重试。',
      createdAt: new Date().toISOString()
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  loadHistory()
  scrollToBottom()
})
</script>

<style scoped>
.chat-room {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

.message {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.user-message {
  flex-direction: row-reverse;
}

.user-message .message-content {
  align-items: flex-end;
}

.message-content {
  display: flex;
  flex-direction: column;
  max-width: 70%;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  word-break: break-word;
  line-height: 1.6;
}

.ai-message .message-bubble {
  background: white;
  color: #333;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.user-message .message-bubble {
  background: #409eff;
  color: white;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.tool-calls {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.tool-call {
  display: flex;
  align-items: center;
}

.input-area {
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e4e7ed;
}

.input-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

/* 打字动画 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}
</style>
