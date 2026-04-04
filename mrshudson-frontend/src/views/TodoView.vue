<template>
  <div class="todo-view">
    <!-- 头部标题 -->
    <div class="todo-header">
      <h1 class="page-title">待办事项</h1>
      <p class="page-subtitle">管理您的任务，保持高效</p>
    </div>

    <!-- 筛选标签 -->
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

    <!-- 待办列表 -->
    <div class="todo-list-container" v-loading="loading">
      <div v-if="filteredTodos.length === 0" class="empty-state">
        <el-icon class="empty-icon"><CircleCheck /></el-icon>
        <p class="empty-text">{{ emptyText }}</p>
      </div>
      <div v-else class="todo-list">
        <TodoItem
          v-for="todo in filteredTodos"
          :key="todo.id"
          :todo="todo"
          @update="handleTodoUpdate"
          @delete="handleTodoDelete"
        />
      </div>
    </div>

    <!-- 底部快速添加 -->
    <div class="quick-add">
      <div class="quick-add-form">
        <el-select
          v-model="newTodoPriority"
          placeholder="优先级"
          size="large"
          class="priority-select"
        >
          <el-option label="高" value="HIGH" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="低" value="LOW" />
        </el-select>
        <el-input
          v-model="newTodoTitle"
          placeholder="添加新的待办事项..."
          size="large"
          @keyup.enter="handleQuickAdd"
          class="title-input"
        />
        <el-button
          type="primary"
          @click="handleQuickAdd"
          :disabled="!newTodoTitle.trim()"
          :loading="adding"
          size="large"
        >
          <el-icon><Plus /></el-icon>
          添加
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, CircleCheck } from '@element-plus/icons-vue'
import * as todoApi from '../api/todo'
import type { TodoResponse } from '../api/todo'
import TodoItem from '../components/TodoItem.vue'

type FilterType = 'all' | 'active' | 'completed'

interface Tab {
  label: string
  value: FilterType
}

const tabs: Tab[] = [
  { label: '全部', value: 'all' },
  { label: '进行中', value: 'active' },
  { label: '已完成', value: 'completed' }
]

const todos = ref<TodoResponse[]>([])
const currentFilter = ref<FilterType>('all')
const loading = ref(false)
const adding = ref(false)
const newTodoTitle = ref('')
const newTodoPriority = ref<todoApi.Priority>('MEDIUM')

const filteredTodos = computed(() => {
  switch (currentFilter.value) {
    case 'active':
      return todos.value.filter(todo => !todo.completed)
    case 'completed':
      return todos.value.filter(todo => todo.completed)
    default:
      return todos.value
  }
})

const emptyText = computed(() => {
  switch (currentFilter.value) {
    case 'active':
      return '暂无进行中的待办事项'
    case 'completed':
      return '暂无已完成的待办事项'
    default:
      return '暂无待办事项，添加一个吧！'
  }
})

const getTabCount = (filter: FilterType): number => {
  switch (filter) {
    case 'active':
      return todos.value.filter(t => !t.completed).length
    case 'completed':
      return todos.value.filter(t => t.completed).length
    default:
      return todos.value.length
  }
}

const loadTodos = async () => {
  loading.value = true
  try {
    const res = await todoApi.getTodos()
    if (res.code === 200) {
      todos.value = res.data
    }
  } catch (error) {
    ElMessage.error('加载待办事项失败')
  } finally {
    loading.value = false
  }
}

const handleQuickAdd = async () => {
  const title = newTodoTitle.value.trim()
  if (!title) return

  adding.value = true
  try {
    const res = await todoApi.createTodo({
      title,
      priority: newTodoPriority.value
    })
    if (res.code === 200) {
      todos.value.unshift(res.data)
      newTodoTitle.value = ''
      ElMessage.success('待办事项已添加')
    }
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    adding.value = false
  }
}

const handleTodoUpdate = (updatedTodo: TodoResponse) => {
  const index = todos.value.findIndex(t => t.id === updatedTodo.id)
  if (index !== -1) {
    todos.value[index] = updatedTodo
  }
}

const handleTodoDelete = (id: number) => {
  todos.value = todos.value.filter(t => t.id !== id)
}

onMounted(() => {
  loadTodos()
})
</script>

<style scoped>
.todo-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
  background: #fafafa;
}

.todo-header {
  text-align: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.filter-tabs {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 20px;
  padding: 4px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.filter-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #666;
  font-size: 14px;
}

.filter-tab:hover {
  background: #f5f5f5;
  color: #333;
}

.filter-tab.active {
  background: #1a1a1a;
  color: #fff;
}

.tab-count {
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.filter-tab.active .tab-count {
  background: rgba(255, 255, 255, 0.2);
}

.todo-list-container {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.todo-list {
  display: flex;
  flex-direction: column;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #ddd;
}

.empty-text {
  font-size: 14px;
  margin: 0;
}

.quick-add {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e8e8e8;
}

.quick-add-form {
  display: flex;
  align-items: center;
  gap: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 4px;
}

.priority-select {
  width: 70px;
  flex-shrink: 0;
}

.priority-select :deep(.el-input__wrapper) {
  background: #f5f5f5;
  border-radius: 8px 0 0 8px;
  box-shadow: none !important;
  padding: 0 8px;
}

.priority-select :deep(.el-input__inner) {
  text-align: center;
  font-size: 14px;
  padding: 0;
}

.priority-select :deep(.el-input__suffix) {
  display: none;
}

.title-input {
  flex: 1;
  min-width: 0;
}

.title-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent;
}

.title-input :deep(.el-input__inner) {
  font-size: 14px;
}

.quick-add-form .el-button {
  border-radius: 0 8px 8px 0;
  margin-left: 0;
  height: 40px;
  padding: 0 20px;
  font-size: 14px;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .todo-view {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .filter-tabs {
    gap: 4px;
  }

  .filter-tab {
    padding: 6px 12px;
    font-size: 13px;
  }

  .tab-count {
    display: none;
  }

  .quick-add-form {
    flex-wrap: wrap;
    gap: 8px;
    padding: 12px;
  }

  .priority-select {
    width: 80px;
    order: 1;
  }

  .priority-select :deep(.el-input__wrapper) {
    border-radius: 8px;
  }

  .title-input {
    width: 100%;
    order: 2;
  }

  .quick-add-form .el-button {
    width: 100%;
    border-radius: 8px;
    order: 3;
  }
}

/* 滚动条样式 */
.todo-list-container::-webkit-scrollbar {
  width: 6px;
}

.todo-list-container::-webkit-scrollbar-track {
  background: transparent;
}

.todo-list-container::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}

.todo-list-container::-webkit-scrollbar-thumb:hover {
  background: #ccc;
}
</style>
