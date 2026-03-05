<template>
  <div class="metrics-container">
    <!-- 页面标题 -->
    <div class="metrics-header">
      <h1>🎯 AI成本优化实际数据统计</h1>
      <p>实时监控系统运行状态和优化效果</p>
    </div>

    <!-- 状态栏 -->
    <div class="status-bar">
      <div class="status-item">
        <div class="status-indicator online"></div>
        <span>系统运行中</span>
      </div>
      <div class="status-item">
        <span>最后更新: {{ lastUpdateTime }}</span>
      </div>
      <el-button type="primary" @click="refreshData" :loading="loading">
        <el-icon><refresh /></el-icon> 刷新数据
      </el-button>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-if="error"
      title="获取数据失败"
      :description="error"
      type="error"
      closable
      show-icon
      @close="error = ''"
      style="margin-bottom: 20px"
    />

    <!-- 指标卡片网格 -->
    <div class="metrics-grid">
      <!-- 语义缓存统计 -->
      <el-card class="metric-card cache-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <div class="card-icon cache-icon">💾</div>
            <div>
              <div class="card-title">语义缓存</div>
              <div class="card-subtitle">Semantic Cache</div>
            </div>
          </div>
        </template>
        <div class="metric-value">{{ formatPercent(semanticCacheHitRate) }}</div>
        <div class="metric-label">缓存命中率</div>
        <el-tag type="success" class="metric-tag">↓ 减少AI调用</el-tag>
        <el-divider />
        <div class="metric-details">
          <div class="detail-item">
            <span>命中次数</span>
            <span class="detail-value">{{ formatNumber(metrics?.semanticCacheHits || 0) }}</span>
          </div>
          <div class="detail-item">
            <span>未命中次数</span>
            <span class="detail-value">{{ formatNumber(metrics?.semanticCacheMisses || 0) }}</span>
          </div>
          <div class="detail-item">
            <span>缓存条目数</span>
            <span class="detail-value">{{ formatNumber(metrics?.semanticCacheEntries || 0) }}</span>
          </div>
        </div>
      </el-card>

      <!-- 意图路由统计 -->
      <el-card class="metric-card intent-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <div class="card-icon intent-icon">🧭</div>
            <div>
              <div class="card-title">意图路由</div>
              <div class="card-subtitle">Intent Router</div>
            </div>
          </div>
        </template>
        <div class="metric-value">{{ formatNumber(totalIntentRouted) }}</div>
        <div class="metric-label">总路由次数</div>
        <el-tag type="success" class="metric-tag">↓ 分流AI请求</el-tag>
        <el-divider />
        <div class="layer-stats">
          <div class="layer-stat">
            <div class="layer-info">
              <div class="layer-dot rule"></div>
              <span class="layer-name">规则层</span>
            </div>
            <div>
              <span class="layer-count">{{ formatNumber(intentStats['rule-layer'] || 0) }}</span>
              <span class="layer-percent">({{ formatPercent(intentPercentages['rule-layer'] || 0) }})</span>
            </div>
          </div>
          <div class="layer-stat">
            <div class="layer-info">
              <div class="layer-dot lightweight"></div>
              <span class="layer-name">轻量AI层</span>
            </div>
            <div>
              <span class="layer-count">{{ formatNumber(intentStats['lightweight-ai-layer'] || 0) }}</span>
              <span class="layer-percent">({{ formatPercent(intentPercentages['lightweight-ai-layer'] || 0) }})</span>
            </div>
          </div>
          <div class="layer-stat">
            <div class="layer-info">
              <div class="layer-dot full"></div>
              <span class="layer-name">完整AI层</span>
            </div>
            <div>
              <span class="layer-count">{{ formatNumber(intentStats['full-ai-layer'] || 0) }}</span>
              <span class="layer-percent">({{ formatPercent(intentPercentages['full-ai-layer'] || 0) }})</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 工具缓存统计 -->
      <el-card class="metric-card tool-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <div class="card-icon tool-icon">🛠️</div>
            <div>
              <div class="card-title">工具缓存</div>
              <div class="card-subtitle">Tool Cache</div>
            </div>
          </div>
        </template>
        <div class="metric-value">{{ formatPercent(toolCacheHitRate) }}</div>
        <div class="metric-label">工具缓存命中率</div>
        <el-tag type="success" class="metric-tag">↓ 减少API调用</el-tag>
        <el-divider />
        <div class="metric-details">
          <div class="detail-item">
            <span>命中次数</span>
            <span class="detail-value">{{ formatNumber(metrics?.toolCacheHits || 0) }}</span>
          </div>
          <div class="detail-item">
            <span>未命中次数</span>
            <span class="detail-value">{{ formatNumber(metrics?.toolCacheMisses || 0) }}</span>
          </div>
        </div>
      </el-card>

      <!-- 响应性能 -->
      <el-card class="metric-card perf-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <div class="card-icon perf-icon">⚡</div>
            <div>
              <div class="card-title">响应性能</div>
              <div class="card-subtitle">Response Performance</div>
            </div>
          </div>
        </template>
        <div class="metric-value">&lt;10ms</div>
        <div class="metric-label">缓存命中响应时间</div>
        <el-tag type="success" class="metric-tag">↑ 缓存加速</el-tag>
        <el-divider />
        <div class="metric-details">
          <div class="detail-item">
            <span>缓存命中响应</span>
            <span class="detail-value success">&lt; 10ms</span>
          </div>
          <div class="detail-item">
            <span>规则层响应</span>
            <span class="detail-value primary">&lt; 50ms</span>
          </div>
          <div class="detail-item">
            <span>AI调用响应</span>
            <span class="detail-value warning">1-3s</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 图表区域 -->
    <div class="charts-row">
      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">意图路由层分布</span>
          </div>
        </template>
        <div ref="intentChartRef" class="chart-container"></div>
      </el-card>

      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">缓存效果对比</span>
          </div>
        </template>
        <div ref="cacheChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- 数据说明 -->
    <el-card class="info-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="card-icon info-icon">ℹ️</div>
          <div>
            <div class="card-title">数据说明</div>
            <div class="card-subtitle">Data Description</div>
          </div>
        </div>
      </template>
      <div class="info-content">
        <p><strong>语义缓存：</strong>基于向量相似度的查询缓存，相似度阈值 0.92，缓存有效期 24 小时</p>
        <p><strong>意图路由：</strong>三层混合路由架构，规则层 → 轻量AI层 → 完整AI层</p>
        <p><strong>工具缓存：</strong>天气、日历等工具调用结果的短期缓存</p>
        <p><strong>数据来源：</strong>实时从系统内存中采集，每 30 秒自动刷新</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getCurrentMetrics, type MetricsSnapshot } from '../api/metrics'

// 状态
const loading = ref(false)
const error = ref('')
const metrics = ref<MetricsSnapshot | null>(null)
const lastUpdateTime = ref('--')
const autoRefreshTimer = ref<number | null>(null)

// 图表引用
const intentChartRef = ref<HTMLElement>()
const cacheChartRef = ref<HTMLElement>()
let intentChart: echarts.ECharts | null = null
let cacheChart: echarts.ECharts | null = null

// 计算属性
const semanticCacheHitRate = computed(() => {
  const hits = metrics.value?.semanticCacheHits || 0
  const misses = metrics.value?.semanticCacheMisses || 0
  const total = hits + misses
  return total > 0 ? (hits / total) * 100 : 0
})

const toolCacheHitRate = computed(() => {
  const hits = metrics.value?.toolCacheHits || 0
  const misses = metrics.value?.toolCacheMisses || 0
  const total = hits + misses
  return total > 0 ? (hits / total) * 100 : 0
})

const intentStats = computed(() => metrics.value?.intentLayerStats || {})
const intentPercentages = computed(() => metrics.value?.intentLayerPercentages || {})

const totalIntentRouted = computed(() => {
  return Object.values(intentStats.value).reduce((sum, count) => sum + count, 0)
})

// 格式化函数
const formatNumber = (num: number) => {
  return num.toLocaleString('zh-CN')
}

const formatPercent = (value: number) => {
  return value.toFixed(1) + '%'
}

const updateLastUpdateTime = () => {
  lastUpdateTime.value = new Date().toLocaleString('zh-CN')
}

// 初始化意图路由图表
const initIntentChart = () => {
  if (!intentChartRef.value) return

  intentChart = echarts.init(intentChartRef.value)
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        data: [
          { value: 0, name: '规则层', itemStyle: { color: '#10b981' } },
          { value: 0, name: '轻量AI层', itemStyle: { color: '#3b82f6' } },
          { value: 0, name: '完整AI层', itemStyle: { color: '#8b5cf6' } }
        ]
      }
    ]
  }
  intentChart.setOption(option)
}

// 初始化缓存对比图表
const initCacheChart = () => {
  if (!cacheChartRef.value) return

  cacheChart = echarts.init(cacheChartRef.value)
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      top: '5%'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: ['语义缓存', '工具缓存']
    },
    yAxis: {
      type: 'value',
      name: '次数'
    },
    series: [
      {
        name: '命中次数',
        type: 'bar',
        data: [0, 0],
        itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '未命中次数',
        type: 'bar',
        data: [0, 0],
        itemStyle: { color: '#ef4444', borderRadius: [4, 4, 0, 0] }
      }
    ]
  }
  cacheChart.setOption(option)
}

// 更新图表数据
const updateCharts = () => {
  if (intentChart) {
    intentChart.setOption({
      series: [{
        data: [
          { value: intentStats.value['rule-layer'] || 0, name: '规则层', itemStyle: { color: '#10b981' } },
          { value: intentStats.value['lightweight-ai-layer'] || 0, name: '轻量AI层', itemStyle: { color: '#3b82f6' } },
          { value: intentStats.value['full-ai-layer'] || 0, name: '完整AI层', itemStyle: { color: '#8b5cf6' } }
        ]
      }]
    })
  }

  if (cacheChart) {
    cacheChart.setOption({
      series: [
        {
          name: '命中次数',
          data: [
            metrics.value?.semanticCacheHits || 0,
            metrics.value?.toolCacheHits || 0
          ]
        },
        {
          name: '未命中次数',
          data: [
            metrics.value?.semanticCacheMisses || 0,
            metrics.value?.toolCacheMisses || 0
          ]
        }
      ]
    })
  }
}

// 刷新数据
const refreshData = async () => {
  loading.value = true
  error.value = ''

  try {
    const response = await getCurrentMetrics()
    if (response.code === 200) {
      metrics.value = response.data
      updateLastUpdateTime()
      nextTick(() => {
        updateCharts()
      })
    } else {
      error.value = response.message || '获取数据失败'
    }
  } catch (err: any) {
    error.value = `获取数据失败: ${err.message}`
    ElMessage.error('获取指标数据失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

// 自动刷新
const startAutoRefresh = () => {
  autoRefreshTimer.value = window.setInterval(() => {
    refreshData()
  }, 30000) // 30秒刷新一次
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer.value) {
    clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}

// 生命周期
onMounted(() => {
  nextTick(() => {
    initIntentChart()
    initCacheChart()
    refreshData()
    startAutoRefresh()
  })

  // 响应式图表
  window.addEventListener('resize', () => {
    intentChart?.resize()
    cacheChart?.resize()
  })
})

onUnmounted(() => {
  stopAutoRefresh()
  intentChart?.dispose()
  cacheChart?.dispose()
  window.removeEventListener('resize', () => {
    intentChart?.resize()
    cacheChart?.resize()
  })
})
</script>

<style scoped>
.metrics-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
  box-sizing: border-box;
}

.metrics-header {
  text-align: center;
  margin-bottom: 30px;
}

.metrics-header h1 {
  font-size: 2rem;
  color: #1f2937;
  margin-bottom: 8px;
}

.metrics-header p {
  color: #6b7280;
  font-size: 1rem;
}

.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 16px 24px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.status-indicator.online {
  background: #10b981;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.metric-card {
  transition: transform 0.3s;
}

.metric-card:hover {
  transform: translateY(-4px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.cache-icon { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
.intent-icon { background: linear-gradient(135deg, #8b5cf6, #6d28d9); }
.tool-icon { background: linear-gradient(135deg, #10b981, #059669); }
.perf-icon { background: linear-gradient(135deg, #f59e0b, #d97706); }
.info-icon { background: linear-gradient(135deg, #6b7280, #4b5563); }

.card-title {
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
}

.card-subtitle {
  font-size: 0.85rem;
  color: #9ca3af;
}

.metric-value {
  font-size: 2.5rem;
  font-weight: 700;
  margin: 16px 0 8px;
  color: #1f2937;
}

.cache-card .metric-value { color: #1d4ed8; }
.intent-card .metric-value { color: #6d28d9; }
.tool-card .metric-value { color: #059669; }
.perf-card .metric-value { color: #d97706; }

.metric-label {
  font-size: 0.9rem;
  color: #6b7280;
  margin-bottom: 12px;
}

.metric-tag {
  margin-top: 8px;
}

.metric-details {
  margin-top: 16px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-value {
  font-weight: 600;
  color: #374151;
}

.detail-value.success { color: #10b981; }
.detail-value.primary { color: #3b82f6; }
.detail-value.warning { color: #f59e0b; }

.layer-stats {
  margin-top: 16px;
}

.layer-stat {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
}

.layer-stat:last-child {
  border-bottom: none;
}

.layer-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.layer-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.layer-dot.rule { background: #10b981; }
.layer-dot.lightweight { background: #3b82f6; }
.layer-dot.full { background: #8b5cf6; }

.layer-name {
  font-weight: 500;
  color: #374151;
}

.layer-count {
  font-weight: 600;
  color: #1f2937;
}

.layer-percent {
  font-size: 0.85rem;
  color: #6b7280;
  margin-left: 4px;
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.chart-card {
  min-height: 400px;
}

.chart-header {
  font-weight: 600;
  color: #374151;
}

.chart-container {
  height: 320px;
}

.info-card {
  margin-top: 20px;
}

.info-content {
  color: #6b7280;
  line-height: 1.8;
}

.info-content p {
  margin: 8px 0;
}

@media (max-width: 768px) {
  .metrics-header h1 {
    font-size: 1.5rem;
  }

  .status-bar {
    flex-direction: column;
    gap: 12px;
  }

  .charts-row {
    grid-template-columns: 1fr;
  }

  .metric-value {
    font-size: 2rem;
  }
}
</style>
