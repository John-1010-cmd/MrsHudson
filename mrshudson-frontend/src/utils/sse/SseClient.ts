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
  onContent?: (text: string) => void        // content 事件
  onAudioUrl?: (url: string) => void       // audio_url 事件
  onTokenUsage?: (usage: TokenUsage) => void // token_usage 事件
  onToolCall?: (tool: ToolCallInfo) => void // tool_call 事件
  onToolResult?: (result: ToolResultInfo) => void // tool_result 事件
  onCacheHit?: (content: string) => void    // cache_hit 事件
  onClarification?: (content: string) => void // clarification 事件
  onError?: (error: string) => void        // error 事件
  onDone?: () => void                       // done 事件
  onOpen?: () => void                       // 连接打开
  onClose?: () => void                     // 连接关闭
}

export class SseClient {
  private abortController: AbortController
  private timeoutId?: number

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
    switch (data.type) {
      case 'content':
        this.options.onContent?.(data.text || data.content || '')
        break
      case 'audio_url':
        this.options.onAudioUrl?.(data.url)
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
