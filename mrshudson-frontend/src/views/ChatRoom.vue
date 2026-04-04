<template>
  <div class="chat-room">
    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <!-- 消息列表内容 -->
      <template v-if="messages.length > 0">
        <!-- 过滤掉正在加载中的空 AI 消息（避免双气泡） -->
        <div v-for="msg in messages.filter(m => !(m.role === 'assistant' && m.id.startsWith('stream-') && m.content.length === 0))" :key="msg.id"
          :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']">
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'assistant'" :size="40" :style="{ background: '#409eff' }">
              🤖
            </el-avatar>
            <el-avatar v-else :size="40" :icon="UserFilled" />
          </div>
          <div class="message-content">
            <!-- 思考过程折叠块（仅 assistant 且有 thinkingContent 时显示） -->
            <div v-if="msg.role === 'assistant' && msg.thinkingContent" class="thinking-block">
              <div class="thinking-header" @click="msg.isThinkingExpanded = !msg.isThinkingExpanded">
                <span class="thinking-icon">🧠</span>
                <span class="thinking-label">思考过程</span>
                <span class="thinking-toggle">{{ msg.isThinkingExpanded ? '▲ 收起' : '▼ 展开' }}</span>
              </div>
              <div v-show="msg.isThinkingExpanded" class="thinking-content">
                {{ msg.thinkingContent }}
              </div>
            </div>
            <div class="message-bubble" v-html="formatMessage(msg.content)"></div>
            <!-- SSE 错误/超时重试按钮（规范 12.1） -->
            <div v-if="msg.role === 'assistant' && msg.hasError" class="error-retry">
              <el-button type="primary" size="small" @click="retryLastMessage">重新发送</el-button>
            </div>
            <!-- 工具调用显示 -->
            <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="tool-calls">
              <div v-for="(tool, index) in msg.toolCalls" :key="index" class="tool-call">
                <el-tag size="small" type="info">🔧 {{ tool.name }}</el-tag>
              </div>
            </div>
            <!-- TTS 合成中状态提示 -->
            <div v-if="msg.role === 'assistant' && msg.ttsStatus === 'synthesizing'" class="tts-status">
              <span class="tts-loading">🔊 语音合成中...</span>
            </div>
            <!-- AI语音播放按钮：仅 ttsStatus === 'ready' 且有 audioUrl 时显示 -->
            <div v-if="msg.role === 'assistant' && msg.ttsStatus === 'ready' && msg.audioUrl" class="audio-player">
              <!-- 主按钮：从头播放 -->
              <el-button type="info" size="small" circle :title="msg.isPlaying ? '暂停' : '从头播放'"
                @click="toggleAudio(msg)">
                <el-icon v-if="!msg.isPlaying">
                  <VideoPlay />
                </el-icon>
                <el-icon v-else>
                  <VideoPause />
                </el-icon>
              </el-button>
              <!-- 继续播放按钮 + 从头播放按钮：暂停后显示 -->
              <template v-if="msg.hasPaused && !msg.isPlaying">
                <el-button type="primary" size="small" class="resume-btn" @click="resumeAudio(msg)">
                  <el-icon><VideoPlay /></el-icon>
                  继续
                </el-button>
                <el-button size="small" @click="replayAudio(msg)">
                  从头播放
                </el-button>
              </template>
              <audio :id="'audio-' + msg.id" :src="msg.audioUrl || undefined" crossorigin="anonymous" preload="none"
                @ended="onAudioEnded(msg)" @error="onAudioError(msg)" />
            </div>
            <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else-if="messages.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无消息，开始对话吧" />
      </div>

      <!-- 加载状态 - 当正在加载且AI消息还没有内容时显示打字动画（避免双气泡） -->
      <div v-if="loading && !hasAiMessageContent" class="message ai-message">
        <div class="message-avatar">
          <el-avatar :size="40" :style="{ background: '#409eff' }">🤖</el-avatar>
        </div>
        <div class="message-content">
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <!-- 响应时间阈值分阶段提示（规范 12.2） -->
          <div v-if="waitMessage" class="wait-message">{{ waitMessage }}</div>
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-area">
      <div class="input-box">
        <el-input v-model="inputMessage" type="textarea" :rows="2"
          :placeholder="conversationId ? '请输入消息，按 Enter 发送...' : '请先选择一个会话'" @keyup.enter.exact="sendMessage"
          :disabled="inputDisabled || !conversationId" />
        <div class="input-actions">
          <VoiceInputButton :disabled="inputDisabled || !conversationId" :loading="loading" @record="handleVoiceRecord"
            @error="handleVoiceError" />
          <el-button type="primary" :loading="loading" @click="sendMessage"
            :disabled="!inputMessage.trim() || !conversationId || inputDisabled">
            <el-icon>
              <Promotion />
            </el-icon>
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Promotion, UserFilled, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, inject, nextTick, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'
import * as chatApi from '../api/chat'
import VoiceInputButton from '../components/VoiceInputButton.vue'
import { SseClient } from '../utils/sse/SseClient'

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
  thinkingContent?: string       // 思考推理过程（可选，模型不支持时为 undefined）
  isThinkingExpanded: boolean    // 思考过程是否展开（流式接收时默认展开，历史消息默认折叠）
  createdAt: string
  toolCalls?: chatApi.ToolCallInfo[]
  audioUrl?: string | null      // 历史消息或 TTS 成功时的音频 URL
  ttsStatus: TtsStatus          // TTS 状态
  isPlaying: boolean
  pausedAt: number              // 暂停位置（秒），0 表示未暂停
  hasPaused: boolean            // 是否曾暂停过
  hasError?: boolean            // 消息是否出错（SSE 错误/超时），用于显示重试按钮
}

type TtsStatus =
  | 'pending'      // AI 还在生成内容
  | 'synthesizing' // content_done 已收到，等待 audio_done
  | 'ready'        // 有 URL，可播放
  | 'timeout'      // 超时，后台继续合成
  | 'error'        // 合成异常
  | 'noaudio'      // 提供商返回空
  | 'no_audio'     // 历史消息无音频

const messages = ref<Message[]>([])
const inputMessage = ref('')
const loading = ref(false)
const inputDisabled = ref(false)  // 发送期间禁用输入框
const messageListRef = ref<HTMLElement>()

// 响应时间阈值分阶段提示（规范 12.2）
const waitMessage = ref<string | null>(null)
const lastSentMessage = ref<string>('')
let wait10sTimer: number | null = null
let wait30sTimer: number | null = null

function startWaitTimers() {
  clearWaitTimers()
  waitMessage.value = null
  // 10s 后提示 "AI 正在处理中..."
  wait10sTimer = window.setTimeout(() => {
    if (loading.value && !hasAiMessageContent.value) {
      waitMessage.value = 'AI 正在处理中...'
    }
  }, 10000)
  // 30s 后提示 "响应较慢，请稍候..."
  wait30sTimer = window.setTimeout(() => {
    if (loading.value && !hasAiMessageContent.value) {
      waitMessage.value = '响应较慢，请稍候...'
    }
  }, 30000)
}

function clearWaitTimers() {
  if (wait10sTimer) { clearTimeout(wait10sTimer); wait10sTimer = null }
  if (wait30sTimer) { clearTimeout(wait30sTimer); wait30sTimer = null }
  waitMessage.value = null
}

// 重试：重新发送最后一条用户消息
function retryLastMessage() {
  const msg = lastSentMessage.value
  if (!msg) return
  // 移除出错的 AI 消息
  messages.value = messages.value.filter(m => !m.hasError)
  sendStreamContent(msg)
}

// 计算是否有AI消息占位符（用于避免双气泡）
const hasAiMessagePlaceholder = computed(() => {
  // 如果有流式消息占位符（id以stream-开头）且正在加载中，说明AI消息正在增量更新
  return messages.value.some(msg =>
    msg.role === 'assistant' && msg.id.startsWith('stream-')
  )
})

// 计算是否有AI消息内容（用于控制打字动画显示）
const hasAiMessageContent = computed(() => {
  return messages.value.some(msg =>
    msg.role === 'assistant' && msg.id.startsWith('stream-') && msg.content.length > 0
  )
})

// 监听会话ID变化，加载对应会话的消息
watch(() => conversationId?.value, async (newId, oldId) => {
  // 切换会话时中断旧 SSE 连接
  if (oldId !== newId && currentSseClient) {
    currentSseClient.abort()
    currentSseClient = null
    loading.value = false
    inputDisabled.value = false
  }
  console.log('ChatRoom: 会话历史消息初始化中，欢迎')
  if (newId) {
    await loadConversationMessages(newId)
  } else {
    messages.value = [{
      id: 'welcome',
      role: 'assistant',
      content: `你好，我是 MrsHudson，你的私人AI管家。\n\n我可以帮你：\n- 查询天气\n- 管理日程\n- 记录待办\n\n请选择一个会话或创建新对话开始使用。`,
      createdAt: new Date().toISOString(),
      thinkingContent: undefined,
      isThinkingExpanded: false,
      ttsStatus: 'no_audio',
      isPlaying: false,
      pausedAt: 0,
      hasPaused: false
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
            thinkingContent: undefined,  // 历史消息无思考过程
            isThinkingExpanded: false,    // 历史消息默认折叠
            createdAt: msg.createdAt,
            toolCalls,
            audioUrl: msg.audioUrl || null,
            ttsStatus: (msg.audioUrl ? 'ready' : 'no_audio') as TtsStatus,
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
          createdAt: new Date().toISOString(),
          thinkingContent: undefined,
          isThinkingExpanded: false,
          ttsStatus: 'no_audio',
          isPlaying: false,
          pausedAt: 0,
          hasPaused: false
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
  if (!content || inputDisabled.value) return

  if (!conversationId.value) {
    ElMessage.warning('请先选择一个会话')
    return
  }

  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content: content,
    createdAt: new Date().toISOString(),
    thinkingContent: undefined,
    isThinkingExpanded: false,
    ttsStatus: 'no_audio',
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false
  }
  messages.value.push(userMsg)
  inputMessage.value = ''
  scrollToBottom()

  await sendStreamContent(content)
}

// 流式发送并接收消息
let currentAiMsg: Message | null = null
let currentSseClient: SseClient | null = null

async function sendStreamContent(content: string) {
  loading.value = true
  inputDisabled.value = true  // 发送开始，禁用输入框
  lastSentMessage.value = content  // 记录最后发送的消息，用于重试
  startWaitTimers()  // 启动等待阈值计时

  const aiMsg: Message = {
    id: 'stream-' + Date.now(),
    role: 'assistant',
    content: '',
    thinkingContent: undefined,  // 流式接收时初始为 undefined，收到 thinking 事件时设置
    isThinkingExpanded: true,   // 流式接收时默认展开
    createdAt: new Date().toISOString(),
    toolCalls: [],
    ttsStatus: 'pending',
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false
  }
  currentAiMsg = aiMsg
  messages.value.push(aiMsg)
  scrollToBottom()

  let fullContent = ''
  let toolCalls: chatApi.ToolCallInfo[] = []
  let resolvedMessageId: string | null = null  // 后端返回的真实 messageId

  try {
    const token = localStorage.getItem('access_token')

    currentSseClient = new SseClient({
      url: '/api/chat/stream',
      method: 'POST',
      headers: {
        'Authorization': token ? `Bearer ${token}` : '',
      },
      body: {
        message: content,
        conversationId: conversationId.value || null
      },
      timeout: 60000,

      onConnecting: () => {
        console.log('[SSE] 连接中...')
      },

      onOpen: () => {
        console.log('[SSE] 连接已打开')
      },

      onFirstContent: () => {
        clearWaitTimers()  // 首字节到达，清除等待提示
        console.log('[SSE] 收到首个内容')
      },

      onThinking: (text: string, conversationId: number | null, messageId: number | null) => {
        clearWaitTimers()  // 首字节到达，清除等待提示
        console.log('[SSE] thinking:', text.substring(0, 50))
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target) {
            if (target.thinkingContent === undefined) {
              target.thinkingContent = ''
            }
            target.thinkingContent += text
            scrollToBottom()
            return
          }
        }
        // 降级：用占位消息
        if (aiMsg.thinkingContent === undefined) {
          aiMsg.thinkingContent = ''
        }
        aiMsg.thinkingContent += text
        scrollToBottom()
      },

      onContent: (text: string, conversationId: number | null, messageId: number | null) => {
        // 首次收到 messageId 时，将占位消息 id 更新为后端真实 messageId
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target) {
            target.content += text
            scrollToBottom()
            return
          }
        }
        // 降级：用占位消息
        fullContent += text
        aiMsg.content = fullContent
        scrollToBottom()
      },

      onContentDone: (conversationId: number | null, messageId: number | null) => {
        console.log('[SSE] content_done 收到')
        const target = messageId !== null ? messages.value.find(m => m.id === String(messageId)) : null
        const msg = target || aiMsg
        msg.ttsStatus = 'synthesizing'
      },

      onAudioDone: (event) => {
        console.log('[SSE] audio_done:', event)
        const target = event.messageId !== null ? messages.value.find(m => m.id === String(event.messageId)) : null
        const msg = target || aiMsg
        if (event.timeout) {
          msg.ttsStatus = 'timeout'
          msg.audioUrl = null
        } else if (event.error) {
          msg.ttsStatus = 'error'
          msg.audioUrl = null
        } else if (event.noaudio) {
          msg.ttsStatus = 'noaudio'
          msg.audioUrl = null
        } else if (event.url) {
          msg.ttsStatus = 'ready'
          msg.audioUrl = event.url
        }
      },

      onCacheHit: (text: string, _conversationId: number | null, messageId: number | null) => {
        // 首次收到 messageId 时，将占位消息 id 更新为后端真实 messageId
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target) {
            target.content += text
            scrollToBottom()
            return
          }
        }
        // 降级：用占位消息
        fullContent += text
        aiMsg.content = fullContent
        scrollToBottom()
      },

      onClarification: (text: string, _conversationId: number | null, messageId: number | null) => {
        // 首次收到 messageId 时，将占位消息 id 更新为后端真实 messageId
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target) {
            target.content += text
            scrollToBottom()
            return
          }
        }
        // 降级：用占位消息
        fullContent += text
        aiMsg.content = fullContent
        scrollToBottom()
      },

      onToolCall: (tool, _conversationId: number | null, messageId: number | null) => {
        // 首次收到 messageId 时，将占位消息 id 更新为后端真实 messageId
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        toolCalls.push({ name: tool.name, arguments: tool.arguments, result: '' })
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target) {
            target.toolCalls = [...(target.toolCalls || []), { name: tool.name, arguments: tool.arguments, result: '' }]
            return
          }
        }
        // 降级：用占位消息
        aiMsg.toolCalls = [...toolCalls]
      },

      onToolResult: (result, _conversationId: number | null, messageId: number | null) => {
        // 首次收到 messageId 时，将占位消息 id 更新为后端真实 messageId
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target && target.toolCalls) {
            const toolCall = target.toolCalls.find(t => t.name === result.name)
            if (toolCall) toolCall.result = result.result
            target.toolCalls = [...target.toolCalls]
            return
          }
        }
        // 降级：用占位消息
        const toolCall = toolCalls.find(t => t.name === result.name)
        if (toolCall) toolCall.result = result.result
        aiMsg.toolCalls = [...toolCalls]
      },

      onTokenUsage: (usage, _conversationId: number | null, messageId: number | null) => {
        // 首次收到 messageId 时，将占位消息 id 更新为后端真实 messageId
        if (messageId !== null && resolvedMessageId === null) {
          resolvedMessageId = String(messageId)
          aiMsg.id = resolvedMessageId
        }
        const stats = `\n\n--- 💡 本次对话消耗 ---\n` +
          `📥 输入: ${usage.inputTokens} tokens\n` +
          `📤 输出: ${usage.outputTokens} tokens\n` +
          `⏱️ 耗时: ${usage.duration}ms\n` +
          `🤖 模型: ${usage.model}\n` +
          `------------------------`
        // 优先用 messageId 精确定位消息气泡
        if (messageId !== null) {
          const target = messages.value.find(m => m.id === String(messageId))
          if (target) {
            target.content += stats
            return
          }
        }
        // 降级：用占位消息
        aiMsg.content = fullContent + stats
      },

      onError: (error: string) => {
        console.error('[SSE] error:', error)
        clearWaitTimers()
        ElMessage.error(error)
        aiMsg.content = '抱歉，服务器返回异常，请稍后重试。'
        aiMsg.hasError = true
      },

      onDone: () => {
        console.log('[SSE] done')
        clearWaitTimers()
        inputDisabled.value = false  // SSE 完成，恢复输入框
        loading.value = false
        currentSseClient = null
      },

      onClose: () => {
        console.log('[SSE] 连接已关闭')
        clearWaitTimers()
        // 确保 inputDisabled 被恢复（兜底）
        inputDisabled.value = false
        loading.value = false
        currentSseClient = null
      }
    })

    await currentSseClient.stream()

    // 流结束后重新加载消息列表（获取最新数据库状态）
    if (conversationId.value) {
      await loadConversationMessages(conversationId.value)
      // 恢复当前消息的 ttsStatus 和 audioUrl（loadConversationMessages 会覆盖）
      const assistantMsgs = messages.value.filter(msg => msg.role === 'assistant')
      if (assistantMsgs.length > 0) {
        const lastMsg = assistantMsgs[assistantMsgs.length - 1]
        if (aiMsg.ttsStatus === 'ready' && aiMsg.audioUrl) {
          lastMsg.ttsStatus = 'ready'
          lastMsg.audioUrl = aiMsg.audioUrl
        }
      }
    }
  } catch (error: any) {
    console.error('流式接收失败:', error)
    clearWaitTimers()
    ElMessage.error(error.message || '网络异常，请重试')
    aiMsg.content = '抱歉，服务器返回异常，请稍后重试。'
    aiMsg.hasError = true
    currentSseClient = null
  } finally {
    loading.value = false
    inputDisabled.value = false  // 出错也要恢复输入框
    clearWaitTimers()
    currentAiMsg = null
    currentSseClient = null
    scrollToBottom()
    refreshConversations?.()
    if (refreshConversations) {
      window.setTimeout(() => refreshConversations(), 800)
      window.setTimeout(() => refreshConversations(), 2000)
    }
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
    createdAt: new Date().toISOString(),
    thinkingContent: undefined,
    isThinkingExpanded: false,
    ttsStatus: 'no_audio',
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false,
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
        thinkingContent: undefined,
        isThinkingExpanded: false,
        toolCalls: res.data.functionCalls,
        audioUrl: res.data.audioUrl,
        ttsStatus: (res.data.audioUrl ? 'ready' : 'no_audio') as TtsStatus,
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
        createdAt: new Date().toISOString(),
        thinkingContent: undefined,
        isThinkingExpanded: false,
        ttsStatus: 'no_audio',
        isPlaying: false,
        pausedAt: 0,
        hasPaused: false,
      })
    }
  } catch (error) {
    console.error('语音发送失败:', error)
    ElMessage.error('语音处理失败，请重试')
    messages.value.push({
      id: 'error-' + Date.now(),
      role: 'assistant',
      content: '对话已开始，有什么我可以帮你？',
      createdAt: new Date().toISOString(),
      thinkingContent: undefined,
      isThinkingExpanded: false,
      ttsStatus: 'no_audio',
      isPlaying: false,
      pausedAt: 0,
      hasPaused: false,
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

// 从头重新播放
const replayAudio = (msg: Message) => {
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

  msg.hasPaused = false
  msg.pausedAt = 0
  audioElement.currentTime = 0
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

// 组件卸载时中断 SSE 连接
onUnmounted(() => {
  if (currentSseClient) {
    currentSseClient.abort()
    currentSseClient = null
  }
  clearWaitTimers()
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

/* 思考过程折叠块 */
.thinking-block {
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
}

.thinking-header:hover {
  background: #ecf5ff;
}

.thinking-toggle {
  margin-left: auto;
  font-size: 12px;
}

.thinking-content {
  padding: 10px 12px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
  border-top: 1px solid #e4e7ed;
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

  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }

  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

/* 响应时间阈值等待提示 */
.wait-message {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

/* 错误重试按钮 */
.error-retry {
  margin-top: 8px;
}
</style>
