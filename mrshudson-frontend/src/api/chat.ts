import axios from './axios'

export interface SendMessageRequest {
  message: string
  sessionId?: string
}

export interface ToolCallInfo {
  name: string
  arguments: string
  result: string
}

export interface SendMessageResponse {
  messageId: string
  content: string
  functionCalls?: ToolCallInfo[]
  createdAt: string
}

export interface MessageInfo {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
}

export interface ChatHistoryResponse {
  messages: MessageInfo[]
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
 * 语音消息响应
 */
export interface VoiceMessageResponse {
  messageId: string
  recognizedText: string
  content: string
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
