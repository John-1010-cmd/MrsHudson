import axios from './axios'

export interface SendMessageRequest {
  message: string
  sessionId?: string
  conversationId?: string | null
}

export interface ToolCallInfo {
  name: string
  arguments: string
  result: string
}

export interface SendMessageResponse {
  messageId: string
  content: string
  audioUrl?: string
  functionCalls?: ToolCallInfo[]
  createdAt: string
  audioUrl?: string
}

export interface MessageInfo {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  functionCall?: string
  audioUrl?: string  // 语音合成音频URL
}

export interface ChatHistoryResponse {
  messages: MessageInfo[]
}

export interface ConversationDTO {
  id: string
  title: string
  provider: string
  lastMessageAt: string
  createdAt: string
}

export interface ConversationListResponse {
  conversations: ConversationDTO[]
}

export interface AIProviderInfo {
  code: string
  name: string
}

export interface AIProviderResponse {
  currentProvider: string
  providers: AIProviderInfo[]
}

/**
 * 发送消息
 */
export const sendMessage = (
  data: SendMessageRequest
): Promise<{ code: number; data: SendMessageResponse; message: string }> => {
  return axios.post('/chat/send', data)
}

/**
 * 获取对话历史
 */
export const getChatHistory = (
  limit: number = 20
): Promise<{ code: number; data: ChatHistoryResponse; message: string }> => {
  return axios.get('/chat/history', { params: { limit } })
}

/**
 * 获取指定会话的对话历史
 */
export const getChatHistoryByConversation = (
  conversationId: string,
  limit: number = 50
): Promise<{ code: number; data: ChatHistoryResponse; message: string }> => {
  return axios.get(`/chat/history/${conversationId}`, { params: { limit } })
}

/**
 * 创建新会话
 */
export const createConversation = (
  title?: string
): Promise<{ code: number; data: ConversationDTO; message: string }> => {
  return axios.post('/chat/conversation', { title })
}

/**
 * 获取会话列表
 */
export const getConversationList = (
  limit: number = 20
): Promise<{ code: number; data: ConversationListResponse; message: string }> => {
  return axios.get('/chat/conversations', { params: { limit } })
}

/**
 * 删除会话
 */
export const deleteConversation = (
  conversationId: string
): Promise<{ code: number; data: null; message: string }> => {
  return axios.delete(`/chat/conversation/${conversationId}`)
}

/**
 * 获取AI提供者信息
 */
export const getAIProviders = (): Promise<{ code: number; data: AIProviderResponse; message: string }> => {
  return axios.get('/chat/providers')
}

// ========== 流式响应相关类型 ==========

export interface StreamMessageRequest {
  message: string
  conversationId?: string | null
}

export interface StreamEventData {
  type: 'content' | 'tool_call' | 'tool_result' | 'done' | 'error' | 'token_usage'
  content?: string
  toolCall?: {
    id: string
    name: string
    arguments: string
  }
  toolResult?: {
    name: string
    result: string
  }
  tokenUsage?: {
    inputTokens: number
    outputTokens: number
    totalTokens: number
    cost: string
  }
  messageId?: string
  error?: string
}

/**
 * 发送流式消息（使用 SSE）
 * @param request 消息请求
 * @param onMessage 收到消息片段的回调
 * @param onDone 完成回调
 * @param onError 错误回调
 * @returns abort 函数，用于取消请求
 */
export const sendStreamMessage = (
  request: StreamMessageRequest,
  onMessage: (data: StreamEventData) => void,
  onDone?: () => void,
  onError?: (error: Error) => void
): (() => void) => {
  const token = localStorage.getItem('access_token')
  const url = `/api/chat/stream?message=${encodeURIComponent(request.message)}&conversationId=${request.conversationId || ''}`

  const eventSource = new EventSource(url, {
    // EventSource 不支持自定义 headers，需要通过 cookie 或其他方式传token
  })

  eventSource.onmessage = (event) => {
    try {
      const data: StreamEventData = JSON.parse(event.data)

      if (data.type === 'error') {
        onError?.(new Error(data.error || 'Unknown error'))
        eventSource.close()
        return
      }

      if (data.type === 'done') {
        onDone?.()
        eventSource.close()
        return
      }

      onMessage(data)
    } catch (e) {
      console.error('Parse SSE data error:', e)
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE error:', error)
    onError?.(new Error('Connection error'))
    eventSource.close()
  }

  // 返回关闭函数
  return () => {
    eventSource.close()
  }
}

/**
 * 使用 Fetch API 发送流式消息（推荐方式，支持自定义 headers）
 * @param request 消息请求
 * @param onMessage 收到消息片段的回调
 * @param onDone 完成回调
 * @param onError 错误回调
 * @returns abort 函数，用于取消请求
 */
export const sendStreamMessageFetch = (
  request: StreamMessageRequest,
  onMessage: (data: StreamEventData) => void,
  onDone?: () => void,
  onError?: (error: Error) => void
): (() => void) => {
  const controller = new AbortController()
  const token = localStorage.getItem('access_token')

  const params = new URLSearchParams({
    message: request.message,
    conversationId: request.conversationId || ''
  })

  fetch(`/api/chat/stream?${params.toString()}`, {
    method: 'GET',
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache'
    },
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('No response body')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          onDone?.()
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const dataStr = line.slice(6).trim()
            if (dataStr === '[DONE]') {
              onDone?.()
              return
            }

            try {
              const data: StreamEventData = JSON.parse(dataStr)

              if (data.type === 'error') {
                onError?.(new Error(data.error || 'Unknown error'))
                return
              }

              if (data.type === 'done') {
                onDone?.()
                return
              }

              onMessage(data)
            } catch (e) {
              console.error('Parse SSE data error:', e)
            }
          }
        }
      }
    })
    .catch((error) => {
      if (error.name === 'AbortError') {
        console.log('Request cancelled')
      } else {
        onError?.(error)
      }
    })

  // 返回取消函数
  return () => {
    controller.abort()
  }
}

/**
 * 语音消息响应
 */
export interface VoiceMessageResponse {
  messageId: string
  recognizedText: string
  content: string
  audioUrl?: string
  functionCalls?: ToolCallInfo[]
  createdAt: string
}

/**
 * 发送语音消息
 * @param audioBlob 音频Blob数据
 * @param format 音频格式（wav、webm等）
 * @param sampleRate 采样率
 * @param sessionId 会话ID（可选）
 */
export const sendVoiceMessage = (
  audioBlob: Blob,
  format: string = 'webm',
  sampleRate: number = 16000,
  sessionId?: string
): Promise<{ code: number; data: VoiceMessageResponse; message: string }> => {
  const formData = new FormData()
  formData.append('audio', audioBlob, `recording.${format}`)
  formData.append('format', format)
  formData.append('sampleRate', sampleRate.toString())
  if (sessionId) {
    formData.append('sessionId', sessionId)
  }

  return axios.post('/chat/voice', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
