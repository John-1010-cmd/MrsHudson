<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <aside
      class="conversation-sidebar"
      :class="{ collapsed: !sidebarVisible }"
      :style="{ width: sidebarVisible ? sidebarWidth : collapsedWidth }"
    >
      <!-- 侧边栏展开时显示完整内容 -->
      <template v-if="sidebarVisible">
        <div class="sidebar-header">
          <h2>MrsHudson</h2>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="createNewConversation">
              <el-icon><Plus /></el-icon>
              新对话
            </el-button>
            <el-button size="small" @click="toggleSidebar" :title="sidebarVisible ? '隐藏侧边栏' : '显示侧边栏'">
              <el-icon><ArrowLeft v-if="sidebarVisible" /><ArrowRight v-else /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 会话列表 -->
        <div class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :class="['conversation-item', { active: currentConversationId === conv.id }]"
            @click="switchConversation(conv.id)"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span class="conversation-title">{{ conv.title }}</span>
            <el-icon
              class="delete-icon"
              @click.stop="deleteConversation(conv.id)"
            >
              <Delete />
            </el-icon>
          </div>
        </div>

        <div class="sidebar-footer">
          <nav class="sidebar-nav">
            <router-link
              to="/chat"
              :class="['nav-item', { active: currentRoute === '/chat' || currentRoute === '/' }]"
            >
              <el-icon><ChatDotRound /></el-icon>
              <span>对话</span>
            </router-link>
            <router-link
              to="/calendar"
              :class="['nav-item', { active: currentRoute === '/calendar' }]"
            >
              <el-icon><Calendar /></el-icon>
              <span>日历</span>
            </router-link>
            <router-link
              to="/todo"
              :class="['nav-item', { active: currentRoute === '/todo' }]"
            >
              <el-icon><List /></el-icon>
              <span>待办</span>
            </router-link>
            <router-link
              to="/weather"
              :class="['nav-item', { active: currentRoute === '/weather' }]"
            >
              <el-icon><PartlyCloudy /></el-icon>
              <span>天气</span>
            </router-link>
            <router-link
              to="/route"
              :class="['nav-item', { active: currentRoute === '/route' }]"
            >
              <el-icon><MapLocation /></el-icon>
              <span>路线</span>
            </router-link>
            <router-link
              to="/metrics"
              :class="['nav-item', { active: currentRoute === '/metrics' }]"
            >
              <el-icon><DataLine /></el-icon>
              <span>优化统计</span>
            </router-link>
          </nav>
          <div class="user-info">
            <span>{{ userStore.user?.username }}</span>
            <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
          </div>
        </div>
      </template>

      <!-- 折叠时显示的竖条、切换按钮和导航图标 -->
      <div v-if="!sidebarVisible" class="collapsed-container">
        <!-- 切换按钮 -->
        <div class="collapsed-toggle" @click="toggleSidebar">
          <el-icon class="toggle-icon"><ArrowRight /></el-icon>
        </div>
        <!-- 导航图标列表 -->
        <div class="collapsed-nav">
          <el-tooltip content="对话" placement="right" :show-after="300">
            <router-link to="/chat" class="collapsed-nav-item" :class="{ active: currentRoute === '/chat' || currentRoute === '/' }">
              <el-icon><ChatDotRound /></el-icon>
            </router-link>
          </el-tooltip>
          <el-tooltip content="日历" placement="right" :show-after="300">
            <router-link to="/calendar" class="collapsed-nav-item" :class="{ active: currentRoute === '/calendar' }">
              <el-icon><Calendar /></el-icon>
            </router-link>
          </el-tooltip>
          <el-tooltip content="待办" placement="right" :show-after="300">
            <router-link to="/todo" class="collapsed-nav-item" :class="{ active: currentRoute === '/todo' }">
              <el-icon><List /></el-icon>
            </router-link>
          </el-tooltip>
          <el-tooltip content="天气" placement="right" :show-after="300">
            <router-link to="/weather" class="collapsed-nav-item" :class="{ active: currentRoute === '/weather' }">
              <el-icon><PartlyCloudy /></el-icon>
            </router-link>
          </el-tooltip>
          <el-tooltip content="路线" placement="right" :show-after="300">
            <router-link to="/route" class="collapsed-nav-item" :class="{ active: currentRoute === '/route' }">
              <el-icon><MapLocation /></el-icon>
            </router-link>
          </el-tooltip>
          <el-tooltip content="优化统计" placement="right" :show-after="300">
            <router-link to="/metrics" class="collapsed-nav-item" :class="{ active: currentRoute === '/metrics' }">
              <el-icon><DataLine /></el-icon>
            </router-link>
          </el-tooltip>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="chat-main">
      <RouterView :key="route.fullPath" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, provide, readonly, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { RouterView } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Calendar,
  List,
  PartlyCloudy,
  MapLocation,
  DataLine,
  Plus,
  Delete,
  ArrowLeft,
  ArrowRight
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import * as chatApi from '../api/chat'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 当前路由路径
const currentRoute = computed(() => route.path)

// 会话列表
const conversations = ref<chatApi.ConversationDTO[]>([])
const currentConversationId = ref<string | null>(null)

// 侧边栏展开/折叠
const sidebarWidth = '260px'
const collapsedWidth = '50px'
const sidebarVisible = ref(true)

const toggleSidebar = () => {
  sidebarVisible.value = !sidebarVisible.value
}

// 加载会话列表
const loadConversations = async () => {
  try {
    const res = await chatApi.getConversationList(20)
    if (res.code === 200) {
      conversations.value = res.data.conversations
      // 如果没有选中会话，且有会话列表，则选中第一个
      if (!currentConversationId.value && conversations.value.length > 0) {
        currentConversationId.value = conversations.value[0].id
      } else if (conversations.value.length === 0) {
        // 如果没有会话，自动创建一个新会话
        await createNewConversation()
      }
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
    // 加载失败时也尝试创建新会话
    await createNewConversation()
  }
}

// 创建新会话
const createNewConversation = async () => {
  try {
    const res = await chatApi.createConversation('新对话')
    if (res.code === 200) {
      conversations.value.unshift(res.data)
      currentConversationId.value = res.data.id
      ElMessage.success('创建会话成功')
    }
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

// 切换会话
const switchConversation = (id: string) => {
  currentConversationId.value = id

  // 如果当前不在对话页面，导航到对话页面
  if (!route.path.startsWith('/chat') && route.path !== '/') {
    router.push('/chat')
  }
}

// 删除会话
const deleteConversation = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await chatApi.deleteConversation(id)
    if (res.code === 200) {
      conversations.value = conversations.value.filter(c => c.id !== id)
      if (currentConversationId.value === id) {
        currentConversationId.value = conversations.value.length > 0
          ? conversations.value[0].id
          : null
      }
      ElMessage.success('删除成功')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleLogout = async () => {
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

// 刷新会话列表
const refreshConversations = async () => {
  try {
    const res = await chatApi.getConversationList(20)
    if (res.code === 200) {
      conversations.value = res.data.conversations
    }
  } catch (error) {
    console.error('刷新会话列表失败:', error)
  }
}

// 提供给子组件 - 不使用 readonly，直接提供 ref，让子组件可以响应式监听
provide('currentConversationId', currentConversationId)
provide('conversations', conversations)
provide('switchConversation', switchConversation)
provide('createNewConversation', createNewConversation)
provide('refreshConversations', refreshConversations)

// 监听路由变化，当离开对话页面时清除当前会话选中状态
watch(
  () => route.path,
  (newPath) => {
    if (!newPath.startsWith('/chat') && newPath !== '/') {
      currentConversationId.value = null
    }
  }
)

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
}

.conversation-sidebar {
  --sidebar-width: 260px;
  --collapsed-width: 50px;
  width: var(--sidebar-width);
  background: #1a1a2e;
  color: white;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  overflow: hidden;
}

.conversation-sidebar.collapsed {
  width: var(--collapsed-width);
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 18px;
  color: #409eff;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.collapsed-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.collapsed-toggle {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px 0;
  cursor: pointer;
  transition: background 0.3s;
}

.collapsed-toggle:hover {
  background: rgba(255, 255, 255, 0.05);
}

.toggle-icon {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  transition: color 0.3s;
}

.collapsed-toggle:hover .toggle-icon {
  color: #409eff;
}

/* 折叠时的导航图标列表 */
.collapsed-nav {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  width: 100%;
}

.collapsed-nav-item {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: none;
  transition: all 0.3s;
}

.collapsed-nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
}

.collapsed-nav-item.active {
  background: rgba(64, 158, 255, 0.2);
  color: #409eff;
}

.collapsed-nav-item .el-icon {
  font-size: 18px;
}

.model-selector {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.model-selector :deep(.el-select) {
  width: 100%;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  color: rgba(255, 255, 255, 0.8);
}

.conversation-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

.conversation-item.active {
  background: rgba(64, 158, 255, 0.2);
  color: #409eff;
}

.conversation-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.delete-icon {
  opacity: 0;
  transition: opacity 0.3s;
  padding: 4px;
  border-radius: 4px;
}

.conversation-item:hover .delete-icon {
  opacity: 1;
}

.delete-icon:hover {
  background: rgba(255, 0, 0, 0.2);
  color: #f56c6c;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.sidebar-nav {
  margin-bottom: 16px;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  transition: all 0.3s;
  gap: 12px;
  border-radius: 8px;
  margin-bottom: 4px;
}

.nav-item:hover,
.nav-item.active {
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.nav-item .el-icon {
  font-size: 18px;
}

.user-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.user-info span {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
}

.chat-main {
  flex: 1;
  background: #f5f7fa;
  overflow: auto;
}
</style>
