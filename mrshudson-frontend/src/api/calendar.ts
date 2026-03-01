import axios from './axios'

export interface CreateEventRequest {
  title: string
  description?: string
  startTime: string
  endTime: string
  location?: string
  category?: 'WORK' | 'PERSONAL' | 'FAMILY'
  reminderMinutes?: number
}

export interface UpdateEventRequest {
  title?: string
  description?: string
  startTime?: string
  endTime?: string
  location?: string
  category?: 'WORK' | 'PERSONAL' | 'FAMILY'
  reminderMinutes?: number
}

export interface EventResponse {
  id: number
  userId: number
  title: string
  description: string
  startTime: string
  endTime: string
  location: string
  category: 'WORK' | 'PERSONAL' | 'FAMILY'
  reminderMinutes: number
  isRecurring: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 获取事件列表
 */
export const getEvents = (
  startDate?: string,
  endDate?: string
): Promise<{ code: number; data: EventResponse[]; message: string }> => {
  return axios.get('/calendar/events', {
    params: { startDate, endDate }
  })
}

/**
 * 获取即将开始的事件
 */
export const getUpcomingEvents = (
  limit: number = 5
): Promise<{ code: number; data: EventResponse[]; message: string }> => {
  return axios.get('/calendar/events/upcoming', { params: { limit } })
}

/**
 * 获取单个事件详情
 */
export const getEventById = (
  id: number
): Promise<{ code: number; data: EventResponse; message: string }> => {
  return axios.get(`/calendar/events/${id}`)
}

/**
 * 创建事件
 */
export const createEvent = (
  data: CreateEventRequest
): Promise<{ code: number; data: EventResponse; message: string }> => {
  return axios.post('/calendar/events', data)
}

/**
 * 更新事件
 */
export const updateEvent = (
  id: number,
  data: UpdateEventRequest
): Promise<{ code: number; data: EventResponse; message: string }> => {
  return axios.put(`/calendar/events/${id}`, data)
}

/**
 * 删除事件
 */
export const deleteEvent = (
  id: number
): Promise<{ code: number; data: null; message: string }> => {
  return axios.delete(`/calendar/events/${id}`)
}
