<template>
  <div class="reminder-view">
    <!-- 头部标题 -->
    <div class="reminder-header">
      <h1 class="page-title">提醒中心</h1>
      <p class="page-subtitle">查看和管理您的提醒通知</p>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="filter-tabs">
        <div
          v-for="tab in tabs"
          :key="tab.value"
          class="filter-tab"
          :class="{ active: currentFilter === tab.value }"
          @click="currentFilter = tab.value"
        >
          <span class="tab-label">{{ tab.label }}</span>
          <span class="tab-count">{{ getTabCount(tab.value) }}</span>
        </div>
      </div>
      <el-button
        v-if="unreadCount > 0"
        type="primary"
        text
        @click="handleMarkAllAsRead"
      >
        全部标为已读
      </el-button>
    </div>

    <!-- 提醒列表 -->
    <div class="reminder-list-container" v-loading="loading">
      <div v-if="filteredReminders.length === 0" class="empty-state">
        <el-icon class="empty-icon"><Bell /></el-icon>
        <p class="empty-text">{{ emptyText }}</p>
      </div>
      <div v-else class="reminder-list">
        <ReminderItem
          v-for="reminder in filteredReminders"
          :key="reminder.id"
          :reminder="reminder"
          @mark-read="handleMarkAsRead"
          @snooze="handleSnooze"
          @click="handleReminderClick"
        />
      </div>
    </div>

    <!-- 延迟提醒对话框 -->
    <el-dialog v-model="snoozeDialogVisible" title="延迟提醒" width="400px">
      <div class="snooze-options">
        <div
          v-for="option in snoozeOptions"
          :key="option.value"
          class="snooze-option"
          @click="confirmSnooze(option.value)"
        >
          <el-icon><Clock /></el-icon>
          <span>{{ option.label }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Clock } from '@element-plus/icons-vue'
import * as reminderApi from '../api/reminder'
import type { Reminder } from '../api/reminder'
import ReminderItem from '../components/ReminderItem.vue'

type FilterType = 'all' | 'EVENT' | 'TODO' | 'unread'

interface Tab {
  label: string
  value: FilterType
}

const router = useRouter()
const tabs: Tab[] = [
  { label: '全部', value: 'all' },
  { label: '日程', value: 'EVENT' },
  { label: '待办', value: 'TODO' },
  { label: '未读', value: 'unread' }
]

const snoozeOptions = [
  { label: '5分钟后', value: 5 },
  { label: '15分钟后', value: 15 },
  { label: '30分钟后', value: 30 },
  { label: '1小时后', value: 60 },
  { label: '明天同一时间', value: 1440 }
]

const reminders = ref<Reminder[]>([])
const loading = ref(false)
const currentFilter = ref<FilterType>('all')
const unreadCount = ref(0)
const snoozeDialogVisible = ref(false)
const selectedReminderId = ref<number | null>(null)

const filteredReminders = computed(() => {
  switch (currentFilter.value) {
    case 'EVENT':
      return reminders.value.filter(r => r.type === 'EVENT')
    case 'TODO':
      return reminders.value.filter(r => r.type === 'TODO')
    case 'unread':
      return reminders.value.filter(r => !r.isRead)
    default:
      return reminders.value
  }
})

const emptyText = computed(() => {
  switch (currentFilter.value) {
    case 'EVENT':
      return '暂无日程提醒'
    case 'TODO':
      return '暂无待办提醒'
    case 'unread':
      return '暂无未读提醒'
    default:
      return '暂无提醒'
  }
})

const getTabCount = (filter: FilterType): number => {
  switch (filter) {
    case 'EVENT':
      return reminders.value.filter(r => r.type === 'EVENT').length
    case 'TODO':
      return reminders.value.filter(r => r.type === 'TODO').length
    case 'unread':
      return unreadCount.value
    default:
      return reminders.value.length
  }
}

const loadReminders = async () => {
  loading.value = true
  try {
    const [res, countRes] = await Promise.all([
      reminderApi.getReminders(),
      reminderApi.getUnreadCount()
    ])
    if (res.code === 200) {
      reminders.value = res.data
    }
    if (countRes.code === 200) {
      unreadCount.value = countRes.data.count
    }
  } catch (error) {
    console.error('加载提醒失败:', error)
  } finally {
    loading.value = false
  }
}

const handleMarkAsRead = async (id: number) => {
  try {
    const res = await reminderApi.markAsRead(id)
    if (res.code === 200) {
      const reminder = reminders.value.find(r => r.id === id)
      if (reminder) {
        reminder.isRead = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
      ElMessage.success('已标记为已读')
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

const handleMarkAllAsRead = async () => {
  try {
    const res = await reminderApi.markAllAsRead()
    if (res.code === 200) {
      reminders.value.forEach(r => r.isRead = true)
      unreadCount.value = 0
      ElMessage.success('已全部标记为已读')
    }
  } catch (error) {
    console.error('批量标记已读失败:', error)
  }
}

const handleSnooze = (id: number) => {
  selectedReminderId.value = id
  snoozeDialogVisible.value = true
}

const confirmSnooze = async (minutes: number) => {
  if (!selectedReminderId.value) return

  try {
    const res = await reminderApi.snoozeReminder(selectedReminderId.value, minutes)
    if (res.code === 200) {
      // 更新列表中的提醒
      const index = reminders.value.findIndex(r => r.id === selectedReminderId.value)
      if (index !== -1) {
        reminders.value[index] = res.data
      }
      ElMessage.success(`已延迟 ${minutes} 分钟`)
    }
  } catch (error) {
    console.error('延迟提醒失败:', error)
  } finally {
    snoozeDialogVisible.value = false
    selectedReminderId.value = null
  }
}

const handleReminderClick = (reminder: Reminder) => {
  // 点击跳转到对应页面
  if (reminder.type === 'EVENT') {
    router.push({ path: '/calendar', query: { eventId: reminder.refId } })
  } else if (reminder.type === 'TODO') {
    router.push({ path: '/todo', query: { todoId: reminder.refId } })
  }
}

onMounted(() => {
  loadReminders()
})
</script>

<style scoped>
.reminder-view {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.reminder-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.page-subtitle {
  color: #909399;
  margin: 8px 0 0;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filter-tabs {
  display: flex;
  gap: 8px;
}

.filter-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s;
  background: #f5f7fa;
}

.filter-tab:hover {
  background: #ecf5ff;
}

.filter-tab.active {
  background: #409eff;
  color: white;
}

.tab-label {
  font-size: 14px;
}

.tab-count {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.1);
}

.filter-tab.active .tab-count {
  background: rgba(255, 255, 255, 0.3);
}

.reminder-list-container {
  min-height: 400px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
}

.reminder-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.snooze-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.snooze-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.snooze-option:hover {
  background: #f5f7fa;
}
</style>
