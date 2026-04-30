import { useEffect, useRef } from 'react'
import type { SecurityEvent, DetectionAlert } from '../types'

interface SseHandlers {
  onEvent?: (event: SecurityEvent) => void
  onAlert?: (alert: DetectionAlert) => void
}

/**
 * GET /api/events/stream 에 SSE 구독.
 * 자동 재연결은 EventSource 네이티브 동작 활용.
 */
export function useSse({ onEvent, onAlert }: SseHandlers) {
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const es = new EventSource('/api/events/stream')
    esRef.current = es

    es.addEventListener('event', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onEvent?.(payload.data as SecurityEvent)
      } catch {
        // ignore malformed
      }
    })

    es.addEventListener('alert', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onAlert?.(payload.data as DetectionAlert)
      } catch {
        // ignore malformed
      }
    })

    return () => {
      es.close()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
}
