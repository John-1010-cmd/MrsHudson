<template>
  <div class="chat-room">
    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <!-- 消息列表内容 -->
      <template v-if="messages.length > 0">
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
            <!-- AI语音播放按钮 -->
            <div v-if="msg.role === 'assistant' && msg.audioUrl" class="audio-player">
              <!-- 主按钮：从头播放 -->
              <el-button
                type="info"
                size="small"
                circle
                :title="msg.isPlaying ? '暂停' : '从头播放'"
                @click="toggleAudio(msg)"
              >
                <el-icon v-if="!msg.isPlaying"><VideoPlay /></el-icon>
                <el-icon v-else><VideoPause /></el-icon>
              </el-button>
              <!-- 继续播放按钮：暂停后显示 -->
              <el-button
                v-if="msg.hasPaused && !msg.isPlaying"
                type="primary"
                size="small"
                class="resume-btn"
                @click="resumeAudio(msg)"
              >
                <el-icon><VideoPlay /></el-icon>
                继续
              </el-button>
              <audio
                :id="'audio-' + msg.id"
                :src="msg.audioUrl"
                crossorigin="anonymous"
                preload="none"
                @ended="onAudioEnded(msg)"
                @error="onAudioError(msg)"
              />
            </div>
            <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else-if="messages.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无消息，开始对话吧" />
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
          :placeholder="conversationId ? '输入消息，按Enter发送...' : '请先创建或选择一个对话'"
          @keyup.enter.exact="sendMessage"
          :disabled="loading || !conversationId"
        />
        <div class="input-actions">
          <VoiceInputButton
            :disabled="loading || !conversationId"
            :loading="loading"
            @record="handleVoiceRecord"
            @error="handleVoiceError"
          />
          <el-button
            type="primary"
            :loading="loading"
            @click="sendMessage"
            :disabled="!inputMessage.trim() || !conversationId"
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
import { ref, onMounted, nextTick, watch, inject, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled, Promotion, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import * as chatApi from '../api/chat'
import VoiceInputButton from '../components/VoiceInputButton.vue'

// 从父组件注入
const injectedId = inject('currentConversationId')
const conversationId = injectedId as Ref<string | null> | undefined

// 确保有值，否则报错
if (!conversationId) {
  console.error('ChatRoom: 无法注入 currentConversationId')
}

interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  toolCalls?: chatApi.ToolCallInfo[]
  audioUrl?: string
  isPlaying?: boolean
  pausedAt?: number  // 暂停位置（秒）
  hasPaused?: boolean  // 是否曾暂停过
}

const messages = ref<Message[]>([])
const inputMessage = ref('')
const loading = ref(false)
const messageListRef = ref<HTMLElement>()

// 监听会话ID变化，加载对应会话的消息
watch(() => conversationId?.value, async (newId, oldId) => {
  console.log('ChatRoom: conversationId 变化:', newId, '旧值:', oldId)
  if (newId) {
    await loadConversationMessages(newId)
  } else {
    messages.value = [{
      id: 'welcome',
      role: 'assistant',
      content: '您好！我是MrsHudson，您的私人管家助手。\n\n我可以帮您：\n🌤️ 查询天气\n📅 管理日程\n✅ 记录待办\n\n请选择左侧会话或创建新对话开始聊天。',
      createdAt: new Date().toISOString()
    }]
  }
}, { immediate: true })

// 加载指定会话的消息
const loadConversationMessages = async (conversationId: string) => {
  if (!conversationId) {
    console.warn('ChatRoom: conversationId 为空，跳过加载')
    return
  }

  console.log('ChatRoom: 开始加载会话消息:', conversationId)
  loading.value = true

  try {
    const res = await chatApi.getChatHistoryByConversation(conversationId, 50)
    console.log('ChatRoom: API 响应:', res.code, res.data?.messages?.length)

    if (res.code === 200) {
      if (res.data.messages.length > 0) {
        messages.value = res.data.messages.map(msg => {
          // 解析工具调用信息
          let toolCalls: chatApi.ToolCallInfo[] | undefined = undefined
          if (msg.functionCall) {
            try {
              const parsed = JSON.parse(msg.functionCall)
              toolCalls = Array.isArray(parsed) ? parsed : [parsed]
            } catch (e) {
              console.warn('解析工具调用信息失败:', msg.functionCall)
            }
          }
          return {
            id: msg.id,
            role: msg.role as 'user' | 'assistant' | 'system',
            content: msg.content,
            createdAt: msg.createdAt,
            toolCalls,
            audioUrl: undefined, // 历史消息不保留音频URL
            isPlaying: false,
            pausedAt: 0,
            hasPaused: false
          }
        })
        console.log('ChatRoom: 加载消息成功，数量:', messages.value.length)
      } else {
        messages.value = [{
          id: 'welcome-' + conversationId,
          role: 'assistant',
          content: '新对话已开始！有什么我可以帮您的吗？',
          createdAt: new Date().toISOString()
        }]
        console.log('ChatRoom: 会话无历史消息，显示欢迎语')
      }
      scrollToBottom()
    } else {
      console.error('ChatRoom: API 返回错误:', res.message)
      ElMessage.error(res.message || '加载消息失败')
    }
  } catch (error) {
    console.error('ChatRoom: 加载会话消息失败:', error)
    ElMessage.error('加载消息失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

// 格式化消息内容（简单的Markdown样式）
const formatMessage = (content: string) => {
  return content
    .replace(/\n/g, '<br>')
    .replace(/🔧\s*(\w+)/g, '<el-tag size="small" type="info">🔧 $1</el-tag>')
}

const formatTime = (time: string) => {
  const date = new Date(time)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')

  return `${year}-${month}-${day} ${hour}:${minute}`
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

const sendMessage = async () => {
  const content = inputMessage.value.trim()
  if (!content || loading.value) return

  // 检查是否有选中的会话
  if (!conversationId.value) {
    ElMessage.warning('请先创建或选择一个对话')
    return
  }

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
    const res = await chatApi.sendMessage({
      message: content,
      conversationId: conversationId.value
    })
    if (res.code === 200) {
      console.log('AI回复:', res.data)
      console.log('audioUrl:', res.data.audioUrl)
      const aiMsg: Message = {
        id: res.data.messageId,
        role: 'assistant',
        content: res.data.content,
        createdAt: res.data.createdAt,
        toolCalls: res.data.functionCalls,
        audioUrl: res.data.audioUrl,
        isPlaying: false,
        pausedAt: 0,
        hasPaused: false
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

// 处理语音录音
const handleVoiceRecord = async (audioBlob: Blob, duration: number) => {
  console.log('收到语音录音，时长:', duration, '秒')

  // 检查是否有选中的会话
  if (!conversationId.value) {
    ElMessage.warning('请先创建或选择一个对话')
    return
  }

  // 添加用户语音消息（显示为语音标识）
  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content: `[语音消息 ${Math.round(duration)}秒]`,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMsg)
  scrollToBottom()

  // 调用API
  loading.value = true
  try {
    const format = audioBlob.type.includes('webm') ? 'webm' : 'wav'
    const res = await chatApi.sendVoiceMessage(audioBlob, format, 16000, conversationId.value)

    if (res.code === 200) {
      // 更新用户消息为识别文本
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg.role === 'user') {
        lastMsg.content = `[语音] ${res.data.recognizedText}`
      }

      // 添加AI回复
      const aiMsg: Message = {
        id: res.data.messageId,
        role: 'assistant',
        content: res.data.content,
        createdAt: res.data.createdAt,
        toolCalls: res.data.functionCalls,
        audioUrl: res.data.audioUrl,
        isPlaying: false,
        pausedAt: 0,
        hasPaused: false
      }
      messages.value.push(aiMsg)

      ElMessage.success(`语音识别: ${res.data.recognizedText}`)
    } else {
      ElMessage.error(res.message || '语音识别失败')
      messages.value.push({
        id: 'error-' + Date.now(),
        role: 'assistant',
        content: '语音识别失败，请重试。',
        createdAt: new Date().toISOString()
      })
    }
  } catch (error) {
    console.error('语音发送失败:', error)
    ElMessage.error('语音处理失败，请重试')
    messages.value.push({
      id: 'error-' + Date.now(),
      role: 'assistant',
      content: '语音处理失败，请稍后重试。',
      createdAt: new Date().toISOString()
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 处理语音错误
const handleVoiceError = (message: string) => {
  ElMessage.error(message)
}

// 播放/暂停音频（主按钮：从头播放）
const toggleAudio = (msg: Message) => {
  const audioElement = document.getElementById('audio-' + msg.id) as HTMLAudioElement
  if (!audioElement) return

  if (msg.isPlaying) {
    // 暂停并记录位置
    msg.pausedAt = audioElement.currentTime
    msg.hasPaused = true
    audioElement.pause()
    msg.isPlaying = false
  } else {
    // 先停止其他正在播放的音频
    messages.value.forEach(m => {
      if (m.role === 'assistant' && m.isPlaying) {
        const otherAudio = document.getElementById('audio-' + m.id) as HTMLAudioElement
        if (otherAudio) {
          otherAudio.pause()
          otherAudio.currentTime = 0
        }
        m.isPlaying = false
        m.pausedAt = 0
      }
    })

    // 从头播放
    audioElement.currentTime = 0
    audioElement.play().then(() => {
      msg.isPlaying = true
    }).catch((error) => {
      console.error('音频播放失败:', error)
      ElMessage.error('音频播放失败')
    })
  }
}

// 继续播放（从暂停位置）
const resumeAudio = (msg: Message) => {
  const audioElement = document.getElementById('audio-' + msg.id) as HTMLAudioElement
  if (!audioElement) return

  // 先停止其他正在播放的音频
  messages.value.forEach(m => {
    if (m.role === 'assistant' && m.isPlaying) {
      const otherAudio = document.getElementById('audio-' + m.id) as HTMLAudioElement
      if (otherAudio) {
        otherAudio.pause()
        otherAudio.currentTime = 0
      }
      m.isPlaying = false
    }
  })

  // 从暂停位置继续播放
  if (msg.pausedAt && msg.pausedAt > 0) {
    audioElement.currentTime = msg.pausedAt
  }
  audioElement.play().then(() => {
    msg.isPlaying = true
  }).catch((error) => {
    console.error('音频播放失败:', error)
    ElMessage.error('音频播放失败')
  })
}

// 音频播放结束
const onAudioEnded = (msg: Message) => {
  msg.isPlaying = false
  msg.pausedAt = 0
  msg.hasPaused = false
}

// 音频播放错误
const onAudioError = (msg: Message, event?: Event) => {
  msg.isPlaying = false
  const audioElement = document.getElementById('audio-' + msg.id) as HTMLAudioElement
  if (audioElement) {
    console.error('音频错误详情:', {
      url: audioElement.src,
      error: audioElement.error,
      networkState: audioElement.networkState,
      readyState: audioElement.readyState
    })
    if (audioElement.error) {
      const errorMap: Record<number, string> = {
        1: '下载中止 (MEDIA_ERR_ABORTED)',
        2: '网络错误 (MEDIA_ERR_NETWORK)',
        3: '解码错误 (MEDIA_ERR_DECODE)',
        4: '格式不支持 (MEDIA_ERR_SRC_NOT_SUPPORTED)'
      }
      ElMessage.error(`音频播放失败: ${errorMap[audioElement.error.code] || '未知错误'}`)
      return
    }
  }
  ElMessage.error('音频加载失败')
}

onMounted(() => {
  console.log('ChatRoom: onMounted, conversationId:', conversationId?.value)
  // 确保当前会话消息已加载
  if (conversationId?.value) {
    loadConversationMessages(conversationId.value)
  } else {
    console.warn('ChatRoom: 没有可用的 conversationId')
  }
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

.audio-player {
  display: flex;
  align-items: center;
  margin-top: 8px;
  gap: 8px;
}

.audio-player .resume-btn {
  animation: fadeIn 0.3s ease;
  padding: 4px 8px;
  font-size: 12px;
}

.audio-player .resume-btn .el-icon {
  margin-right: 2px;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
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
  align-items: center;
  gap: 12px;
}

/* 空状态 */
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  padding: 40px;
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
