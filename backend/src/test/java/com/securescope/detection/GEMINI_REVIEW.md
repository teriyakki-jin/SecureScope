## [2026-04-30 10:26:46] `D:\develop\bangsan\backend\src\test\java\com\securescope\detection\BruteForceRuleTest.java`

제공해주신 `BruteForceRuleTest.java` 코드를 리뷰한 결과입니다.

### 1. 버그 및 논리적 결함
*   **만료 시간(TTL) 검증 부재:** `BruteForceRule`이 Redis 카운터를 생성할 때 설정된 `windowSeconds` 동안만 유지되도록 `expire`를 호출하는지 확인하는 테스트가 없습니다.
*   **동시성 이슈 고려:** 실제 구현에서 `increment`와 `delete` 사이에 원자성이 보장되지 않을 경우, 알림이 중복 발생할 수 있습니다. (테스트 자체의 문제는 아니나, 구현 설계의 위험 요소임)

### 2. 코드 품질 및 개선점
*   **Redis 키 형식 검증:** `contains("1.2.3.4")`는 다소 모호합니다. Redis 키 생성 규칙(예: `prefix:ip`)이 정확히 일치하는지 `eq()`를 사용하여 검증하는 것이 더 견고합니다.
*   **ValueOperations 제네릭 경고:** `mock(ValueOperations.class)`는 타입 안전성 경고를 발생시킬 수 있습니다. `@Mock` 어노테이션이나 명확한 타입 캐스팅을 권장합니다.
*   **엣지 케이스 테스트 부족:** 
    *   임계값 바로 직전(threshold - 1)에서의 동작.
    *   Redis 연동 중 예외 발생 시의 예외 처리(Alert이 무시되는지 등).
*   **가독성:** `DetectionProperties` 설정 시 매직 넘버(5, 60 등) 보다는 명시적인 변수명을 사용하거나 테스트 목적에 맞는 이름을 부여하면 의도가 더 잘 드러납니다.

### 3. 총평
전반적으로 JUnit 5와 Mockito를 활용하여 핵심 비즈니스 로직(필터링, 임계값 탐지, 리셋)을 잘 테스트하고 있습니다. 다만, **Redis의 TTL 설정 여부**와 **정확한 키 포맷**에 대한 검증을 추가하면 더욱 신뢰도 높은 테스트가 될 것입니다.

---

## [2026-04-30 10:27:15] `D:\develop\bangsan\backend\src\test\java\com\securescope\detection\AfterHoursRuleTest.java`

제시된 `AfterHoursRuleTest.java` 코드를 리뷰한 결과입니다.

### 1. 버그 및 잠재적 위험
*   **경계값 모호성 (18시):** `AllowedHours(9, 18)` 설정에서 18시(`ValueSource(ints = {18})`)를 '허용 시간대 밖'으로 테스트하고 있습니다. 일반적으로 "9시부터 18시"라고 하면 18시 00분 00초가 포함되는지 여부가 개발자/비즈니스 요구사항마다 다를 수 있습니다. 코드상에서 `hour < start || hour >= end` 형태인지 명확한 확인이 필요하며, 테스트에서도 17시 59분과 18시 01분 같은 세밀한 경계값 테스트가 누락되어 있습니다.
*   **날짜 의존성:** `ZonedDateTime.now()`를 사용하므로, 만약 `AfterHoursRule`이 향후 '평일/주말' 구분을 추가하게 된다면 테스트 실행 요일에 따라 결과가 달라지는 **Flaky Test**가 될 위험이 있습니다. 고정된 날짜(예: `2024-01-01`)를 사용하는 것이 안전합니다.

### 2. 코드 품질 및 개선점
*   **타임존 처리의 일관성:** `eventAt`에서 `Asia/Seoul`로 `Instant`를 생성하고 있습니다. 실제 `AfterHoursRule` 클래스 내부에서도 동일하게 `Asia/Seoul` 혹은 설정된 타임존을 기준으로 `Instant`를 해석하고 있는지 확인이 필요합니다. (시스템 기본 타임존을 사용하면 배포 환경에 따라 테스트가 깨질 수 있습니다.)
*   **테스트 케이스 보완:**
    *   **분 단위 테스트:** 9시 정각(허용), 18시 정각(불허/허용 여부 확인), 17시 59분(허용) 등 정각 이외의 케이스를 추가하여 로직의 정밀도를 높여야 합니다.
    *   **필드 검증:** `shouldAlertOutsideAllowedHours`에서 `alertType`과 `severity`만 검증하는데, 생성된 알림의 메시지나 대상 사용자(`user`)가 이벤트와 일치하는지도 검증하는 것이 좋습니다.
*   **가독성:** `PROPS` 상수를 선언할 때 `AllowedHours` 외에 관련 없는 `BruteForce`, `PortScan` 값이 포함되어 있습니다. 테스트 의도를 명확히 하기 위해 `AfterHoursRule`에 필요한 값만 강조되도록 구성하거나 Mocking을 고려할 수 있습니다.

### 3. 요약
전반적으로 JUnit 5의 기능을 잘 활용한 깔끔한 테스트 코드입니다. 다만, **시간 경계값(18시 정각)에 대한 정의**를 명확히 하고, **고정된 기준 시점**을 사용하도록 `eventAt` 메서드를 개선하면 더욱 견고한 테스트가 될 것입니다.

---

