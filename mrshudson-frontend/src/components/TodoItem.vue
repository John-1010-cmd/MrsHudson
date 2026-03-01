<template>
  <div class="todo-item" :class="{ 'is-completed': todo.completed }">
    <div class="todo-main">
      <el-checkbox
        :model-value="todo.completed"
        @change="handleComplete"
        size="large"
        class="todo-checkbox"
      />
      <div class="todo-content">
        <span class="todo-title" :class="{ 'is-completed': todo.completed }">
          {{ todo.title }}
        </span>
        <div class="todo-meta">
          <el-tag
            :type="priorityType"
            size="small"
            effect="light"
            class="priority-tag"
          >
            {{ priorityLabel }}
          </el-tag>
          <span v-if="todo.dueDate" class="due-date">
            <el-icon><Calendar /></el-icon>
            {{ formatDueDate(todo.dueDate) }}
          </span>
        </div>
      </div>
    </div>
    <el-button
      type="danger"
      link
      size="small"
      @click="handleDelete"
      class="delete-btn"
    >
      <el-icon><Delete /></el-icon>
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Delete } from '@element-plus/icons-vue'
import type { TodoResponse, Priority } from '../api/todo'
import * as todoApi from '../api/todo'

const props = defineProps<{
  todo: TodoResponse
}>()

const emit = defineEmits<{
  update: [todo: TodoResponse]
  delete: [id: number]
}>()

const priorityType = computed(() => {
  const types: Record<Priority, 'danger' | 'warning' | 'success'> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return types[props.todo.priority]
})

const priorityLabel = computed(() => {
  const labels: Record<Priority, string> = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低'
  }
  return labels[props.todo.priority]
})

const formatDueDate = (dateStr: string): string => {
  const date = new Date(dateStr)
  const today = new Date()
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)

  const dateOnly = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const todayOnly = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const tomorrowOnly = new Date(tomorrow.getFullYear(), tomorrow.getMonth(), tomorrow.getDate())

  if (dateOnly.getTime() === todayOnly.getTime()) {
    return '今天'
  } else if (dateOnly.getTime() === tomorrowOnly.getTime()) {
    return '明天'
  } else {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }
}

const handleComplete = async (completed: boolean) => {
  try {
    const res = await todoApi.completeTodo(props.todo.id, completed)
    if (res.code === 200) {
      emit('update', res.data)
      ElMessage.success(completed ? '已完成' : '已取消完成')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要删除这个待办事项吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await todoApi.deleteTodo(props.todo.id)
    if (res.code === 200) {
      emit('delete', props.todo.id)
      ElMessage.success('已删除')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}
</script>

<style scoped>
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.2s ease;
  margin-bottom: 8px;
}

.todo-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border-color: #d9d9d9;
}

.todo-item.is-completed {
  background: #f5f7fa;
}

.todo-main {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 12px;
}

.todo-checkbox {
  flex-shrink: 0;
}

.todo-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.todo-title {
  font-size: 14px;
  color: #303133;
  line-height: 1.5;
  word-break: break-word;
}

.todo-title.is-completed {
  text-decoration: line-through;
  color: #909399;
}

.todo-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.priority-tag {
  font-size: 11px;
}

.due-date {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.due-date .el-icon {
  font-size: 12px;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.todo-item:hover .delete-btn {
  opacity: 1;
}

@media (max-width: 768px) {
  .delete-btn {
    opacity: 1;
  }
}
</style>
