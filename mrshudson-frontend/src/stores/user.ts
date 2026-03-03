import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

export interface User {
  id: number
  username: string
}

export const useUserStore = defineStore('user', () => {
  // State
  const user = ref<User | null>(null)
  const isLoggedIn = ref(false)
  const loading = ref(false)
  const accessToken = ref<string | null>(localStorage.getItem('access_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refresh_token'))

  // Getters
  const hasToken = computed(() => !!accessToken.value)

  // 设置 Token
  const setTokens = (newAccessToken: string, newRefreshToken: string) => {
    accessToken.value = newAccessToken
    refreshToken.value = newRefreshToken
    localStorage.setItem('access_token', newAccessToken)
    localStorage.setItem('refresh_token', newRefreshToken)
  }

  // 清除 Token
  const clearTokens = () => {
    accessToken.value = null
    refreshToken.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  // Actions
  const login = async (username: string, password: string): Promise<{ success: boolean; message?: string }> => {
    loading.value = true
    try {
      const res = await authApi.login({ username, password })
      if (res.code === 200) {
        const { id, username: userName, accessToken: newAccessToken, refreshToken: newRefreshToken } = res.data
        user.value = { id, username: userName }
        isLoggedIn.value = true
        setTokens(newAccessToken, newRefreshToken)
        return { success: true }
      }
      // 登录失败，返回后端错误信息
      return { success: false, message: res.message || '用户名或密码错误' }
    } catch (error: any) {
      console.error('登录失败:', error)
      // 网络错误或服务器错误
      if (error.response?.status === 403) {
        return { success: false, message: '访问被拒绝，请检查服务器配置' }
      } else if (error.response?.status >= 500) {
        return { success: false, message: '服务器错误，请稍后重试' }
      } else if (!error.response) {
        return { success: false, message: '网络错误，请检查后端服务是否启动' }
      }
      return { success: false, message: '登录失败，请稍后重试' }
    } finally {
      loading.value = false
    }
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } finally {
      user.value = null
      isLoggedIn.value = false
      clearTokens()
    }
  }

  const fetchCurrentUser = async () => {
    try {
      // 如果没有 Token，直接返回 false
      if (!hasToken.value) {
        return false
      }

      const res = await authApi.getCurrentUser()
      if (res.code === 200) {
        user.value = res.data
        isLoggedIn.value = true
        return true
      }
      return false
    } catch {
      return false
    }
  }

  return {
    user,
    isLoggedIn,
    loading,
    accessToken,
    refreshToken,
    hasToken,
    login,
    logout,
    fetchCurrentUser,
    setTokens,
    clearTokens
  }
})
