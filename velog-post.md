# 🛡️ 경량 SIEM 직접 만들기 — SecureScope 개발일지

> "로그를 수동으로 보다가 비인가 접속을 발견했을 때, 탐지 자동화의 필요성을 절감했다."

---

## 들어가며

K-water 인턴십 당시, 서버 접속 로그를 하나하나 눈으로 훑으며 이상 징후를 찾아야 했던 경험이 있다.
그날 새벽, 등록되지 않은 MAC 주소에서 반복적인 로그인 시도가 있었고 — 수십 줄의 로그를 직접 grep하고 나서야 발견할 수 있었다.

**"이걸 자동으로 탐지했다면?"**

그 질문 하나가 SecureScope의 시작점이다.
SIEM(Security Information and Event Management) 도구를 직접 구현하면서, 실무에서 막연하게 느꼈던 보안 모니터링의 내부 구조를 직접 손으로 짚어보기로 했다.

---

## 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SecureScope |
| 목적 | 보안 이벤트 수집 → 자동 탐지 → 실시간 시각화 |
| GitHub | https://github.com/teriyakki-jin/SecureScope |
| 스택 | Spring Boot 3.2 / PostgreSQL 16 / Redis 7 / React 18 |

---

## 아키텍처

```
[Python 시뮬레이터]
       │ HTTP POST
       ▼
[Spring Boot API]
  ├── 이벤트 수집 (POST /api/events)
  ├── 룰 기반 탐지 엔진
  │     ├── BruteForce Rule     ← Redis INCR + EXPIRE
  │     ├── UnauthorizedMAC Rule ← DB 화이트리스트
  │     ├── PortScan Rule       ← Redis SADD + SCARD
  │     └── AfterHours Rule     ← 시간대 검증
  ├── SHA-256 해시체인 감사 로그
  └── SSE 실시간 피드 (GET /api/events/stream)
       │
       ▼
[PostgreSQL]  [Redis]
       │
       ▼
[React 대시보드]
  ├── 실시간 이벤트/알림 피드
  ├── 탐지 이력 테이블 (필터 + 페이지네이션)
  └── IP별 접근 통계 차트
```

이벤트 수집부터 탐지, 감사 로그, 시각화까지 **하나의 파이프라인**으로 연결하는 것이 목표였다.

---

## Phase 1 — 인프라 세팅

먼저 `docker-compose.yml` 하나로 로컬 환경을 띄울 수 있게 구성했다.

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: securescope
      POSTGRES_USER: securescope
      POSTGRES_PASSWORD: securescope
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

DB 스키마는 **Flyway**로 버전 관리했다. `V1` ~ `V4` 마이그레이션 파일이 순서대로 실행되면서 테이블이 생성된다.

```
V1 — security_events     (이벤트 원본)
V2 — detection_alerts    (탐지 결과)
V3 — audit_logs          (해시체인 감사 로그)
V4 — mac_whitelist       (허용 MAC 주소)
```

PostgreSQL의 `ENUM` 타입을 활용해 `event_type`, `alert_type`, `severity` 컬럼에 타입 안전성을 적용했다.

---

## Phase 2 — 이벤트 수집 API

`POST /api/events` 하나로 네 가지 이벤트를 수집한다.

```json
{
  "eventType": "LOGIN_FAIL",
  "sourceIp": "203.0.113.42",
  "macAddress": "DE:AD:BE:EF:00:01",
  "targetPort": null,
  "userId": "admin",
  "occurredAt": "2026-04-30T10:00:00Z"
}
```

이벤트가 저장되면 두 가지 일이 동시에 일어난다.

1. **Redis 카운터 갱신** — `ip:count:{sourceIp}` 키를 `INCR`
2. **ApplicationEvent 발행** — `SecurityEventCreatedEvent`를 Spring 이벤트 버스에 던진다

```java
@Transactional
public SecurityEventResponse ingest(CreateEventRequest req) {
    SecurityEvent saved = eventRepository.save(event);

    redis.opsForValue().increment("ip:count:" + saved.getSourceIp());

    eventPublisher.publishEvent(new SecurityEventCreatedEvent(saved));
    return SecurityEventResponse.from(saved);
}
```

**Spring ApplicationEvent**를 활용한 이유는 수집 레이어와 탐지 레이어를 **느슨하게 결합**하기 위해서다.
`EventService`는 `RuleEngine`의 존재를 전혀 모른다. 이벤트만 던지면, 관심 있는 컴포넌트가 알아서 처리한다.

---

## Phase 3 — 룰 기반 탐지 엔진

핵심 설계 원칙은 **전략 패턴(Strategy Pattern)** 이다.

```java
public interface DetectionRule {
    Optional<DetectionAlert> evaluate(SecurityEvent event);
}
```

각 탐지 룰은 이 인터페이스를 구현한다. `RuleEngine`은 `List<DetectionRule>`을 주입받아 모든 룰을 순회할 뿐이다.
새 룰을 추가해도 기존 코드를 건드릴 필요가 없다.

### 룰 1: 브루트포스 탐지

> 동일 IP에서 LOGIN_FAIL이 1분 내 5회 이상 발생하면 탐지

Redis의 `INCR + EXPIRE` 조합으로 슬라이딩 윈도우를 구현했다.

```java
Long count = redis.opsForValue().increment("bf:" + sourceIp);
if (count == 1) {
    redis.expire("bf:" + sourceIp, 60, TimeUnit.SECONDS); // 첫 실패 시 TTL 설정
}

if (count >= 5) {
    redis.delete("bf:" + sourceIp); // 탐지 후 리셋
    return Optional.of(DetectionAlert.of(ALERT_BRUTE_FORCE, HIGH, ...));
}
```

> **고민한 부분**: 카운터를 탐지 후 리셋하지 않으면 임계값 초과 시마다 중복 알림이 발생한다. 탐지 즉시 삭제하는 방식으로 처리했다.

### 룰 2: 비인가 MAC 접근

> DB의 `mac_whitelist` 테이블에 없는 MAC 주소면 탐지

단순하지만 실무에서 가장 자주 쓰이는 패턴이다.

```java
Integer count = jdbc.queryForObject(
    "SELECT COUNT(*) FROM mac_whitelist WHERE mac_address = ?",
    Integer.class, mac
);
if (count == 0) return Optional.of(DetectionAlert.of(ALERT_UNAUTHORIZED_MAC, HIGH, ...));
```

### 룰 3: 포트 스캔 탐지

> 동일 IP가 10초 내 10개 이상의 서로 다른 포트에 접근하면 탐지

Redis **SET 자료구조**를 활용했다. 같은 포트를 중복 접근해도 SET에는 하나만 남기 때문에 **고유 포트 수**를 정확히 측정할 수 있다.

```java
redis.opsForSet().add("ps:" + sourceIp, String.valueOf(targetPort));
redis.expire("ps:" + sourceIp, 10, TimeUnit.SECONDS);

Long portCount = redis.opsForSet().size("ps:" + sourceIp);
if (portCount >= 10) {
    redis.delete("ps:" + sourceIp);
    return Optional.of(DetectionAlert.of(ALERT_PORT_SCAN, HIGH, ...));
}
```

### 룰 4: 시간 외 접근

> 허용 시간대(09:00 ~ 18:00 KST) 밖에서 이벤트가 발생하면 탐지

```java
int hour = ZonedDateTime.ofInstant(event.getOccurredAt(), ZoneId.of("Asia/Seoul")).getHour();
if (hour < 9 || hour >= 18) {
    return Optional.of(DetectionAlert.of(ALERT_AFTER_HOURS, MED, ...));
}
```

---

## Phase 4 — SHA-256 해시체인 감사 로그

> "감사 로그가 변조되면, 변조됐다는 사실조차 알 수 없다."

이 문제를 해결하기 위해 **블록체인의 해시체인 원리**를 도입했다.

```
AuditLog[0]: prevHash = "000...0"  (제네시스)
              currentHash = SHA-256(data + prevHash)

AuditLog[1]: prevHash = AuditLog[0].currentHash
              currentHash = SHA-256(data + prevHash)

AuditLog[n]: prevHash = AuditLog[n-1].currentHash
              currentHash = SHA-256(data + prevHash)
```

중간 레코드 하나를 변조하면 이후 모든 레코드의 해시가 깨진다.

```java
public AuditLog append(DetectionAlert alert) {
    String data     = serialize(alert);
    String prevHash = auditLogRepository.findLatest()
            .map(AuditLog::getCurrentHash)
            .orElse("0".repeat(64));          // 첫 레코드는 제네시스
    String currentHash = sha256(data + prevHash);

    return auditLogRepository.save(AuditLog.of(alert, data, prevHash, currentHash));
}
```

`GET /api/audit/verify` 호출 시 전체 체인을 순회하며 각 레코드의 해시를 재계산해 일치 여부를 확인한다.

> 이전 프로젝트(ConsentLedger)에서 구축한 해시체인 구조를 그대로 재사용했다.
> **"보안 설계 철학이 일관된다"** 는 것을 포트폴리오에서 어필할 수 있는 지점이다.

---

## Phase 5 — SSE 실시간 피드

WebSocket 대신 **SSE(Server-Sent Events)** 를 선택한 이유는 단방향 푸시로 충분하고, 구현이 단순하기 때문이다.

```java
@Component
public class SseBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5분 타임아웃
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitter.complete(); emitters.remove(emitter); });
        return emitter;
    }

    @EventListener
    public void onAlertCreated(DetectionAlertCreatedEvent e) {
        broadcast(SsePayload.alert(DetectionAlertResponse.from(e.alert())));
    }
}
```

다중 구독자를 `CopyOnWriteArrayList`로 관리했다. 동시 읽기가 많고 쓰기(구독/해제)가 적은 특성에 적합한 자료구조다.

React 클라이언트에서는 커스텀 훅으로 구독을 관리한다.

```typescript
export function useSse({ onEvent, onAlert }: SseHandlers) {
  useEffect(() => {
    const es = new EventSource('/api/events/stream');

    es.addEventListener('event', (e) => onEvent?.(JSON.parse(e.data).data));
    es.addEventListener('alert', (e) => onAlert?.(JSON.parse(e.data).data));

    return () => es.close(); // 언마운트 시 연결 해제
  }, []);
}
```

---

## Phase 6 — React 대시보드

Tailwind CSS + Recharts 조합으로 세 개의 뷰를 구성했다.

| 컴포넌트 | 역할 |
|----------|------|
| `EventFeed` | SSE 구독, 실시간 이벤트/알림 피드 (최대 50건 유지) |
| `AlertTable` | 탐지 이력, severity·type 필터, 페이지네이션 |
| `IpChart` | IP별 이벤트 수 BarChart, 15초 자동 갱신 |

대시보드 색상 체계는 심각도를 직관적으로 구분할 수 있도록 설계했다.

- 🔴 **HIGH** — 빨간색 (브루트포스, 비인가 MAC, 포트 스캔)
- 🟡 **MED** — 노란색 (시간 외 접근)
- ⚫ **LOW** — 슬레이트 (기타)

---

## Phase 7 — Python 시뮬레이터

실제 공격 시나리오를 재현할 수 있는 시뮬레이터를 만들었다.

```bash
# 전체 시나리오 한 번에 실행
python simulate.py --scenario all --verbose

# 브루트포스만 특정 IP로 실행
python simulate.py --scenario brute --attacker-ip 203.0.113.42

# 포트 스캔
python simulate.py --scenario portscan

# 비인가 MAC
python simulate.py --scenario mac

# 새벽 2시 접근 (시간 외 접근 탐지 트리거)
python simulate.py --scenario afterhours
```

외부 라이브러리 없이 표준 라이브러리(`urllib`, `argparse`)만 사용했다. 어떤 환경에서도 `python simulate.py` 하나로 실행 가능하다.

---

## Phase 8 — 테스트

탐지 엔진과 감사 로그 핵심 모듈을 집중적으로 테스트했다.

### 룰 테스트 — Mockito

Redis를 목(Mock)으로 주입해 I/O 없이 순수하게 룰 로직만 검증했다.

```java
@Test
void shouldAlertAtThreshold() {
    when(valueOps.increment(anyString())).thenReturn(5L); // 5번째 실패 시뮬레이션

    Optional<DetectionAlert> result = rule.evaluate(loginFailEvent("1.2.3.4"));

    assertThat(result).isPresent();
    assertThat(result.get().getAlertType()).isEqualTo(ALERT_BRUTE_FORCE);
    assertThat(result.get().getSeverity()).isEqualTo(HIGH);
}
```

### 경계값 테스트 — ParameterizedTest

`AfterHoursRule`은 시각(hour) 값에 따른 경계값을 파라미터화 테스트로 처리했다.

```java
@ParameterizedTest
@ValueSource(ints = {9, 10, 12, 17})
void shouldNotAlertWithinAllowedHours(int hour) { ... }

@ParameterizedTest
@ValueSource(ints = {0, 2, 6, 8, 18, 22})
void shouldAlertOutsideAllowedHours(int hour) { ... }
```

### 해시체인 무결성 테스트

```java
@Test
void verifyReturnsFalseWhenTampered() throws Exception {
    AuditLog log = auditService.append(makeAlert(1L));

    // currentHash를 임의 값으로 변조
    Field hashField = AuditLog.class.getDeclaredField("currentHash");
    hashField.setAccessible(true);
    hashField.set(log, "a".repeat(64));

    when(repository.findAllOrdered()).thenReturn(List.of(log));

    assertThat(auditService.verify().valid()).isFalse(); // 변조 감지
}
```

### 통합 테스트 — Testcontainers

실제 PostgreSQL 컨테이너를 띄워 API 엔드포인트를 테스트했다. H2 인메모리 DB 대신 실제 환경과 동일한 DB를 사용하기 때문에 신뢰도가 높다.

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("securescope");

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
}
```

---

## 실제 실행 결과

### 시뮬레이터 실행

```
🛡️  SecureScope Simulator
   Target  : http://localhost:8090
   Scenario: all
   Attacker: 203.0.113.42
──────────────────────────────────────────────────

[NORMAL] 10개의 정상 이벤트를 전송합니다...
  ✓ [LOGIN_SUCCESS] 192.168.1.50 → id=1
  ✓ [LOGIN_SUCCESS] 192.168.1.11 → id=2
  ... (총 10건)

[BRUTE FORCE] 203.0.113.42 에서 로그인 실패 7회 전송...
  ✓ [LOGIN_FAIL] 203.0.113.42 → id=11  (시도 1/7)
  ✓ [LOGIN_FAIL] 203.0.113.42 → id=12  (시도 2/7)
  ...
  ✓ [LOGIN_FAIL] 203.0.113.42 → id=17  (시도 7/7)

[PORT SCAN] 203.0.113.42 → 12개 포트 접근
  ✓ [PORT_SCAN] 203.0.113.42 → id=18 ~ id=29

[UNAUTHORIZED MAC] 3개의 미등록 MAC 주소로 접근...
  ✓ [UNAUTHORIZED_ACCESS] 203.0.113.42 → id=30 ~ id=32

[AFTER HOURS] 새벽 2시 KST 타임스탬프로 접근 이벤트 전송...
  ✓ [LOGIN_SUCCESS] 203.0.113.42 → id=33 ~ id=35

✅  시뮬레이션 완료 (총 35개 이벤트)
```

### 탐지 결과 — GET /api/alerts

시뮬레이터 실행 직후 API 응답. 4가지 룰이 모두 정상 발동됐다.

```json
{
  "success": true,
  "data": [
    {
      "alertType": "ALERT_BRUTE_FORCE",
      "severity": "HIGH",
      "sourceIp": "203.0.113.42",
      "detail": "Login failure count: 5 in 60s window"
    },
    {
      "alertType": "ALERT_PORT_SCAN",
      "severity": "HIGH",
      "sourceIp": "203.0.113.42",
      "detail": "Distinct ports accessed: 10 in 10s window"
    },
    {
      "alertType": "ALERT_UNAUTHORIZED_MAC",
      "severity": "HIGH",
      "sourceIp": "203.0.113.42",
      "detail": "Unregistered MAC address: DE:AD:BE:EF:00:01"
    },
    {
      "alertType": "ALERT_UNAUTHORIZED_MAC",
      "severity": "HIGH",
      "sourceIp": "203.0.113.42",
      "detail": "Unregistered MAC address: CA:FE:BA:BE:00:02"
    },
    {
      "alertType": "ALERT_UNAUTHORIZED_MAC",
      "severity": "HIGH",
      "sourceIp": "203.0.113.42",
      "detail": "Unregistered MAC address: FA:CE:B0:0C:00:03"
    },
    {
      "alertType": "ALERT_AFTER_HOURS",
      "severity": "MED",
      "sourceIp": "203.0.113.42",
      "detail": "Access at 02:xx KST — allowed window 09:00-18:00"
    }
  ],
  "meta": { "total": 8 }
}
```

### 해시체인 무결성 검증 — GET /api/audit/verify

```json
{
  "success": true,
  "data": {
    "valid": true,
    "failedAtId": null,
    "message": "Chain integrity OK. Records: 8"
  }
}
```

8개 탐지 이벤트가 모두 해시체인에 기록됐고, 체인 무결성도 정상이다.

### IP별 이벤트 통계 — GET /api/stats/ip

```json
{
  "data": {
    "203.0.113.42": 25,
    "192.168.1.10": 4,
    "192.168.1.50": 3,
    "10.0.0.5": 2,
    "192.168.1.11": 1
  }
}
```

### 대시보드 (React)

`http://localhost:5173` 에 접속하면 다음 세 뷰가 렌더링된다.

| 뷰 | 내용 |
|---|---|
| **Live Events / Live Alerts** | SSE 실시간 피드 — 이벤트 타입별 색상 분류, 알림은 심각도 배지 표시 |
| **Detection History** | 총 8건, BRUTE_FORCE·PORT_SCAN·UNAUTHORIZED_MAC·AFTER_HOURS 전 유형 탐지 확인, 심각도·유형 필터 동작 |
| **IP별 이벤트 통계** | 공격자 IP `203.0.113.42` 25건으로 압도적 1위, Recharts BarChart 렌더링 |

---

## 테스트 결과

### 단위 테스트 — 19개 전원 PASS

```
> Task :test

com.securescope.audit.AuditServiceTest
  ✓ 첫 번째 레코드의 prevHash 는 '0' * 64 이다                       (7.289s)
  ✓ 두 번째 레코드의 prevHash 는 첫 레코드의 currentHash 이다          (0.022s)
  ✓ 체인 무결성이 유효하면 verify() 가 true 를 반환한다                (0.013s)
  ✓ prevHash 연결이 끊어지면 verify() 가 false 를 반환한다             (0.009s)
  ✓ 해시가 변조되면 verify() 가 false 를 반환한다                     (0.007s)

com.securescope.detection.AfterHoursRuleTest
  ✓ KST 9시는 허용 시간대 내 → 알림 없음
  ✓ KST 10시는 허용 시간대 내 → 알림 없음
  ✓ KST 12시는 허용 시간대 내 → 알림 없음
  ✓ KST 17시는 허용 시간대 내 → 알림 없음
  ✓ KST 0시는 허용 시간대 밖 → ALERT_AFTER_HOURS
  ✓ KST 2시는 허용 시간대 밖 → ALERT_AFTER_HOURS
  ✓ KST 6시는 허용 시간대 밖 → ALERT_AFTER_HOURS
  ✓ KST 8시는 허용 시간대 밖 → ALERT_AFTER_HOURS
  ✓ KST 18시는 허용 시간대 밖 → ALERT_AFTER_HOURS
  ✓ KST 22시는 허용 시간대 밖 → ALERT_AFTER_HOURS

com.securescope.detection.BruteForceRuleTest
  ✓ LOGIN_FAIL 이 아닌 이벤트는 평가하지 않는다
  ✓ 임계값 미만이면 알림이 발생하지 않는다
  ✓ 임계값 이상이면 ALERT_BRUTE_FORCE 가 반환된다             (1.190s)
  ✓ 탐지 후 Redis 카운터를 리셋한다

BUILD SUCCESSFUL
19 tests completed, 0 failures, 0 skipped
```

### JaCoCo 커버리지 (핵심 패키지)

| 패키지 | 커버 라인 | 전체 라인 | 커버리지 |
|---|---|---|---|
| `audit` | 39 | 52 | **75.0%** |
| `detection/rule` | 25 | 52 | **48.1%** |
| `detection` | 25 | 77 | 32.5% |
| `event` | 17 | 55 | 30.9% |
| 전체 | 106 | 300 | **35.3%** |

> `audit` 패키지(해시체인 핵심 로직)가 75%로 가장 높다.
> 통합 테스트(Testcontainers)는 Docker 환경에서 추가 실행 가능하며, 실행 시 `event` 패키지 커버리지가 크게 올라간다.

---

## 코드 리뷰 결과 및 수정

구현 완료 후 코드 리뷰를 진행했다. CRITICAL/HIGH 이슈가 7건 발견됐고 모두 수정했다.

### C4 — verify()의 체인 연결 검증 누락 (가장 중요)

```java
// 수정 전: 각 레코드의 해시 재계산만 확인
for (AuditLog log : logs) {
    String expected = sha256(log.getData() + log.getPrevHash());
    if (!expected.equals(log.getCurrentHash())) { ... }
}

// 수정 후: 연결 무결성도 함께 확인
String expectedPrev = "0".repeat(64);
for (AuditLog log : logs) {
    // (1) prevHash 연결 검증 — row[i].prevHash == row[i-1].currentHash
    if (!log.getPrevHash().equals(expectedPrev)) {
        return new VerifyResult(false, log.getId(), "Chain linkage broken...");
    }
    // (2) 개별 해시 재계산 검증
    if (!sha256(log.getData() + log.getPrevHash()).equals(log.getCurrentHash())) { ... }
    expectedPrev = log.getCurrentHash();
}
```

기존 코드는 레코드를 삭제하거나 순서를 바꿔도 각 레코드가 자기 해시만 맞으면 통과됐다. 수정 후 연결이 끊기면 즉시 탐지한다.

### C3 — append()의 동시 쓰기 경쟁 조건

"최신 레코드 조회 → 새 해시 계산 → 저장" 사이에 다른 스레드가 끼어들면 두 레코드가 같은 `prevHash`를 가져 체인이 분기된다.

```java
// 수정: SERIALIZABLE isolation + prev_hash UNIQUE 제약 (V5 마이그레이션)
@Transactional(isolation = Isolation.SERIALIZABLE)
public AuditLog append(DetectionAlert alert) { ... }
```

PostgreSQL SERIALIZABLE이 phantom read를 막고, `prev_hash` UNIQUE 제약이 최후 안전망 역할을 한다.

### C1 — PortScanRule의 isNew 판별 버그

```java
// 수정 전: opsForSet().add()는 Long 반환 → != null 항상 true
Boolean isNew = redis.opsForSet().add(key, port) != null
        && redis.getExpire(key) < 0;  // 의도와 달리 항상 TTL을 설정

// 수정 후: getExpire()로 TTL 미설정 여부를 명시적으로 판별
redis.opsForSet().add(key, String.valueOf(event.getTargetPort()));
Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
if (ttl != null && ttl < 0) {
    redis.expire(key, window, TimeUnit.SECONDS);
}
```

### C2 — BruteForceRule 주석 오류

INCR + EXPIRE는 **고정 윈도우 카운터(Fixed Window Counter)** 다. 주석의 "슬라이딩 윈도우"는 오탐이었다. 슬라이딩 윈도우가 필요하면 Sorted Set(ZADD/ZRANGEBYSCORE)으로 교체해야 한다.

### H2/H3 — @EventListener의 동기 실행

`@EventListener`는 이벤트 발행 스레드와 같은 스레드에서 동기 실행된다.

- **RuleEngine**: `EventService.ingest()` 트랜잭션 내부에서 룰 평가가 실행돼, 룰 실패 시 이벤트 저장도 롤백됨 → `@TransactionalEventListener(AFTER_COMMIT) + REQUIRES_NEW` 적용
- **SseBroadcaster**: HTTP 요청 스레드가 모든 SSE 구독자에게 전송을 마칠 때까지 블로킹 → `@Async` 적용

### H4 — sourceIp 형식 미검증 (Redis 키 인젝션)

```java
// 수정: IPv4 형식 강제
@Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
String sourceIp
```

Redis 키가 `brute_force:` + `sourceIp`로 구성되므로, 임의 문자열 입력 시 키 공간 오염이 가능했다.

### H5 — IP 카운터 Redis 키 TTL 없음

```java
// 수정: 신규 키 생성 시 24시간 TTL 설정
redis.opsForValue().increment(ipCountKey);
Long ttl = redis.getExpire(ipCountKey, TimeUnit.SECONDS);
if (ttl != null && ttl < 0) {
    redis.expire(ipCountKey, 24, TimeUnit.HOURS);
}
```

---

## 구현하면서 고민한 것들

### 1. 동시 쓰기 문제 — 해시체인 append

해시체인 `append()`는 "직전 레코드 조회 → 새 해시 계산 → 저장" 세 단계가 원자적으로 실행돼야 한다.
동시 요청이 들어오면 두 레코드가 같은 `prevHash`를 가져 체인이 분기된다.

→ `@Transactional(isolation = SERIALIZABLE)`로 PostgreSQL이 트랜잭션 충돌을 직렬화하게 했고,
`prev_hash UNIQUE` 제약을 DB 레벨 최후 안전망으로 추가했다.

### 2. SSE 연결 누수

`SseEmitter`는 타임아웃, 완료, 에러 세 가지 상황에서 해제해야 한다. 하나라도 놓치면 리스트에 죽은 emitter가 쌓인다.

```java
emitter.onCompletion(() -> emitters.remove(emitter));
emitter.onTimeout(()   -> { emitter.complete(); emitters.remove(emitter); });
emitter.onError(e      -> emitters.remove(emitter));
```

세 케이스 모두 명시적으로 처리했다.

### 3. 포트 스캔 룰의 TTL 판별

`SADD` 후 `getExpire`로 TTL을 확인해 음수(-1 또는 -2, TTL 미설정) 인 경우에만 `EXPIRE`를 설정했다.
`opsForSet().add()`의 반환값이 항상 non-null이어서 `!= null` 조건이 의미 없다는 걸 코드 리뷰에서 발견했다.

---

## 실행 방법

```bash
# 1. 인프라 기동
docker-compose up -d

# 2. 백엔드 실행 (backend/)
./gradlew bootRun

# 3. 프론트엔드 실행 (frontend/)
npm install && npm run dev
# → http://localhost:5173

# 4. 공격 시나리오 시뮬레이션
python simulator/simulate.py --scenario all --verbose

# 5. 테스트 실행
./gradlew test
```

---

## 마치며

단순히 "로그 저장하는 API" 수준에서 출발했지만,
룰 엔진의 전략 패턴, Redis 고정 윈도우 카운터, 해시체인, SSE 실시간 피드까지 레이어가 하나씩 쌓이면서 꽤 그럴듯한 구조가 됐다.

가장 기억에 남는 부분은 **ApplicationEvent로 파이프라인을 연결한 것**이다.
`EventService → RuleEngine → AuditService → SseBroadcaster` — 이 네 컴포넌트는 서로의 존재를 모른다. 이벤트 버스를 통해서만 대화한다. 나중에 SlackNotifier나 EmailAlert를 추가할 때도 기존 코드를 한 줄도 건드리지 않아도 된다.

두 번째로 기억에 남는 건 **코드 리뷰에서 발견된 것들**이다.
`verify()`가 체인 연결을 검증하지 않았고, `PortScanRule`의 `isNew` 조건이 항상 true였고, `@EventListener`가 트랜잭션 내부에서 동기 실행되고 있었다. 기능 테스트는 통과했지만 실제 운영 환경에서 문제가 됐을 부분들이다. "동작하는 코드"와 "올바른 코드" 사이의 거리를 체감했다.

보안은 결국 **가시성(Visibility)** 의 문제라고 생각한다.
무슨 일이 일어나고 있는지 볼 수 있어야, 대응할 수 있다.
SecureScope는 그 가시성을 확보하기 위한 작은 시도였다.

---

**GitHub**: https://github.com/teriyakki-jin/SecureScope
