## [2026-04-30 10:22:31] `D:\develop\bangsan\frontend\src\components\EventFeed\EventFeed.tsx`

제시해주신 `EventFeed.tsx` 코드에 대한 리뷰 결과입니다.

### 1. 코드 품질 및 개선점

*   **상수 관리 및 타입 안정성:**
    *   `EVENT_TYPE_COLOR`의 키가 `string`으로 정의되어 있습니다. `../../types`에 정의된 `EventType`을 사용하여 `Record<EventType, string>`으로 타입을 지정하면 오타 방지 및 타입 추론에 유리합니다.
    *   `SeverityBadge`의 색상 선택 로직도 `EVENT_TYPE_COLOR`처럼 별도의 매핑 객체(Record)로 분리하면 가독성이 좋아집니다.

*   **컴포넌트 분리:**
    *   `EventFeed` 컴포넌트 내부에 이벤트 리스트와 알림 리스트 렌더링 로직이 섞여 있습니다. `FeedItem`이나 `EventList`, `AlertList`와 같이 하위 컴포넌트로 분리하면 코드가 더 깔끔해집니다.

*   **날짜 포맷팅 최적화:**
    *   `map` 함수 내부에서 매번 `new Date(ev.occurredAt).toLocaleTimeString('ko-KR')`을 호출하고 있습니다. 아이템이 많아질 경우 성능에 영향을 줄 수 있으므로, 메모이제이션을 고려하거나 포맷팅 유틸리티 함수를 사용하는 것이 좋습니다.

### 2. 기능적 개선 제안

*   **스크롤 동작:**
    *   현재 최신 데이터가 위로 추가되는 방식(`[e, ...prev]`)입니다. 데이터가 빈번하게 발생할 경우 리스트가 계속 밀려나서 사용자가 특정 항목을 읽기 어려울 수 있습니다.
    *   사용자가 스크롤을 올렸을 때는 자동 업데이트로 인해 위치를 잃지 않도록 하는 처리가 필요할 수 있습니다.

*   **접근성 및 접근성:**
    *   `animate-pulse`를 사용하는 상태 표시등에 `aria-label` 등을 추가하여 스크린 리더 사용자가 현재 상태(Live)를 인지할 수 있도록 개선할 수 있습니다.

### 3. 버그 위험 요소

*   **중복 ID 처리:**
    *   SSE 특성상 간혹 중복된 메시지가 수신될 수 있습니다. `setEvents` 시 `id` 중복 체크 로직을 추가하면 React `key` 중복 경고를 방지할 수 있습니다.

---

**요약된 개선 코드 예시 (일부):**

```typescript
// 매핑 객체 활용
const SEVERITY_COLORS: Record<string, string> = {
  HIGH: 'bg-red-600',
  MED: 'bg-yellow-600',
  LOW: 'bg-slate-600',
};

function SeverityBadge({ severity }: { severity: string }) {
  const color = SEVERITY_COLORS[severity] ?? 'bg-slate-600';
  return (
    <span className={`${color} text-white text-xs px-2 py-0.5 rounded-full font-semibold`}>
      {severity}
    </span>
  )
}
```

---

