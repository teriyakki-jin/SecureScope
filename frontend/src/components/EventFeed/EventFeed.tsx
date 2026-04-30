import { useState, useCallback } from 'react'
import { useSse } from '../../hooks/useSse'
import type { SecurityEvent, DetectionAlert } from '../../types'

const EVENT_TYPE_COLOR: Record<string, string> = {
  LOGIN_SUCCESS: 'text-green-400',
  LOGIN_FAIL: 'text-yellow-400',
  UNAUTHORIZED_ACCESS: 'text-red-400',
  PORT_SCAN: 'text-orange-400',
}

const MAX_ITEMS = 50

export function EventFeed() {
  const [events, setEvents] = useState<SecurityEvent[]>([])
  const [alerts, setAlerts] = useState<DetectionAlert[]>([])

  const onEvent = useCallback((e: SecurityEvent) => {
    setEvents((prev) => [e, ...prev].slice(0, MAX_ITEMS))
  }, [])

  const onAlert = useCallback((a: DetectionAlert) => {
    setAlerts((prev) => [a, ...prev].slice(0, MAX_ITEMS))
  }, [])

  useSse({ onEvent, onAlert })

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {/* 이벤트 피드 */}
      <div className="bg-slate-800 rounded-xl p-4 h-96 overflow-y-auto">
        <h2 className="text-white font-semibold mb-3 flex items-center gap-2">
          <span className="inline-block w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          Live Events
        </h2>
        {events.length === 0 && (
          <p className="text-slate-500 text-sm">이벤트를 기다리는 중...</p>
        )}
        <ul className="space-y-1">
          {events.map((ev) => (
            <li key={ev.id} className="text-xs font-mono text-slate-300 border-b border-slate-700 pb-1">
              <span className={`font-bold ${EVENT_TYPE_COLOR[ev.eventType] ?? 'text-white'}`}>
                {ev.eventType}
              </span>
              {' | '}
              <span className="text-sky-300">{ev.sourceIp}</span>
              {ev.targetPort && <span className="text-slate-400"> :{ev.targetPort}</span>}
              {' | '}
              <span className="text-slate-500">{new Date(ev.occurredAt).toLocaleTimeString('ko-KR')}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* 알림 피드 */}
      <div className="bg-slate-800 rounded-xl p-4 h-96 overflow-y-auto">
        <h2 className="text-white font-semibold mb-3 flex items-center gap-2">
          <span className="inline-block w-2 h-2 rounded-full bg-red-400 animate-pulse" />
          Live Alerts
        </h2>
        {alerts.length === 0 && (
          <p className="text-slate-500 text-sm">탐지 알림을 기다리는 중...</p>
        )}
        <ul className="space-y-2">
          {alerts.map((al) => (
            <li key={al.id} className="bg-red-900/30 border border-red-700/50 rounded p-2">
              <div className="flex items-center justify-between">
                <span className="text-red-300 font-bold text-xs">{al.alertType}</span>
                <SeverityBadge severity={al.severity} />
              </div>
              <p className="text-slate-300 text-xs mt-1">{al.detail}</p>
              <p className="text-slate-500 text-xs">{al.sourceIp}</p>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const color =
    severity === 'HIGH' ? 'bg-red-600' :
    severity === 'MED'  ? 'bg-yellow-600' : 'bg-slate-600'
  return (
    <span className={`${color} text-white text-xs px-2 py-0.5 rounded-full font-semibold`}>
      {severity}
    </span>
  )
}
