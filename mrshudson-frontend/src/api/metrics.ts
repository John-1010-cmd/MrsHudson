import axios from './axios'

export interface MetricsSnapshot {
  timestamp: string
  intentLayerStats: Record<string, number>
  intentLayerPercentages: Record<string, number>
  semanticCacheHits: number
  semanticCacheMisses: number
  semanticCacheHitRate: number
  toolCacheHits: number
  toolCacheMisses: number
  toolCacheHitRate: number
  semanticCacheEntries: number
}

export interface DailyMetrics {
  date: string
  aiCallCount: number
  cacheHitCount: number
  totalCost: number
  savedCost: number
}

export interface ComparisonData {
  before: {
    aiCallCount: number
    avgResponseTime: number
    monthlyCost: number
    toolQueryZeroAiRate: number
  }
  after: {
    semanticCacheHitRate: number
    toolCacheHitRate: number
    intentLayerStats: Record<string, number>
    intentLayerPercentages: Record<string, number>
  }
  theoreticalSavings: number
}

/**
 * 获取当前指标快照
 */
export const getCurrentMetrics = (): Promise<{ code: number; data: MetricsSnapshot; message: string }> => {
  return axios.get('/admin/metrics/current')
}

/**
 * 获取趋势数据
 */
export const getMetricsTrend = (days: number = 7): Promise<{ code: number; data: DailyMetrics[]; message: string }> => {
  return axios.get('/admin/metrics/trend', { params: { days } })
}

/**
 * 获取指定日期统计
 */
export const getDailyMetrics = (date: string): Promise<{ code: number; data: DailyMetrics; message: string }> => {
  return axios.get(`/admin/metrics/daily/${date}`)
}

/**
 * 获取优化前后对比数据
 */
export const getComparison = (): Promise<{ code: number; data: ComparisonData; message: string }> => {
  return axios.get('/admin/metrics/comparison')
}

/**
 * 重置统计数据
 */
export const resetMetrics = (): Promise<{ code: number; data: null; message: string }> => {
  return axios.post('/admin/metrics/reset')
}
