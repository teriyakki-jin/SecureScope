## [2026-04-30 10:04:29] `D:\develop\bangsan\backend\src\main\java\com\securescope\detection\rule\BruteForceRule.java`

`BruteForceRule.java` 코드에 대한 리뷰 결과입니다. 버그, 코드 품질, 개선점을 중심으로 정리했습니다.

### 1. 주요 버그 및 기술적 결함
*   **Race Condition (원자성 부족):** `increment`와 `expire`가 별도의 명령으로 실행됩니다. 만약 `increment` 성공 후 `expire` 실행 전 서버가 다운되거나 오류가 발생하면, 해당 키는 TTL 없이 Redis에 영구히 남게 됩니다. 
    *   **개선:** Lua 스크립트를 사용하여 `INCR`과 `EXPIRE`를 원자적으로 실행하거나, `setIfAbsent`와 함께 초기값을 설정하는 방식을 권장합니다.
*   **고정 윈도우(Fixed Window) 방식의 한계:** 주석에는 '슬라이딩 윈도우'라고 되어 있으나, 실제 구현은 첫 번째 실패 시점부터 고정된 시간(window) 동안 카운트하는 방식입니다. 윈도우 종료 직전과 직후에 공격이 집중될 경우 탐지하지 못하는 '경계 문제'가 발생할 수 있습니다.
    *   **개선:** 정교한 탐지가 필요하다면 Redis의 `ZSET`을 이용한 실제 슬라이딩 윈도우 구현을 고려하십시오.

### 2. 코드 품질 및 설계
*   **탐지 후 즉시 삭제의 부작용:** `threshold`에 도달하자마자 `redis.delete(key)`를 수행하므로, 임계치 직후의 추가 공격에 대한 연속적인 모니터링이나 가중치 부여가 어렵습니다.
*   **Redis 키 설계:** `RedisKeyPrefix.BRUTE_FORCE`와 `event.getSourceIp()` 사이에 구분자(예: `:`)가 명시적으로 포함되어 있는지 확인이 필요합니다. (예: `brute:127.0.0.1`)
*   **Hardcoded Severity:** `Severity.HIGH`가 하드코딩되어 있습니다. `DetectionProperties`에서 설정 가능하도록 분리하는 것이 유연성 측면에서 좋습니다.

### 3. 개선 제안 (요약)
1.  **원자성 확보:** `increment` 대신 Lua 스크립트를 사용하여 TTL 설정을 보장하십시오.
2.  **로그 기록:** 탐지 시점뿐만 아니라, 임계치에 도달하기 전의 비정상적인 흐름에 대해서도 `DEBUG` 레벨의 로그를 남기면 운영 시 추적이 용이합니다.
3.  **방어적 코드:** `props.bruteForce()`가 `null`을 반환할 가능성에 대비하거나, 애플리케이션 시작 시점에 해당 설정값이 유효한지 검증(Validation)하는 로직을 추가하십시오.

---

**한 줄 평:** 
"Redis를 활용한 기본적인 차단 로직은 깔끔하나, 분산 환경에서의 예외 상황(TTL 누락) 방지와 탐지 정확도 향상을 위한 원자적 연산 도입이 필요합니다."

---

## [2026-04-30 10:04:55] `D:\develop\bangsan\backend\src\main\java\com\securescope\detection\rule\UnauthorizedMacRule.java`

`UnauthorizedMacRule.java`에 대한 코드 리뷰 결과입니다.

### 1. 주요 개선 사항 (성능 및 아키텍처)
*   **성능 이슈 (Caching 필요):** 모든 `SecurityEvent`마다 DB를 조회(SELECT COUNT)하는 방식은 이벤트 발생량이 많을 경우 시스템 성능에 병목이 될 수 있습니다. MAC 화이트리스트는 자주 변하지 않으므로 **로컬 캐시(Caffeine)나 Redis**를 도입하여 DB 부하를 줄이는 것을 권장합니다.
*   **계층 분리 (Repository 패턴):** `JdbcTemplate`을 Rule 클래스에서 직접 사용하는 것보다 `MacWhitelistRepository`를 만들어 데이터 접근 로직을 분리하는 것이 좋습니다. 이는 테스트 용이성과 유지보수성을 높여줍니다.

### 2. 코드 품질 및 안정성
*   **인덱스 확인:** `mac_whitelist` 테이블의 `mac_address` 컬럼에 **인덱스**가 반드시 생성되어 있어야 합니다. 인덱스가 없다면 이벤트 처리 속도가 기하급수적으로 느려질 수 있습니다.
*   **MAC 주소 정규화:** 입력받는 `mac` 주소의 대소문자 구분이 DB와 다를 수 있습니다. `mac.toUpperCase()`와 같이 정규화하여 비교하거나, DB 조회 시 `LOWER()` 함수 등을 사용하는 것이 안전합니다.
*   **유효성 검증:** `isBlank()` 체크 외에 MAC 주소 형식(예: `00:00:00...`)에 대한 유효성 검증 로직을 추가하면 잘못된 데이터로 인한 오탐을 방지할 수 있습니다.

### 3. 기타 제언
*   **Hardcoded SQL:** SQL 쿼리가 문자열로 포함되어 있습니다. Repository로 이동시키거나 상수로 관리하여 가독성을 높이세요.
*   **Severity 동적 할당:** 현재 `HIGH`로 고정되어 있는데, 자산의 중요도에 따라 `Severity`를 동적으로 결정할 수 있다면 더 유연한 탐지 시스템이 될 것입니다.

**요약:** 로직은 명확하나, 실시간 보안 이벤트 처리를 위해 **캐싱 도입**과 **Repository 패턴 적용**이 가장 시급한 개선 과제입니다.

---

## [2026-04-30 10:05:21] `D:\develop\bangsan\backend\src\main\java\com\securescope\detection\rule\PortScanRule.java`

`PortScanRule.java` 코드에 대한 리뷰 결과입니다.

### 1. 버그 및 잠재적 문제
*   **원자성(Atomicity) 결여**: `SADD`를 실행한 후 `EXPIRE`를 별도로 호출하고 있습니다. 만약 포트 추가 직후(EXPIRE 실행 전) 애플리케이션이 종료되거나 장애가 발생하면, 해당 Redis 키는 만료 시간 없이 영구적으로 남게 되어 메모리 누수를 유발할 수 있습니다.
*   **TTL 설정 조건의 불확실성**: `redis.getExpire(key) < 0` 조건은 키가 존재하지만 TTL이 없는 경우를 체크합니다. 하지만 첫 번째 `SADD` 시점에 네트워크 이슈 등으로 `EXPIRE` 설정에 실패하면, 이후 동일 IP의 다른 포트 접근 시에도 TTL이 설정되지 않을 위험이 있습니다.

### 2. 코드 품질 및 개선점
*   **Lua 스크립트 권장**: Redis의 `SADD`, `SCARD`, `EXPIRE` 로직을 하나의 Lua 스크립트로 묶어 실행하는 것이 좋습니다. 이를 통해 **원자성을 보장**하고 Redis와의 **네트워크 왕복(Round-trip) 횟수를 줄여** 성능을 향상시킬 수 있습니다.
*   **Key 구분자 확인**: `RedisKeyPrefix.PORT_SCAN` 문자열 뒤에 `:`와 같은 구분자가 포함되어 있는지 확인이 필요합니다. 구분자가 없다면 `port_scan127.0.0.1`과 같이 가독성이 떨어지는 키가 생성될 수 있습니다.
*   **알림 과부하(Alert Fatigue) 방지**: 임계치 도달 시 `redis.delete(key)`를 수행하여 초기화하는 로직은 깔끔합니다. 다만, 공격자가 지속적으로 스캔을 시도할 경우 짧은 시간 내에 너무 많은 알림이 발생할 수 있으므로, 동일 IP에 대한 알림 억제(Deduplication) 로직을 추가하는 것을 고려해 보세요.
*   **설정값 유효성**: `props.portScan().threshold()`가 0이나 음수일 경우에 대한 방어 로직이 있으면 더 견고한 코드가 됩니다.

### 3. 개선 제안 코드 (Lua 스크립트 개념)
```java
// 예시: Redis 실행 부분
String script = 
    "redis.call('SADD', KEYS[1], ARGV[1]); " +
    "if redis.call('SCARD', KEYS[1]) == 1 then " +
    "  redis.call('EXPIRE', KEYS[1], ARGV[2]); " +
    "end; " +
    "return redis.call('SCARD', KEYS[1]);";

Long currentCount = redis.execute(new DefaultRedisScript<>(script, Long.class), 
    Collections.singletonList(key), 
    String.valueOf(event.getTargetPort()), 
    String.valueOf(window));
```
이러한 방식으로 개선하면 첫 번째 원소 삽입 시에만 정확히 `EXPIRE`를 설정하고 원자성을 유지할 수 있습니다.

---

## [2026-04-30 10:05:55] `D:\develop\bangsan\backend\src\main\java\com\securescope\detection\rule\AfterHoursRule.java`

`AfterHoursRule.java` 코드에 대한 리뷰 결과입니다.

### 1. 주요 버그 및 논리 오류
*   **자정(Overnight) 시간대 처리 불가**: 현재 논리(`hour >= start && hour < end`)는 `start < end`인 경우(예: 09~18시)만 정상 작동합니다. 만약 허용 시간이 밤 10시부터 새벽 6시(`start=22, end=6`)라면 해당 조건식은 항상 `false`가 되어 모든 시간대를 침입으로 간주하거나 탐지하지 못하게 됩니다.
    *   **개선**: `start < end`일 때와 `start > end`일 때의 로직을 분리하여 처리해야 합니다.
*   **타임존 하드코딩**: `ZoneId.of("Asia/Seoul")`와 메시지의 `KST`가 하드코딩되어 있습니다. 글로벌 서비스나 서버 환경 변화에 취약하므로, `DetectionProperties` 등을 통해 설정값으로 관리하거나 시스템 기본값을 따르는 것이 좋습니다.

### 2. 코드 품질 및 개선점
*   **상태 코드 활용**: 현재 `DetectionProperties`의 구조에 의존하고 있는데, 만약 `allowedHours()` 설정이 누락되거나 잘못된 값(0~23 범위를 벗어남)이 들어올 경우에 대한 방어 로직이 없습니다.
*   **메시지 가독성**: `%02d:xx` 표현은 직관적이지만, 실제 분/초 정보가 생략되어 있어 정확한 이벤트 발생 시각을 파악하기 어렵습니다. `event.getOccurredAt()`의 전체 시각을 포함하는 것이 사후 분석에 유리합니다.
*   **단위 테스트 용이성**: `ZonedDateTime.ofInstant` 내부에서 현재 시스템의 영향을 받는 부분이 있다면 테스트 시 모킹(Mocking)이 어려울 수 있습니다. (다행히 여기서는 `event` 객체 내부의 `Instant`를 사용하므로 큰 문제는 없으나, 시간대 설정은 주입받는 방식이 더 깔끔합니다.)

### 3. 수정 제안 (요약 코드)
```java
// Overnight 대응 로직 예시
boolean withinAllowed;
if (start <= end) {
    withinAllowed = hour >= start && hour < end;
} else {
    // 예: 22시 ~ 06시 (hour >= 22 || hour < 6)
    withinAllowed = hour >= start || hour < end;
}
```

전반적으로 코드가 간결하고 의도가 명확하지만, **날짜/시간 범위 계산 시의 경계값(Overnight) 처리**는 보안 규칙에서 매우 중요한 오류이므로 반드시 수정이 필요합니다.

---

