<template>
  <div class="route-view">
    <div class="route-header">
      <h2>出行路线规划</h2>
    </div>

    <el-card class="route-input-card">
      <div class="input-section">
        <div class="input-row">
          <el-input
            v-model="origin"
            placeholder="请输入起点"
            clearable
          >
            <template #prefix>
              <el-icon><Location /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="input-row">
          <el-input
            v-model="destination"
            placeholder="请输入终点"
            clearable
          >
            <template #prefix>
              <el-icon><LocationInformation /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="mode-selector">
          <span class="mode-label">出行方式：</span>
          <el-radio-group v-model="mode">
            <el-radio-button label="driving">驾车</el-radio-button>
            <el-radio-button label="walking">步行</el-radio-button>
            <el-radio-button label="transit">公交</el-radio-button>
          </el-radio-group>
        </div>

        <div class="action-row">
          <el-button
            type="primary"
            size="large"
            @click="handlePlanRoute"
            :loading="loading"
          >
            <el-icon><Compass /></el-icon>
            开始规划
          </el-button>
        </div>
      </div>
    </el-card>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="8" animated />
    </div>

    <el-card v-else-if="routeResult" class="route-result-card">
      <template #header>
        <div class="result-header">
          <span class="result-title">路线规划结果</span>
          <el-tag type="info" effect="plain">{{ modeText }}</el-tag>
        </div>
      </template>

      <div class="route-detail">
        <div class="route-path">
          <div class="path-start">
            <el-icon><Location /></el-icon>
            <span>{{ origin }}</span>
          </div>
          <div class="path-arrow">
            <el-icon><Right /></el-icon>
          </div>
          <div class="path-end">
            <el-icon><LocationInformation /></el-icon>
            <span>{{ destination }}</span>
          </div>
        </div>

        <el-divider />

        <div class="route-steps">
          <pre class="steps-content">{{ routeResult.result }}</pre>
        </div>
      </div>
    </el-card>

    <el-empty
      v-else
      description="请输入起点和终点，开始规划路线"
      :image-size="120"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Location,
  LocationInformation,
  Compass,
  Right
} from '@element-plus/icons-vue'
import { planRoute } from '../api/route'

type RouteMode = 'walking' | 'driving' | 'transit'

interface RouteResult {
  result: string
}

const origin = ref('')
const destination = ref('')
const mode = ref<RouteMode>('driving')
const loading = ref(false)
const routeResult = ref<RouteResult | null>(null)

const modeText = computed(() => {
  const modeMap: Record<RouteMode, string> = {
    driving: '驾车',
    walking: '步行',
    transit: '公交'
  }
  return modeMap[mode.value]
})

const handlePlanRoute = async () => {
  if (!origin.value.trim()) {
    ElMessage.warning('请输入起点')
    return
  }

  if (!destination.value.trim()) {
    ElMessage.warning('请输入终点')
    return
  }

  loading.value = true
  routeResult.value = null

  try {
    const response = await planRoute({
      origin: origin.value.trim(),
      destination: destination.value.trim(),
      mode: mode.value
    })

    if (response.code === 200) {
      routeResult.value = response.data
    } else {
      ElMessage.error(response.message || '路线规划失败')
    }
  } catch (error: any) {
    console.error('路线规划失败:', error)
    if (error.response?.status === 401) {
      ElMessage.error('请先登录')
    } else {
      ElMessage.error('路线规划失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.route-view::-webkit-scrollbar {
  display: none;
}
.route-view {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
  min-height: calc(100vh - 100px);
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  display: flex;
  flex-direction: column;
}

.route-header {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.route-header h2 {
  margin: 0;
  color: #303133;
}

.route-input-card {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.input-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-row {
  width: 100%;
}

.input-row :deep(.el-input__wrapper) {
  padding: 4px 11px;
}

.mode-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mode-label {
  color: #606266;
  font-size: 14px;
}

.action-row {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.action-row .el-button {
  min-width: 140px;
}

.loading {
  padding: 40px;
  flex-shrink: 0;
}

/* 结果卡片 - 可滚动 */
.route-result-card {
  flex: 1;
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  display: flex;
  flex-direction: column;
}

.route-result-card :deep(.el-card__body) {
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 16px 4px 16px 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-title {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.route-detail {
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.route-path {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 8px;
  flex-shrink: 0;
}

.path-start,
.path-end {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.path-start {
  color: #67c23a;
}

.path-end {
  color: #f56c6c;
}

.path-arrow {
  color: #909399;
}

/* 路线步骤 - 可滚动区域 - 强制显示滚动条 */
.route-steps {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  overflow-x: hidden;
  padding: 0 16px 0 0;
  min-height: 200px;
}

/* Webkit 滚动条 - 始终显示 */
.route-steps::-webkit-scrollbar {
  -webkit-appearance: none;
  width: 12px;
  background-color: #e4e7ed;
  border-radius: 6px;
  display: none;
}

.route-steps::-webkit-scrollbar-track {
  background: #e4e7ed;
  border-radius: 6px;
  border: 1px solid #c0c4cc;
}

.route-steps::-webkit-scrollbar-thumb {
  background: #909399;
  border-radius: 6px;
  border: 2px solid #e4e7ed;
  min-height: 60px;
}

.route-steps::-webkit-scrollbar-thumb:hover {
  background: #606266;
}

/* Firefox 滚动条 */
.route-steps {
  scrollbar-width: none;
  scrollbar-color: #909399 #e4e7ed;
}

.steps-content {
  margin: 0;
  padding: 12px 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-wrap: break-word;
}

@media (max-width: 600px) {
  .route-view {
    height: calc(100vh - 80px);
    padding: 12px;
  }

  .route-path {
    flex-direction: column;
    gap: 8px;
  }

  .path-arrow {
    transform: rotate(90deg);
  }
}
</style>
