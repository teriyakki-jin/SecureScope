import client from './client'
import type { ApiResponse, SecurityEvent, IpStat } from '../types'

export async function fetchEvents(page = 0, size = 20): Promise<ApiResponse<SecurityEvent[]>> {
  const { data } = await client.get<ApiResponse<SecurityEvent[]>>('/events', {
    params: { page, size },
  })
  return data
}

export async function fetchIpStats(): Promise<IpStat[]> {
  const { data } = await client.get<ApiResponse<Record<string, number>>>('/stats/ip')
  return Object.entries(data.data).map(([ip, count]) => ({ ip, count }))
}
