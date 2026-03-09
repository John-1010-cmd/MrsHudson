<template>
  <div
    class="reminder-item"
    :class="{ unread: !reminder.isRead }"
    @click="$emit('click', reminder)"
  >
    <!-- 类型图标 -->
    <div class="reminder-icon" :class="typeClass">
      <el-icon><Calendar v-if="reminder.type === 'EVENT'" /><List v-else-if="reminder.type === 'TODO'" /><Warning v-else-if="reminder.type === 'WEATHER'" /><InfoFilled v-else /></el-icon>
    </div>

    <!-- 内容 -->
    <div class="reminder-content">
      <div class="reminder-header">
        <span class="reminder-title">{{ reminder.title }}</span>
        <el-tag v-if="!reminder.isRead" type="danger" size="small">未读</el-tag>
      </div>
      <div v-if="reminder.content" class="reminder-body">
        {{ reminder.content }}
      </div>
      <div class="reminder-footer">
        <span class="reminder-time">
          <el-icon><Clock /></el-icon>
          {{ formattedTime }}
        </span>
        <span class="reminder-type">{{ typeText }}</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="reminder-actions" @click.stop>
      <el-dropdown trigger="click">
        <el-button text>
          <el-icon><MoreFilled /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="!reminder.isRead" @click="$emit('mark-read', reminder.id)">
              <el-icon><Check /></el-icon>
              标记已读
            </el-dropdown-item>
            <el-dropdown-item @click="$emit('snooze', reminder.id)">
              <el-icon><Clock /></el-icon>
              延迟提醒
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Calendar, List, Warning, InfoFilled, Clock, MoreFilled, Check } from '@element-plus/icons-vue'
import type { Reminder } from '../api/reminder'

const props = defineProps<{
  reminder: Reminder
}>()

defineEmits<{
  'mark-read': [id: number]
  'snooze': [id: number]
  'click': [reminder: Reminder]
}>()

const typeClass = computed(() => {
  switch (props.reminder.type) {
    case 'EVENT':
      return 'type-event'
    case 'TODO':
      return 'type-todo'
    case 'WEATHER':
      return 'type-weather'
    default:
      return 'type-system'
  }
})

const typeText = computed(() => {
  switch (props.reminder.type) {
    case 'EVENT':
      return '日程'
    case 'TODO':
      return '待办'
    case 'WEATHER':
      return '天气'
    default:
      return '系统'
  }
})

const formattedTime = computed(() => {
  const time = new Date(props.reminder.remindAt)
  const now = new Date()
  const diffMs = time.getTime() - now.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 0) {
    // 已过期
    return '已过期'
  } else if (diffMins < 60) {
    return `${diffMins}分钟后`
  } else if (diffHours < 24) {
    return `${diffHours}小时后`
  } else if (diffDays < 7) {
    return `${diffDays}天后`
  } else {
    return time.toLocaleDateString('zh-CN')
  }
})
</script>

<style scoped>
.reminder-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s;
}

.reminder-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.reminder-item.unread {
  background: #f0f9ff;
  border-left: 4px solid #409eff;
}

.reminder-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}

.reminder-icon.type-event {
  background: #e6f7ff;
  color: #1890ff;
}

.reminder-icon.type-todo {
  background: #fff7e6;
  color: #fa8c16;
}

.reminder-icon.type-weather {
  background: #f6ffed;
  color: #52c41a;
}

.reminder-icon.type-system {
  background: #f9f0ff;
  color: #722ed1;
}

.reminder-content {
  flex: 1;
  min-width: 0;
}

.reminder-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.reminder-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.reminder-body {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  white-space: pre-line;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.reminder-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.reminder-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

.reminder-type {
  padding: 2px 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.reminder-actions {
  flex-shrink: 0;
}
</style>
