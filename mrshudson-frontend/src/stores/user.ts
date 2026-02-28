import { ref } from 'vue'
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

  // Actions
  const login = async (username: string, password: string) => {
    loading.value = true
    try {
      const res = await authApi.login({ username, password })
      if (res.code === 200) {
        user.value = res.data
        isLoggedIn.value = true
        return true
      }
      return false
    } catch (error) {
      console.error('登录失败:', error)
      return false
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
    }
  }

  const fetchCurrentUser = async () => {
    try {
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
    login,
    logout,
    fetchCurrentUser
  }
})
