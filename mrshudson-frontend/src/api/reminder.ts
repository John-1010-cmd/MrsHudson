import axios from './axios'

export interface Reminder {
  id: number
  type: 'EVENT' | 'TODO' | 'WEATHER' | 'SYSTEM'
  title: string
  content: string
  remindAt: string
  isRead: boolean
  refId: number
  createdAt: string
}

/**
 * 获取所有提醒
 */
export const getReminders = (): Promise<{ code: number; data: Reminder[]; message: string }> => {
  return axios.get('/reminders')
}

/**
 * 获取未读提醒
 */
export const getUnreadReminders = (): Promise<{ code: number; data: Reminder[]; message: string }> => {
  return axios.get('/reminders/unread')
}

/**
 * 获取未读提醒数量
 */
export const getUnreadCount = (): Promise<{ code: number; data: { count: number }; message: string }> => {
  return axios.get('/reminders/unread-count')
}

/**
 * 标记提醒为已读
 */
export const markAsRead = (id: number): Promise<{ code: number; data: null; message: string }> => {
  return axios.put(`/reminders/${id}/read`)
}

/**
 * 批量标记所有提醒为已读
 */
export const markAllAsRead = (): Promise<{ code: number; data: null; message: string }> => {
  return axios.put('/reminders/read-all')
}

/**
 * 延迟提醒
 * @param id 提醒ID
 * @param minutes 延迟分钟数
 */
export const snoozeReminder = (
  id: number,
  minutes: number
): Promise<{ code: number; data: Reminder; message: string }> => {
  return axios.put(`/reminders/${id}/snooze`, null, { params: { minutes } })
}
