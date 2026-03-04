import axios from './axios'

export interface RouteRequest {
  origin: string
  destination: string
  mode: 'walking' | 'driving' | 'transit'
}

export interface RouteResponse {
  result: string
}

/**
 * 规划路线
 */
export const planRoute = (
  data: RouteRequest
): Promise<{ code: number; data: RouteResponse; message: string }> => {
  return axios.post('/route/plan', data)
}
