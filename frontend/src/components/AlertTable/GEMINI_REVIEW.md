## [2026-04-30 10:23:13] `D:\develop\bangsan\frontend\src\components\AlertTable\AlertTable.tsx`

`AlertTable.tsx` 코드에 대한 리뷰 결과입니다. 버그 방지, 유지보수성, UX 관점에서 정리했습니다.

### 1. 버그 및 잠재적 문제 (Bugs & Stability)
*   **경합 조건 (Race Condition):** `useEffect` 내에서 API를 호출할 때, 이전 요청을 취소하거나 무시하는 로직이 없습니다. 네트워크 지연으로 인해 이전 페이지 데이터가 현재 페이지에 덮어씌워질 위험이 있습니다. (`AbortController` 또는 `ignore` 플래그 사용 권장)
*   **타입 안정성 부족:** `SeverityBadge`의 `severity` 프롭이 `string`으로 선언되어 있습니다. 정의된 `Severity` 타입을 사용하여 오타를 방지해야 합니다.

### 2. 코드 품질 (Code Quality)
*   **매직 넘버 (Magic Number):** 페이지 크기(`10`)가 여러 곳에 하드코딩되어 있습니다. `const PAGE_SIZE = 10` 상수로 관리하여 `fetchAlerts`와 `totalPages` 계산 로직의 일관성을 유지하세요.
*   **부실한 에러 핸들링:** `catch(console.error)`는 개발자용입니다. API 실패 시 사용자에게 알림(Toast)을 주거나 에러 UI를 보여주는 처리가 필요합니다.
*   **상태 업데이트 일관성:** `page` 변경 시 `setLoading(true)`는 하지만, 에러 발생 시 `setAlerts([])` 등의 초기화 처리가 없어 이전 데이터가 그대로 남을 수 있습니다.

### 3. 개선 제안 (Improvements)
*   **날짜 포맷팅:** 테이블 렌더링 시마다 `new Date()`를 생성하는 것은 비용이 듭니다. 데이터가 들어오는 시점에 포맷팅하거나, 날짜 처리 라이브러리(date-fns 등)를 사용하여 유틸리티 함수로 분리하세요.
*   **UI/UX:**
    *   로딩 중일 때 테이블 본문이 사라지고 "로딩 중..." 텍스트만 보이는데, 테이블 레이아웃을 유지하는 **스켈레톤 UI**를 적용하면 레이아웃 시프트를 방지할 수 있습니다.
    *   필터(`severity`, `alertType`)를 URL Query String과 동기화하면 페이지 새로고침 시에도 필터 상태가 유지되어 편리합니다.
*   **컴포넌트 분리:** `AlertTable`이 비대해질 수 있으므로, `FilterBar`, `TableBody`, `Pagination` 등으로 분리하면 가독성이 좋아집니다.

---

**추천 코드 조각 (Race Condition 해결):**
```typescript
useEffect(() => {
  let ignore = false;
  setLoading(true);
  
  fetchAlerts(...)
    .then(res => {
      if (!ignore) {
        setAlerts(res.data);
        setTotal(res.meta?.total ?? 0);
      }
    })
    .finally(() => {
      if (!ignore) setLoading(false);
    });

  return () => { ignore = true; };
}, [severity, alertType, page]);
```

---

