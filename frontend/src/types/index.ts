export type EventType =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAIL'
  | 'UNAUTHORIZED_ACCESS'
  | 'PORT_SCAN'

export type AlertType =
  | 'ALERT_BRUTE_FORCE'
  | 'ALERT_UNAUTHORIZED_MAC'
  | 'ALERT_PORT_SCAN'
  | 'ALERT_AFTER_HOURS'

export type Severity = 'LOW' | 'MED' | 'HIGH'

export interface SecurityEvent {
  id: number
  eventType: EventType
  sourceIp: string
  macAddress: string | null
  targetPort: number | null
  userId: string | null
  occurredAt: string
}

export interface DetectionAlert {
  id: number
  alertType: AlertType
  severity: Severity
  sourceIp: string
  detail: string
  triggerEventId: number | null
  detectedAt: string
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  error: string | null
  meta: { total: number; page: number; limit: number } | null
}

export interface IpStat {
  ip: string
  count: number
}
