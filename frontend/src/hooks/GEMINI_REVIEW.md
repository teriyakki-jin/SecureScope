## [2026-04-30 10:21:32] `D:\develop\bangsan\frontend\src\hooks\useSse.ts`

제시하신 `useSse` 커스텀 훅에 대한 리뷰 결과입니다.

### 1. 주요 버그 및 위험 요소
*   **Stale Closure (함수 클로저 캡처 문제):** `useEffect`의 의존성 배열이 빈 배열(`[]`)입니다. 만약 이 훅을 사용하는 컴포넌트에서 `onEvent`나 `onAlert`를 `useCallback` 없이 정의하여 넘기면, SSE 리스너는 항상 **첫 렌더링 때의 함수**만 참조하게 됩니다. 이로 인해 최신 state에 접근하지 못하는 버그가 발생할 가능성이 높습니다.
*   **연결 오류 처리 부재:** `es.onerror` 핸들러가 없어 네트워크 끊김, 서버 500 에러 등 연결 문제 발생 시 상태를 알 수 없으며 디버깅이 어렵습니다.

### 2. 코드 품질 및 개선점
*   **ESLint 규칙 억제:** `react-hooks/exhaustive-deps`를 비활성화하는 대신, `useRef`를 사용하여 핸들러의 최신 상태를 유지하거나 의존성 배열을 올바르게 관리하는 것이 권장됩니다.
*   **타입 안전성:** `payload.data as SecurityEvent`와 같은 강제 타입 캐스팅은 런타임 에러의 위험이 있습니다. 데이터 구조가 확실한지 검증하는 과정이 있으면 더 좋습니다.
*   **확장성:** SSE 주소(`/api/events/stream`)가 하드코딩되어 있습니다. 매개변수로 분리하여 재사용성을 높일 수 있습니다.

### 3. 개선 제안 코드
핸들러가 바뀌어도 SSE 연결을 재시작하지 않으면서 최신 핸들러를 실행하도록 `useRef`를 활용한 구조입니다.

```typescript
import { useEffect, useRef } from 'react'
import type { SecurityEvent, DetectionAlert } from '../types'

interface SseHandlers {
  onEvent?: (event: SecurityEvent) => void
  onAlert?: (alert: DetectionAlert) => void
}

export function useSse({ onEvent, onAlert }: SseHandlers) {
  // 최신 핸들러를 저장할 Ref (Stale closure 방지)
  const handlersRef = useRef(onEvent, onAlert)

  useEffect(() => {
    handlersRef.current = { onEvent, onAlert }
  }, [onEvent, onAlert])

  useEffect(() => {
    const es = new EventSource('/api/events/stream')

    es.addEventListener('event', (e) => {
      try {
        const payload = JSON.parse(e.data)
        handlersRef.current.onEvent?.(payload.data)
      } catch (err) {
        console.error('SSE(event) parsing error:', err)
      }
    })

    es.addEventListener('alert', (e) => {
      try {
        const payload = JSON.parse(e.data)
        handlersRef.current.onAlert?.(payload.data)
      } catch (err) {
        console.error('SSE(alert) parsing error:', err)
      }
    })

    es.onerror = (err) => {
      console.error('SSE connection error:', err)
      // 필요 시 재연결 로직이나 상태 업데이트 추가
    }

    return () => {
      es.close()
    }
  }, []) // SSE 연결은 마운트 시 한 번만 수행
}
```

**요약:** `useRef`를 통해 핸들러를 관리함으로써 **불필요한 재연결을 방지**하면서도 **최신 함수를 실행**하도록 개선하는 것이 핵심입니다.

---

