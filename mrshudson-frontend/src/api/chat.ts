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
