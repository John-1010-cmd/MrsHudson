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
}

export interface MessageInfo {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  functionCall?: string
  audioUrl?: string  // 语音合成音频URL
  thinkingContent?: string  // 思考推理过程（历史消息为 null）
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

// ========== 流式响应使用 SseClient（位于 utils/sse/SseClient.ts）==========

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
