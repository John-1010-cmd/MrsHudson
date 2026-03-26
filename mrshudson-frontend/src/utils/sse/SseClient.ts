/**
 * SSE 流式客户端 - 统一处理层
 *
 * 封装 fetch + ReadableStream + 事件解析 + 超时保护 + 异常处理
 * 遵循 SSE_STREAM_SPEC.md 规范
 */

export interface TokenUsage {
  inputTokens: number
  outputTokens: number
  duration: number
  model: string
}

export interface ToolCallInfo {
  name: string
  arguments: string
}

export interface ToolResultInfo {
  name: string
  result: string
}

export interface SseClientOptions {
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  body?: any
  timeout?: number  // 超时时间(ms)，默认 60000

  // 状态回调
  onConnecting?: () => void      // 刚发起连接
  onFirstContent?: () => void    // 收到首个 content 事件

  // 事件回调
  onContent?: (text: string, conversationId: number | null, messageId: number | null) => void
  onContentDone?: (conversationId: number | null, messageId: number | null) => void
  onAudioDone?: (event: AudioDoneEvent) => void
  onTokenUsage?: (usage: TokenUsage) => void
  onToolCall?: (tool: ToolCallInfo) => void
  onToolResult?: (result: ToolResultInfo) => void
  onCacheHit?: (content: string) => void
  onClarification?: (content: string) => void
  onError?: (error: string) => void
  onDone?: () => void
  onOpen?: () => void
  onClose?: () => void
}

export interface AudioDoneEvent {
  conversationId: number | null
  messageId: number | null
  url: string | null      // 音频 URL，仅 TTS 成功时有值
  timeout: boolean        // 超时（后台继续合成，历史消息可能有 URL）
  error: string | null    // 合成异常信息
  noaudio: boolean        // 提供商返回空（历史消息也不会有 URL）
}

export class SseClient {
  private abortController: AbortController
  private timeoutId?: number
  private _firstContentReceived = false

  constructor(private options: SseClientOptions) {
    this.abortController = new AbortController()
  }

  async stream(): Promise<void> {
    const { timeout = 60000 } = this.options
    const startTime = Date.now()
    let lastEventTime = startTime

    const response = await fetch(this.options.url, {
      method: this.options.method || 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...this.options.headers
      },
      body: JSON.stringify(this.options.body),
      signal: this.abortController.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')

    this.options.onOpen?.()

    const decoder = new TextDecoder()
    let buffer = ''
    let doneReceived = false

    try {
      while (true) {
        // 超时保护：60秒无数据则中断
        const { done, value } = await Promise.race([
          reader.read(),
          new Promise<{ done: true; value: undefined }>((_, reject) => {
            this.timeoutId = window.setTimeout(() => reject(new Error('SSE_TIMEOUT')), timeout)
          })
        ])

        if (this.timeoutId) {
          clearTimeout(this.timeoutId)
          this.timeoutId = undefined
        }

        lastEventTime = Date.now()

        if (done) {
          if (!doneReceived) {
            console.warn('[SSE] 连接关闭但未收到 done 事件')
          }
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const dataStr = this.extractSseData(line)
          if (!dataStr) continue

          try {
            const data = JSON.parse(dataStr)
            this.handleEvent(data)
            if (data.type === 'done') {
              doneReceived = true
            }
          } catch (e) {
            console.error('[SSE] JSON 解析失败:', e)
          }
        }
      }
    } finally {
      this.options.onClose?.()
    }
  }

  /**
   * 从 SSE 行数据中提取 JSON 字符串
   * 处理 data: {...} 格式，支持 data: 和 JSON 之间的空白字符
   */
  private extractSseData(line: string): string | null {
    const trimmed = line.trim()
    if (!trimmed) return null
    if (trimmed.startsWith('data:')) {
      // 提取 data: 后面的内容，并去除所有空白（包括换行符）
      const dataContent = trimmed.slice(5).replace(/\s+/g, '').trim()
      if (dataContent.startsWith('{') && dataContent.endsWith('}')) {
        return dataContent
      }
    }
    return null
  }

  /**
   * 处理 SSE 事件
   */
  private handleEvent(data: any): void {
    const conversationId: number | null = data.conversationId ?? null
    const messageId: number | null = data.messageId ?? null

    switch (data.type) {
      case 'content':
        if (!this._firstContentReceived) {
          this._firstContentReceived = true
          this.options.onFirstContent?.()
        }
        this.options.onContent?.(data.text || data.content || '', conversationId, messageId)
        break
      case 'content_done':
        this.options.onContentDone?.(conversationId, messageId)
        break
      case 'audio_done':
        this.options.onAudioDone?.({
          conversationId,
          messageId,
          url: data.url || null,
          timeout: data.timeout || false,
          error: data.error || null,
          noaudio: data.noaudio || false
        })
        break
      case 'token_usage':
        this.options.onTokenUsage?.({
          inputTokens: data.inputTokens || 0,
          outputTokens: data.outputTokens || 0,
          duration: data.duration || 0,
          model: data.model || 'unknown'
        })
        break
      case 'tool_call':
        if (data.toolCall) {
          this.options.onToolCall?.({
            name: data.toolCall.name || '',
            arguments: data.toolCall.arguments || ''
          })
        }
        break
      case 'tool_result':
        if (data.toolResult) {
          this.options.onToolResult?.({
            name: data.toolResult.name || '',
            result: data.toolResult.result || ''
          })
        }
        break
      case 'cache_hit':
        this.options.onCacheHit?.(data.content || '')
        break
      case 'clarification':
        this.options.onClarification?.(data.content || '')
        break
      case 'error':
        this.options.onError?.(data.message || 'Unknown error')
        break
      case 'done':
        this.options.onDone?.()
        break
    }
  }

  /**
   * 中断 SSE 连接
   */
  abort(): void {
    this.abortController.abort()
    if (this.timeoutId) {
      clearTimeout(this.timeoutId)
    }
  }
}
