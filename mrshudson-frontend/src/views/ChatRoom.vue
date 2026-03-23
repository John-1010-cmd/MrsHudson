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
            <div v-if="msg.role === 'assistant' && (msg.audioUrl || msg.streamAudioUrl)" class="audio-player">
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
                :src="msg.streamAudioUrl || msg.audioUrl"
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

      <!-- 加载状态 - 仅当没有AI消息占位符时显示（避免双气泡） -->
      <div v-if="loading && !hasAiMessagePlaceholder" class="message ai-message">
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
          :placeholder="conversationId ? '请输入消息，按 Enter 发送...' : '请先选择一个会话'"
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
import { ref, computed, onMounted, nextTick, watch, inject, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled, Promotion, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import * as chatApi from '../api/chat'
import VoiceInputButton from '../components/VoiceInputButton.vue'

// 从父组件注入
const injectedId = inject('currentConversationId')
const conversationId = injectedId as Ref<string | null> | undefined
const refreshConversations = inject('refreshConversations') as (() => Promise<void>) | undefined
const injectedConversations = inject<Ref<chatApi.ConversationDTO[]>>('conversations')
const conversations = injectedConversations

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
  audioUrl?: string          // 历史消息中的音频URL（从后端数据库加载）
  streamAudioUrl?: string     // 流式响应中生成的音频URL（仅在当前会话有效，不会被 loadConversationMessages 覆盖）
  isPlaying?: boolean
  pausedAt?: number  // 暂停位置（秒）
  hasPaused?: boolean  // 是否曾暂停过
}

const messages = ref<Message[]>([])
const inputMessage = ref('')
const loading = ref(false)
const messageListRef = ref<HTMLElement>()

// 计算是否有AI消息占位符（用于避免双气泡）
const hasAiMessagePlaceholder = computed(() => {
  // 如果有流式消息占位符（id以stream-开头）且正在加载中，说明AI消息正在增量更新
  return messages.value.some(msg =>
    msg.role === 'assistant' && msg.id.startsWith('stream-')
  )
})

// 监听会话ID变化，加载对应会话的消息
watch(() => conversationId?.value, async (newId, oldId) => {
  console.log('ChatRoom: 会话历史消息初始化中，欢迎')
  if (newId) {
    await loadConversationMessages(newId)
  } else {
    messages.value = [{
      id: 'welcome',
      role: 'assistant',
      content: `你好，我是 MrsHudson，你的私人AI管家。\n\n我可以帮你：\n- 查询天气\n- 管理日程\n- 记录待办\n\n请选择一个会话或创建新对话开始使用。`,
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

  console.log('ChatRoom: 会话历史消息初始化中，欢迎')
  loading.value = true

  try {
    const res = await chatApi.getChatHistoryByConversation(conversationId, 50)
    console.log('ChatRoom: 会话历史消息初始化中，欢迎')

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
        console.log('ChatRoom: 加载消息成功，数量', messages.value.length)
      } else {
        messages.value = [{
          id: 'welcome-' + conversationId,
          role: 'assistant',
          content: '对话已开始，有什么我可以帮你？',
          createdAt: new Date().toISOString()
        }]
        console.log('ChatRoom: 会话历史消息加载完成，欢迎')
      }
      scrollToBottom()
    } else {
      console.error('ChatRoom: API 返回错误:', res.message)
      ElMessage.error(res.message || '加载消息失败')
    }
  } catch (error) {
    console.error('ChatRoom: 加载会话消息失败:', error)
    ElMessage.error('加载消息失败，请重试')
  } finally {
    loading.value = false
  }
}

// HTML转义函数（防止XSS和显示问题）
const escapeHtml = (text: string): string => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 格式化消息内容（简单的Markdown样式）
const formatMessage = (content: string) => {
  if (!content) return ''
  // 先转义HTML特殊字符，再用<br>替换换行
  const escaped = escapeHtml(content)
  return escaped
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
    ElMessage.warning('请先选择一个会话')
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

  // 使用流式响应
  await sendStreamContent(content)
}

// 流式发送并接收消息
let currentAiMsg: Message | null = null

async function sendStreamContent(content: string) {
  loading.value = true

  // 创建 AI 消息占位（用于增量更新）
  const aiMsg: Message = {
    id: 'stream-' + Date.now(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    toolCalls: [],
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false
  }
  currentAiMsg = aiMsg
  messages.value.push(aiMsg)
  scrollToBottom()

  let fullContent = ''
  let toolCalls: chatApi.ToolCallInfo[] = []

  try {
    const token = localStorage.getItem('access_token')

    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Authorization': token ? `Bearer ${token}` : '',
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache'
      },
      body: JSON.stringify({
        message: content,
        conversationId: conversationId.value || null
      })
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('No response body')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    const extractSseData = (line: string): string | null => {
      const trimmed = line.trim()
      if (!trimmed) return null
      // SSE 格式：data: {...} 或 data:{...}
      if (trimmed.startsWith('data:')) {
        const dataContent = trimmed.slice(5).trim()
        // 验证是 JSON 对象
        if (dataContent.startsWith('{') && dataContent.endsWith('}')) {
          return dataContent
        }
      }
      // 直接是 JSON 对象（无 data: 前缀）
      if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
        return trimmed
      }
      return null
    }
    let doneReceived = false

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const dataStr = extractSseData(line)
        if (!dataStr) continue
        if (dataStr === '[DONE]') {
          doneReceived = true
          break
        }

        try {
          const data = JSON.parse(dataStr)

          if (data.type === 'error') {
            throw new Error(data.error || 'Unknown error')
          }

          if (data.type === 'content' && (data.text || data.content)) {
            fullContent += data.text || data.content
            aiMsg.content = fullContent
            scrollToBottom()
          }

          if (data.type === 'cache_hit' && data.content) {
            // 缓存命中，直接显示缓存内容
            fullContent += data.content
            aiMsg.content = fullContent
            scrollToBottom()
          }

          if (data.type === 'clarification' && data.content) {
            // 澄清提示
            fullContent += data.content
            aiMsg.content = fullContent
            scrollToBottom()
          }

          if (data.type === 'tool_call' && data.toolCall) {
            toolCalls.push({
              name: data.toolCall.name,
              arguments: data.toolCall.arguments,
              result: ''
            })
            aiMsg.toolCalls = [...toolCalls]
          }

          if (data.type === 'tool_result' && data.toolResult) {
            const toolCall = toolCalls.find(t => t.name === data.toolResult.name)
            if (toolCall) {
              toolCall.result = data.toolResult.result
            }
            aiMsg.toolCalls = [...toolCalls]
          }

          if (data.type === 'token_usage') {
            const stats = `\n\n--- 💡 本次对话消耗 ---\n` +
              `📥 输入: ${data.inputTokens} tokens\n` +
              `📤 输出: ${data.outputTokens} tokens\n` +
              `⏱️ 耗时: ${data.duration}ms\n` +
              `🤖 模型: ${data.model}\n` +
              `------------------------`
            aiMsg.content = fullContent + stats
          }

          if (data.type === 'audio_url' && data.url) {
            // 语音合成完成，设置音频URL（使用 streamAudioUrl 避免被 loadConversationMessages 覆盖）
            aiMsg.streamAudioUrl = data.url
            console.log('语音合成完成:', data.url)
          }

          if (data.type === 'done') {
            doneReceived = true
          }
        } catch (e) {
          console.error('Parse SSE data error:', e, 'Raw data:', dataStr)
        }
      }

      if (doneReceived) {
        break
      }
    }

    // 流结束后，重新加载消息列表以获取最新数据（包括意图路由直接处理的响应）
    // 注意：需要保留当前流式消息的 streamAudioUrl，因为 loadConversationMessages 会替换整个 messages 数组
    if (conversationId.value) {
      // 记录流式消息在数组中的索引位置（用于恢复 streamAudioUrl）
      // 因为流式消息 ID（'stream-xxx'）与后端返回的真实 ID（数字）不同，无法通过 ID 匹配恢复
      const streamMsgIndex = messages.value.length - 1
      const currentStreamAudioUrl = aiMsg.streamAudioUrl

      // 重新加载消息
      await loadConversationMessages(conversationId.value)

      // 用索引直接恢复流式响应中的 audioUrl
      if (messages.value[streamMsgIndex] && currentStreamAudioUrl) {
        messages.value[streamMsgIndex].streamAudioUrl = currentStreamAudioUrl
      }
    }
  } catch (error: any) {
    console.error('流式接收失败:', error)
    ElMessage.error(error.message || '网络异常，请重试')
    aiMsg.content = '抱歉，服务器返回异常，请稍后重试。'
  } finally {
    loading.value = false
    currentAiMsg = null
    scrollToBottom()
    // 刷新会话列表（更新标题等元数据）
    refreshConversations?.()
    // 考虑异步操作的可能延迟，多次刷新确保最新可视图
    if (refreshConversations) {
      window.setTimeout(() => refreshConversations(), 800)
      window.setTimeout(() => refreshConversations(), 2000)
    }
    // 启动标题轮询：每秒检查一次当前会话标题是否已更新
    // 解决后端 @Async 异步生成标题导致的延迟问题
    startTitlePolling()
  }
}

/**
 * 轮询检查当前会话标题是否已更新
 * 后端使用 @Async 异步生成标题，需要轮询直到标题变化或超时
 */
const startTitlePolling = () => {
  const currentConvId = conversationId?.value
  if (!currentConvId || !conversations?.value) return

  // 记录初始标题（新对话）
  const initialConv = conversations.value.find(c => c.id === currentConvId)
  const initialTitle = initialConv?.title || '新对话'

  // 轮询状态标记
  let pollCount = 0
  const maxPolls = 10 // 最多轮询10次，每次间隔1秒，共10秒

  const pollInterval = window.setInterval(async () => {
    pollCount++

    // 重新获取会话列表
    await refreshConversations?.()

    // 检查当前会话标题是否已更新
    const updatedConv = conversations?.value?.find(c => c.id === currentConvId)
    if (updatedConv && updatedConv.title !== initialTitle && updatedConv.title !== '新对话') {
      // 标题已更新，停止轮询
      console.log('ChatRoom: 标题已更新:', updatedConv.title)
      window.clearInterval(pollInterval)
      return
    }

    // 超过最大轮询次数，停止轮询
    if (pollCount >= maxPolls) {
      console.log('ChatRoom: 标题轮询超时（10秒），停止检查')
      window.clearInterval(pollInterval)
    }
  }, 1000) // 每秒检查一次
}

// 处理语音录音
const handleVoiceRecord = async (audioBlob: Blob, duration: number) => {
  console.log('收到语音录制，时长', duration, '秒')

  // 检查是否有选中的会话
  if (!conversationId.value) {
    ElMessage.warning('请先选择一个会话')
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
        content: '对话已开始，有什么我可以帮你？',
        createdAt: new Date().toISOString()
      })
    }
  } catch (error) {
    console.error('语音发送失败:', error)
    ElMessage.error('语音处理失败，请重试')
    messages.value.push({
      id: 'error-' + Date.now(),
      role: 'assistant',
      content: '对话已开始，有什么我可以帮你？',
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

// 继续播放（从暂停位置继续）
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
  console.log('ChatRoom: 会话历史消息初始化中，欢迎')
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
