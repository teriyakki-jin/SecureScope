# SecureScope

경량 SIEM(Security Information and Event Management) 포트폴리오 프로젝트.
보안 이벤트를 수집하고, 룰 기반으로 위협을 탐지하며, 실시간 대시보드로 시각화한다.

> 공공기관 인턴십에서 수작업으로 로그를 grep하다 비인가 접속을 뒤늦게 발견한 경험에서 출발했다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **이벤트 수집** | REST API로 보안 이벤트 ingestion |
| **룰 기반 탐지** | BruteForce / UnauthorizedMAC / PortScan / AfterHours 4개 룰 |
| **SHA-256 해시체인** | 탐지 이벤트의 변조 불가 감사 로그 |
| **SSE 실시간 피드** | Server-Sent Events로 대시보드 실시간 갱신 |
| **React 대시보드** | 이벤트 피드, 알림 테이블, IP 통계 차트 |

---

## 기술 스택

| 계층 | 기술 |
|------|------|
| Backend | Spring Boot 3.2 · Java 17 · Gradle |
| Database | PostgreSQL 16 · Flyway 마이그레이션 |
| Cache | Redis 7 (Fixed Window Counter, SET 기반 포트 추적) |
| Frontend | React 18 · Vite · TailwindCSS · Recharts |
| Infra | Docker Compose |
| Test | JUnit 5 · Mockito · Testcontainers · JaCoCo |

---

## 아키텍처

```
[Python 시뮬레이터]
       │ HTTP POST /api/events
       ▼
[Spring Boot]
  ├── EventService           ← 이벤트 수집 + Redis IP 카운터
  ├── RuleEngine             ← Strategy 패턴, 4개 룰 평가
  │     ├── BruteForceRule   ← Redis INCR+EXPIRE (고정 윈도우)
  │     ├── UnauthorizedMACRule ← DB 화이트리스트
  │     ├── PortScanRule     ← Redis SADD+SCARD
  │     └── AfterHoursRule   ← 시간대 검증
  ├── AuditService           ← SHA-256 해시체인 감사 로그
  └── SseBroadcaster         ← SSE 실시간 브로드캐스트 (@Async)
       │
       ▼
[PostgreSQL]  [Redis]        [React Dashboard]
```

**이벤트 흐름:**
`EventService` → `@TransactionalEventListener(AFTER_COMMIT)` → `RuleEngine` → `AuditService` → `SseBroadcaster`

컴포넌트 간 직접 의존 없이 Spring ApplicationEvent로 연결. SlackNotifier, EmailAlert 추가 시 기존 코드 수정 불필요.

---

## 탐지 룰

### BruteForce
- 동일 IP에서 `LOGIN_FAIL` 이 **60초 내 5회** 이상 발생 시 탐지
- Redis INCR + EXPIRE 고정 윈도우 카운터

### UnauthorizedMAC
- DB 화이트리스트에 없는 MAC 주소 접근 즉시 탐지

### PortScan
- 동일 IP가 **10초 내 10개** 이상의 서로 다른 포트에 접근 시 탐지
- Redis SADD(Set) + SCARD로 포트 수 집계

### AfterHours
- 허용 시간대(09:00–18:00 KST) 외 접근 탐지
- `application.yml`에서 시간 범위 조정 가능

---

## SHA-256 해시체인

```
row[0]: prevHash = "000...0"(64)   currentHash = SHA-256(data₀ + prevHash₀)
row[1]: prevHash = currentHash₀    currentHash = SHA-256(data₁ + prevHash₁)
row[2]: prevHash = currentHash₁    ...
```

- `GET /api/audit/verify` 로 전체 체인 무결성 검증 (개별 해시 + 연결 무결성 동시 확인)
- `prev_hash UNIQUE` DB 제약 + `SERIALIZABLE` 트랜잭션으로 동시 쓰기 경쟁 조건 방지

---

## 빠른 시작

### 사전 요구 사항

- Java 17+
- Docker Desktop
- Node.js 18+
- Python 3.8+

### 로컬 개발 (권장)

```bash
# 1. 저장소 클론
git clone https://github.com/teriyakki-jin/SecureScope.git
cd SecureScope

# 2. 인프라 기동 (PostgreSQL + Redis)
docker compose up -d

# 3. 백엔드 실행
cd backend
./gradlew bootRun
# → http://localhost:8080

# 4. 프론트엔드 실행 (새 터미널)
cd frontend
npm install && npm run dev
# → http://localhost:5173

# 5. 공격 시나리오 시뮬레이션
python simulator/simulate.py --scenario all --verbose
```

### Docker 전체 배포

```bash
# 백엔드 포함 전체 컨테이너 기동
docker compose --profile full up -d --build

# 로그 확인
docker compose logs -f backend
```

### 시뮬레이터 옵션

```bash
# 전체 시나리오
python simulator/simulate.py --scenario all

# 특정 시나리오
python simulator/simulate.py --scenario brute     # 브루트포스
python simulator/simulate.py --scenario portscan  # 포트 스캔
python simulator/simulate.py --scenario mac       # 비인가 MAC
python simulator/simulate.py --scenario afterhours # 시간 외 접근

# 공격자 IP 지정
python simulator/simulate.py --scenario brute --attacker-ip 10.0.0.99 --verbose
```

---

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/events` | 보안 이벤트 수집 |
| `GET` | `/api/events` | 이벤트 목록 (페이징) |
| `GET` | `/api/events/stream` | SSE 실시간 스트림 |
| `GET` | `/api/alerts` | 탐지 알림 목록 |
| `GET` | `/api/stats/ip` | IP별 이벤트 통계 |
| `GET` | `/api/audit/verify` | 해시체인 무결성 검증 |

### 이벤트 수집 예시

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "LOGIN_FAIL",
    "sourceIp": "203.0.113.42",
    "userId": "admin"
  }'
```

### 이벤트 타입

| 타입 | 설명 |
|------|------|
| `LOGIN_SUCCESS` | 로그인 성공 |
| `LOGIN_FAIL` | 로그인 실패 |
| `PORT_SCAN` | 포트 스캔 |
| `UNAUTHORIZED_ACCESS` | 비인가 접근 (MAC 검사) |

---

## 테스트

```bash
cd backend

# 전체 테스트 실행
./gradlew test

# 커버리지 리포트 생성
./gradlew test jacocoTestReport
# → build/reports/jacoco/test/html/index.html
```

### 테스트 구성 (19개, 전원 PASS)

| 클래스 | 테스트 수 | 주요 검증 |
|--------|-----------|-----------|
| `AuditServiceTest` | 5 | 해시체인 생성·연결·변조 탐지 |
| `AfterHoursRuleTest` | 10 | 경계값 파라미터화 테스트 |
| `BruteForceRuleTest` | 4 | 임계값·리셋·필터링 |
| `EventControllerIntegrationTest` | 3 | Testcontainers PostgreSQL |

---

## 설정

`backend/src/main/resources/application.yml`

```yaml
securescope:
  detection:
    brute-force:
      threshold: 5        # 탐지 임계값 (실패 횟수)
      window-seconds: 60  # 측정 윈도우 (초)
    port-scan:
      threshold: 10       # 탐지 임계값 (포트 수)
      window-seconds: 10
    allowed-hours:
      start: 9            # 허용 시작 시각 (KST)
      end: 18             # 허용 종료 시각 (KST)
```

---

## 프로젝트 구조

```
SecureScope/
├── backend/
│   ├── src/main/java/com/securescope/
│   │   ├── audit/          # SHA-256 해시체인 감사 로그
│   │   ├── dashboard/      # SSE 브로드캐스터
│   │   ├── detection/      # 룰 엔진 + 탐지 알림
│   │   │   └── rule/       # 4개 탐지 룰
│   │   ├── event/          # 보안 이벤트 수집
│   │   └── common/         # 공통 유틸리티
│   └── src/main/resources/db/migration/  # Flyway V1~V5
├── frontend/
│   └── src/
│       ├── components/     # EventFeed / AlertTable / IpChart
│       └── hooks/          # useSse (SSE 구독)
├── simulator/
│   └── simulate.py         # 공격 시나리오 시뮬레이터
└── docker-compose.yml
```

---

## 개발일지

Velog 개발일지: [SecureScope 개발일지](https://velog.io)

---

## License

MIT
