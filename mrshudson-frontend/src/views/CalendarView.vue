<template>
  <div class="calendar-view">
    <!-- 头部工具栏 -->
    <div class="calendar-header">
      <div class="header-left">
        <el-button-group>
          <el-button @click="goToPrevMonth">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <el-button @click="goToToday">今天</el-button>
          <el-button @click="goToNextMonth">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </el-button-group>
        <h2 class="current-month">{{ currentYearMonth }}</h2>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建事件
        </el-button>
      </div>
    </div>

    <!-- 日历网格 -->
    <div class="calendar-container">
      <!-- 星期标题 -->
      <div class="week-header">
        <div v-for="day in weekDays" :key="day" class="week-day">{{ day }}</div>
      </div>

      <!-- 日期网格 -->
      <div class="days-grid">
        <div
          v-for="day in calendarDays"
          :key="day.date"
          class="day-cell"
          :class="{
            'other-month': !day.isCurrentMonth,
            'today': day.isToday,
            'selected': selectedDate === day.date
          }"
          @click="selectDate(day)"
        >
          <div class="day-number">{{ day.dayOfMonth }}</div>
          <div class="day-events">
            <div
              v-for="event in day.events.slice(0, 3)"
              :key="event.id"
              class="event-item"
              :class="`category-${event.category.toLowerCase()}`"
              @click.stop="openEventDetail(event)"
            >
              {{ event.title }}
            </div>
            <div v-if="day.events.length > 3" class="more-events">
              +{{ day.events.length - 3 }} 更多
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑事件弹窗 -->
    <EventDialog
      v-model:visible="dialogVisible"
      :initial-date="selectedDate"
      :event="editingEvent"
      @save="handleSaveEvent"
      @delete="handleDeleteEvent"
    />

    <!-- 事件详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="事件详情"
      width="400px"
    >
      <div v-if="viewingEvent" class="event-detail">
        <h3>{{ viewingEvent.title }}</h3>
        <p class="detail-item">
          <el-icon><Clock /></el-icon>
          {{ formatDateTime(viewingEvent.startTime) }} ~ {{ formatDateTime(viewingEvent.endTime) }}
        </p>
        <p v-if="viewingEvent.location" class="detail-item">
          <el-icon><Location /></el-icon>
          {{ viewingEvent.location }}
        </p>
        <p class="detail-item">
          <el-icon><CollectionTag /></el-icon>
          {{ getCategoryLabel(viewingEvent.category) }}
        </p>
        <p v-if="viewingEvent.description" class="detail-item description">
          {{ viewingEvent.description }}
        </p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="editEvent">编辑</el-button>
        <el-button type="danger" @click="confirmDelete">删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Plus, Clock, Location, CollectionTag } from '@element-plus/icons-vue'
import * as calendarApi from '../api/calendar'
import type { EventResponse } from '../api/calendar'
import EventDialog from '../components/EventDialog.vue'

const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const currentDate = ref(new Date())
const selectedDate = ref('')
const events = ref<EventResponse[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const viewingEvent = ref<EventResponse | null>(null)
const editingEvent = ref<EventResponse | null>(null)

const currentYearMonth = computed(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth() + 1
  return `${year}年${month}月`
})

const calendarDays = computed(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth()

  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)

  const startDate = new Date(firstDay)
  startDate.setDate(startDate.getDate() - firstDay.getDay())

  const days: {
    date: string
    dayOfMonth: number
    isCurrentMonth: boolean
    isToday: boolean
    events: EventResponse[]
  }[] = []

  const today = new Date()
  const todayStr = formatDate(today)

  for (let i = 0; i < 42; i++) {
    const date = new Date(startDate)
    date.setDate(startDate.getDate() + i)

    const dateStr = formatDate(date)
    const isCurrentMonth = date.getMonth() === month
    const isToday = dateStr === todayStr

    const dayEvents = events.value.filter(event => {
      const eventDate = event.startTime.split('T')[0]
      return eventDate === dateStr
    })

    days.push({
      date: dateStr,
      dayOfMonth: date.getDate(),
      isCurrentMonth,
      isToday,
      events: dayEvents
    })
  }

  return days
})

const formatDate = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const formatDateTime = (dateTimeStr: string): string => {
  const date = new Date(dateTimeStr)
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const getCategoryLabel = (category: string): string => {
  const labels: Record<string, string> = {
    WORK: '工作',
    PERSONAL: '个人',
    FAMILY: '家庭'
  }
  return labels[category] || category
}

const goToPrevMonth = () => {
  currentDate.value = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() - 1, 1)
  loadEvents()
}

const goToNextMonth = () => {
  currentDate.value = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() + 1, 1)
  loadEvents()
}

const goToToday = () => {
  currentDate.value = new Date()
  loadEvents()
}

const selectDate = (day: { date: string; events: EventResponse[] }) => {
  selectedDate.value = day.date
}

const openCreateDialog = () => {
  editingEvent.value = null
  if (!selectedDate.value) {
    selectedDate.value = formatDate(new Date())
  }
  dialogVisible.value = true
}

const openEventDetail = (event: EventResponse) => {
  viewingEvent.value = event
  detailVisible.value = true
}

const editEvent = () => {
  if (viewingEvent.value) {
    editingEvent.value = viewingEvent.value
    detailVisible.value = false
    dialogVisible.value = true
  }
}

const confirmDelete = () => {
  if (!viewingEvent.value) return

  ElMessageBox.confirm('确定要删除这个事件吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    handleDeleteEvent(viewingEvent.value!.id)
  }).catch(() => {})
}

const loadEvents = async () => {
  try {
    const year = currentDate.value.getFullYear()
    const month = currentDate.value.getMonth()
    const startDate = formatDate(new Date(year, month, 1))
    const endDate = formatDate(new Date(year, month + 1, 0))

    const res = await calendarApi.getEvents(startDate, endDate)
    if (res.code === 200) {
      events.value = res.data
    }
  } catch (error) {
    ElMessage.error('加载事件失败')
  }
}

const handleSaveEvent = async (eventData: calendarApi.CreateEventRequest) => {
  try {
    if (editingEvent.value) {
      const res = await calendarApi.updateEvent(editingEvent.value.id, eventData)
      if (res.code === 200) {
        ElMessage.success('事件已更新')
        dialogVisible.value = false
        loadEvents()
      }
    } else {
      const res = await calendarApi.createEvent(eventData)
      if (res.code === 200) {
        ElMessage.success('事件已创建')
        dialogVisible.value = false
        loadEvents()
      }
    }
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteEvent = async (eventId: number) => {
  try {
    const res = await calendarApi.deleteEvent(eventId)
    if (res.code === 200) {
      ElMessage.success('事件已删除')
      detailVisible.value = false
      loadEvents()
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadEvents()
})
</script>

<style scoped>
.calendar-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
  background: #fff;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.current-month {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
}

.calendar-container {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.week-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.week-day {
  padding: 12px;
  text-align: center;
  font-weight: 500;
  color: #606266;
}

.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 1fr);
  height: calc(100% - 45px);
}

.day-cell {
  border-right: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  padding: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  min-height: 100px;
}

.day-cell:nth-child(7n) {
  border-right: none;
}

.day-cell:hover {
  background-color: #f5f7fa;
}

.day-cell.other-month {
  color: #c0c4cc;
  background-color: #fafafa;
}

.day-cell.today {
  background-color: #ecf5ff;
}

.day-cell.today .day-number {
  color: #409eff;
  font-weight: bold;
}

.day-cell.selected {
  background-color: #e6f7ff;
  box-shadow: inset 0 0 0 2px #409eff;
}

.day-number {
  font-size: 14px;
  margin-bottom: 4px;
}

.day-events {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.event-item {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.event-item:hover {
  opacity: 0.8;
}

.category-work {
  background-color: #fde2e2;
  color: #f56c6c;
}

.category-personal {
  background-color: #e1f3d8;
  color: #67c23a;
}

.category-family {
  background-color: #d9ecff;
  color: #409eff;
}

.more-events {
  font-size: 11px;
  color: #909399;
  padding: 2px 6px;
}

.event-detail h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  color: #606266;
}

.detail-item .el-icon {
  color: #909399;
}

.detail-item.description {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
  white-space: pre-wrap;
}
</style>
