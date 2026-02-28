<template>
  <div class="chat-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <h2>MrsHudson</h2>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/chat" class="nav-item active">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话</span>
        </router-link>
        <router-link to="/calendar" class="nav-item">
          <el-icon><Calendar /></el-icon>
          <span>日历</span>
        </router-link>
        <router-link to="/todo" class="nav-item">
          <el-icon><List /></el-icon>
          <span>待办</span>
        </router-link>
        <router-link to="/weather" class="nav-item">
          <el-icon><PartlyCloudy /></el-icon>
          <span>天气</span>
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <div class="user-info">
          <span>{{ userStore.user?.username }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="chat-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Calendar, List, PartlyCloudy } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const handleLogout = async () => {
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
}

.sidebar {
  width: 240px;
  background: #1a1a2e;
  color: white;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-header h2 {
  margin: 0;
  font-size: 20px;
  color: #409eff;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  transition: all 0.3s;
  gap: 12px;
}

.nav-item:hover,
.nav-item.active {
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.nav-item .el-icon {
  font-size: 20px;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info span {
  color: rgba(255, 255, 255, 0.8);
}

.chat-main {
  flex: 1;
  background: #f5f7fa;
  overflow: hidden;
}
</style>
