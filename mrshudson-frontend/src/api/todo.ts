import axios from './axios'

export type Priority = 'HIGH' | 'MEDIUM' | 'LOW'

export interface CreateTodoRequest {
  title: string
  priority?: Priority
  dueDate?: string
}

export interface TodoResponse {
  id: number
  userId: number
  title: string
  priority: Priority
  dueDate: string | null
  completed: boolean
  createdAt: string
  updatedAt: string
}

export interface UpdateTodoRequest {
  title?: string
  priority?: Priority
  dueDate?: string | null
  completed?: boolean
}

/**
 * 获取待办事项列表
 */
export const getTodos = (
  completed?: boolean
): Promise<{ code: number; data: TodoResponse[]; message: string }> => {
  return axios.get('/todos', {
    params: { completed }
  })
}

/**
 * 创建待办事项
 */
export const createTodo = (
  data: CreateTodoRequest
): Promise<{ code: number; data: TodoResponse; message: string }> => {
  return axios.post('/todos', data)
}

/**
 * 更新待办事项
 */
export const updateTodo = (
  id: number,
  data: UpdateTodoRequest
): Promise<{ code: number; data: TodoResponse; message: string }> => {
  return axios.put(`/todos/${id}`, data)
}

/**
 * 标记待办事项完成/取消完成
 */
export const completeTodo = (
  id: number,
  completed: boolean
): Promise<{ code: number; data: TodoResponse; message: string }> => {
  return axios.patch(`/todos/${id}/complete`, { completed })
}

/**
 * 删除待办事项
 */
export const deleteTodo = (
  id: number
): Promise<{ code: number; data: null; message: string }> => {
  return axios.delete(`/todos/${id}`)
}
