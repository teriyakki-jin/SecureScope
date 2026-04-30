import client from './client'
import type { ApiResponse, DetectionAlert, Severity, AlertType } from '../types'

export async function fetchAlerts(params?: {
  severity?: Severity
  alertType?: AlertType
  sourceIp?: string
  page?: number
  size?: number
}): Promise<ApiResponse<DetectionAlert[]>> {
  const { data } = await client.get<ApiResponse<DetectionAlert[]>>('/alerts', { params })
  return data
}
