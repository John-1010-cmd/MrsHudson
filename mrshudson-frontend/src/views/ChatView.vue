<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <aside class="conversation-sidebar">
      <div class="sidebar-header">
        <h2>MrsHudson</h2>
        <el-button type="primary" size="small" @click="createNewConversation">
          <el-icon><Plus /></el-icon>
          新对话
        </el-button>
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
        </nav>
        <div class="user-info">
          <span>{{ userStore.user?.username }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
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
import { ref, onMounted, computed, provide, readonly } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { RouterView } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Calendar,
  List,
  PartlyCloudy,
  Plus,
  Delete
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

// 提供给子组件 - 不使用 readonly，直接提供 ref，让子组件可以响应式监听
provide('currentConversationId', currentConversationId)
provide('conversations', conversations)
provide('switchConversation', switchConversation)
provide('createNewConversation', createNewConversation)

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
  width: 260px;
  background: #1a1a2e;
  color: white;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 18px;
  color: #409eff;
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
  overflow: hidden;
}
</style>
