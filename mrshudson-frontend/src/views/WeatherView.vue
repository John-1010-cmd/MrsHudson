<template>
  <div class="weather-view">
    <div class="weather-header">
      <h2>天气查询</h2>
      <div class="location-selector">
        <el-input
          v-model="city"
          placeholder="输入城市名称"
          @keyup.enter="fetchWeather"
        >
          <template #append>
            <el-button @click="fetchWeather">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>
    </div>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="weatherData" class="weather-content">
      <!-- 当前天气 -->
      <el-card class="current-weather">
        <div class="weather-main">
          <div class="city-info">
            <h3>{{ weatherData.city }}</h3>
            <p class="update-time">{{ weatherData.reportTime }} 更新</p>
          </div>
          <div class="weather-info">
            <div class="temperature">
              <span class="temp-value">{{ weatherData.temperature }}</span>
              <span class="temp-unit">°C</span>
            </div>
            <div class="weather-desc">{{ weatherData.weather }}</div>
          </div>
        </div>

        <el-divider />

        <div class="weather-details">
          <div class="detail-item">
            <el-icon><WindPower /></el-icon>
            <span>风向: {{ weatherData.windDirection || '暂无' }}</span>
          </div>
          <div class="detail-item">
            <el-icon><Flag /></el-icon>
            <span>风力: {{ weatherData.windPower || '暂无' }}</span>
          </div>
          <div class="detail-item">
            <el-icon><InfoFilled /></el-icon>
            <span>湿度: {{ weatherData.humidity || '暂无' }}%</span>
          </div>
        </div>
      </el-card>

      <!-- 未来天气预报 -->
      <el-card v-if="forecast && forecast.length > 0" class="forecast-weather">
        <template #header>
          <div class="card-header">
            <span>未来天气预报</span>
          </div>
        </template>
        <div class="forecast-list">
          <div
            v-for="(day, index) in forecast"
            :key="index"
            class="forecast-item"
          >
            <span class="forecast-date">{{ day.date }}</span>
            <span class="forecast-weather">{{ day.weather }}</span>
            <span class="forecast-temp">{{ day.tempMin }}°C ~ {{ day.tempMax }}°C</span>
          </div>
        </div>
      </el-card>
    </div>

    <el-empty v-else description="请输入城市查询天气" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, WindPower, Flag, InfoFilled } from '@element-plus/icons-vue'
import axios from 'axios'

interface WeatherData {
  city: string
  temperature: string
  weather: string
  humidity: string
  windDirection: string
  windPower: string
  reportTime: string
}

interface ForecastDay {
  date: string
  weather: string
  tempMin: string
  tempMax: string
}

const city = ref('北京')
const loading = ref(false)
const weatherData = ref<WeatherData | null>(null)
const forecast = ref<ForecastDay[]>([])

const fetchWeather = async () => {
  if (!city.value.trim()) {
    ElMessage.warning('请输入城市名称')
    return
  }

  loading.value = true
  try {
    const response = await axios.get(`/api/weather/current?city=${encodeURIComponent(city.value)}`)
    if (response.data.code === 200) {
      weatherData.value = response.data.data
      // 如果有预报数据也一并设置
      forecast.value = response.data.data.forecast || []
    } else {
      ElMessage.error(response.data.message || '获取天气失败')
    }
  } catch (error: any) {
    console.error('获取天气失败:', error)
    if (error.response?.status === 401) {
      ElMessage.error('请先登录')
    } else {
      ElMessage.error('获取天气失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 默认加载北京天气
  fetchWeather()
})
</script>

<style scoped>
.weather-view {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.weather-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.weather-header h2 {
  margin: 0;
  color: #303133;
}

.location-selector {
  width: 300px;
}

.loading {
  padding: 40px;
}

.weather-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.current-weather {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.current-weather :deep(.el-card__body) {
  color: white;
}

.weather-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.city-info h3 {
  margin: 0 0 8px 0;
  font-size: 24px;
}

.update-time {
  margin: 0;
  opacity: 0.8;
  font-size: 12px;
}

.weather-info {
  text-align: center;
}

.temperature {
  display: flex;
  align-items: flex-start;
}

.temp-value {
  font-size: 64px;
  font-weight: 300;
  line-height: 1;
}

.temp-unit {
  font-size: 24px;
  margin-top: 8px;
}

.weather-desc {
  font-size: 18px;
  margin-top: 8px;
}

.weather-details {
  display: flex;
  justify-content: space-around;
  padding: 10px 0;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.detail-item .el-icon {
  font-size: 18px;
}

.current-weather :deep(.el-divider) {
  background-color: rgba(255, 255, 255, 0.2);
  margin: 20px 0;
}

.card-header {
  font-weight: bold;
  color: #303133;
}

.forecast-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.forecast-item {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  align-items: center;
}

.forecast-date {
  font-weight: 500;
  color: #606266;
  min-width: 80px;
}

.forecast-weather {
  color: #409eff;
  font-weight: 500;
}

.forecast-temp {
  color: #909399;
  font-size: 14px;
}
</style>
