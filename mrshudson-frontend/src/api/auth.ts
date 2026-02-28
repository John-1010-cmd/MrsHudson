import axios from './axios'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  id: number
  username: string
}

/**
 * 登录
 */
export const login = (data: LoginRequest): Promise<{ code: number; data: LoginResponse; message: string }> => {
  return axios.post('/auth/login', data)
}

/**
 * 登出
 */
export const logout = (): Promise<{ code: number; message: string }> => {
  return axios.post('/auth/logout')
}

/**
 * 获取当前用户
 */
export const getCurrentUser = (): Promise<{ code: number; data: LoginResponse; message: string }> => {
  return axios.get('/auth/me')
}
