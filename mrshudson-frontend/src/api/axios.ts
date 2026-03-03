import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000
  // 注意：使用 JWT 时不需要 withCredentials
})

// 从 localStorage 获取 Token
const getAccessToken = (): string | null => {
  return localStorage.getItem('access_token')
}

const getRefreshToken = (): string | null => {
  return localStorage.getItem('refresh_token')
}

const setTokens = (accessToken: string, refreshToken: string): void => {
  localStorage.setItem('access_token', accessToken)
  localStorage.setItem('refresh_token', refreshToken)
}

const clearTokens = (): void => {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
}

// 是否正在刷新 Token
let isRefreshing = false
// 等待 Token 刷新的请求队列
let refreshSubscribers: ((token: string) => void)[] = []

// 将请求添加到队列
const subscribeTokenRefresh = (callback: (token: string) => void) => {
  refreshSubscribers.push(callback)
}

// 使用新 Token 重试队列中的请求
const onTokenRefreshed = (newToken: string) => {
  refreshSubscribers.forEach((callback) => callback(newToken))
  refreshSubscribers = []
}

// 请求拦截器
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response) => {
    return response.data
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // 如果是 401 错误且不是刷新 Token 的请求
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      // 如果是刷新 Token 接口本身返回 401，直接跳转到登录页
      if (originalRequest.url === '/auth/refresh') {
        clearTokens()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      originalRequest._retry = true

      if (isRefreshing) {
        // 如果正在刷新，将请求加入队列
        return new Promise((resolve) => {
          subscribeTokenRefresh((newToken: string) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(instance(originalRequest))
          })
        })
      }

      isRefreshing = true

      try {
        const refreshToken = getRefreshToken()
        if (!refreshToken) {
          throw new Error('No refresh token')
        }

        // 调用刷新 Token 接口
        const response = await axios.post('/api/auth/refresh', null, {
          headers: {
            'X-Refresh-Token': refreshToken
          }
        })

        if (response.data.code === 200) {
          const { accessToken, refreshToken: newRefreshToken } = response.data.data
          setTokens(accessToken, newRefreshToken)

          // 重试队列中的请求
          onTokenRefreshed(accessToken)

          // 重试原始请求
          originalRequest.headers.Authorization = `Bearer ${accessToken}`
          return instance(originalRequest)
        }
      } catch (refreshError) {
        // 刷新失败，清除 Token 并跳转到登录页
        clearTokens()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

export default instance
