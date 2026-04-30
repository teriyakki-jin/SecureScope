## [2026-04-30 09:45:34] `D:\develop\bangsan\backend\src\main\resources\db\migration\V1__create_security_events.sql`

제공해주신 SQL 파일에 대한 리뷰 결과입니다.

### 1. 데이터 타입 최적화 (PostgreSQL 특화)
*   **`source_ip`**: `VARCHAR(45)` 대신 PostgreSQL의 내장 타입인 **`INET`** 사용을 권장합니다. 저장 공간이 절약되며, 서브넷 검색 등 IP 관련 연산이 훨씬 빠릅니다.
*   **`mac_address`**: `VARCHAR(17)` 대신 **`MACADDR`** 타입을 사용하는 것이 효율적이고 유효성 검증도 자동으로 이루어집니다.

### 2. 확장성 및 유연성
*   **`ENUM` 타입 사용**: `event_type`을 ENUM으로 정의하면 저장 효율은 좋으나, 나중에 새로운 이벤트를 추가할 때 `ALTER TYPE`을 사용해야 하며 트랜잭션 처리에 제약이 있을 수 있습니다. 이벤트 종류가 자주 늘어날 가능성이 있다면 별도의 참조 테이블(Lookup Table) 사용을 고려해 보세요.
*   **`target_port` 제약 조건**: 포트 번호의 유효 범위(0~65535)를 보장하도록 `CHECK (target_port BETWEEN 0 AND 65535)` 제약 조건을 추가하는 것이 안전합니다.

### 3. 성능 및 인덱스
*   **복합 인덱스 고려**: 현재 단일 컬럼 인덱스들은 잘 구성되어 있습니다. 하지만 "특정 타입의 최신 이벤트"를 조회하는 쿼리가 많다면 `(event_type, occurred_at DESC)`와 같은 **복합 인덱스**가 성능상 더 유리할 수 있습니다.
*   **파티셔닝**: 보안 이벤트 로그 특성상 데이터가 급격히 쌓일 수 있습니다. 향후 대용량 데이터 처리를 위해 `occurred_at` 기준의 **테이블 파티셔닝** 도입을 미리 검토해 보시기 바랍니다.

### 4. 기타 의견
*   `occurred_at`과 `created_at`이 모두 `NOW()` 기본값을 가지고 있어 중복처럼 보일 수 있으나, 실제 발생 시각과 DB 기록 시각을 구분하는 설계는 추후 지연 로그 분석 시 유용하므로 좋은 선택입니다.
*   `user_id`가 `NULL` 허용인 점은 비로그인 사용자의 공격(예: PORT_SCAN)을 기록하기에 적절합니다.

---

## [2026-04-30 09:46:11] `D:\develop\bangsan\backend\src\main\resources\db\migration\V2__create_detection_alerts.sql`

이 SQL 마이그레이션 파일에 대한 리뷰 결과입니다. 버그, 코드 품질, 개선점을 중심으로 요약했습니다.

### 1. 코드 품질 및 개선점

*   **ENUM 타입 관리의 유연성 부족**: `alert_type`과 `severity`를 PostgreSQL `ENUM`으로 정의했습니다. `ENUM`은 타입 안전성을 제공하지만, 나중에 새로운 경고 유형이 추가될 때 `ALTER TYPE ... ADD VALUE` 명령을 사용해야 하며, 이는 트랜잭션 내에서 실행할 때 주의가 필요합니다. 요구사항이 자주 변경된다면 별도의 참조 테이블(Lookup Table) 사용을 권장합니다.
*   **외래 키 인덱스 누락**: `trigger_event_id`는 `security_events` 테이블을 참조하는 외래 키입니다. 조인 성능 최적화 및 참조 무결성 확인 속도를 높이기 위해 이 컬럼에 대한 인덱스 생성을 권장합니다.
    ```sql
    CREATE INDEX idx_detection_alerts_trigger_event_id ON detection_alerts (trigger_event_id);
    ```
*   **일관성 있는 명명**: `severity`에서 `MED`는 `MEDIUM`의 약어로 보입니다. 다른 값들은 `LOW`, `HIGH`로 명확하므로, 가독성을 위해 `MEDIUM`으로 풀어서 쓰는 것이 더 직관적일 수 있습니다.

### 2. 성능 및 설계 고려사항

*   **인덱스 최적화**: `source_ip`, `detected_at`, `alert_type`, `severity`에 각각 개별 인덱스를 생성했습니다. 검색 패턴에 따라(예: 특정 IP의 최근 경고 조회) 복합 인덱스(`source_ip`, `detected_at` DESC)를 고려해 볼 수 있습니다.
*   **IP 주소 타입**: `VARCHAR(45)`는 IPv6를 포함하기에 충분한 크기입니다. 하지만 PostgreSQL은 네트워크 주소 전용 타입인 `inet`을 제공합니다. `inet` 타입을 사용하면 IP 범위 검색이나 서브넷 마스크 연산 등을 더 효율적으로 수행할 수 있습니다.

### 3. 버그 및 안정성

*   **치명적인 버그는 발견되지 않았습니다.** 문법적으로 올바르며 `TIMESTAMPTZ` 사용 등 시간대 처리도 적절합니다.

---

**추천 수정 제안 (외래 키 인덱스 추가):**
```sql
-- 기존 코드 유지 후 아래 행 추가
CREATE INDEX idx_detection_alerts_trigger_event_id ON detection_alerts (trigger_event_id);
```

---

## [2026-04-30 09:46:38] `D:\develop\bangsan\backend\src\main\resources\db\migration\V3__create_audit_logs.sql`

진행 중인 보안 감사 로그 테이블 설계에 대한 리뷰 결과입니다.

### 1. 버그 및 정합성
*   **외래 키 인덱스 부재**: `alert_id`에 대한 인덱스가 없습니다. `detection_alerts`와 조인하거나 특정 경보의 감사 로그를 조회할 때 성능 저하가 발생할 수 있습니다.
*   **체인 무결성 제약**: `prev_hash`가 첫 번째 로그 외에는 `NOT NULL`이어야 하며, 이전 행의 `current_hash`를 참조한다는 보장이 SQL 수준에서는 없습니다. (이는 애플리케이션 계층에서 처리하거나 트리거를 고려할 수 있습니다.)

### 2. 코드 품질 및 개선점
*   **JSONB 타입 권장**: `data` 필드에 구조화된 데이터가 저장된다면 `TEXT` 대신 `JSONB` 타입을 사용하는 것이 좋습니다. 인덱싱 및 쿼리 효율성이 대폭 향상됩니다.
*   **해시 인덱스 최적화**: `current_hash`에 유니크 인덱스가 설정되어 있어 무결성은 보장되지만, 삽입이 매우 빈번할 경우 `CHAR(64)` 인덱스의 크기가 성능에 영향을 줄 수 있습니다.
*   **확장성**: `created_at` 외에 로그의 주체(예: `user_id` 또는 `system_component`)를 식별할 수 있는 컬럼을 추가하는 것을 권장합니다.

### 3. 개선된 제안 코드
```sql
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    alert_id     BIGINT REFERENCES detection_alerts(id) NOT NULL,
    data         JSONB   NOT NULL, -- TEXT에서 JSONB로 변경
    prev_hash    CHAR(64),         -- 첫 번째 레코드는 NULL 허용
    current_hash CHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 유니크 인덱스 (기존 유지)
CREATE UNIQUE INDEX idx_audit_logs_current_hash ON audit_logs (current_hash);

-- 조회 성능을 위한 외래 키 인덱스 추가
CREATE INDEX idx_audit_logs_alert_id ON audit_logs (alert_id);

-- (선택) 체인 검증을 위한 이전 해시 인덱스
CREATE INDEX idx_audit_logs_prev_hash ON audit_logs (prev_hash);
```

---

## [2026-04-30 09:47:03] `D:\develop\bangsan\backend\src\main\resources\db\migration\V4__create_mac_whitelist.sql`

제시해주신 SQL 코드를 리뷰한 결과입니다. PostgreSQL의 특성을 고려하여 개선할 수 있는 부분들이 있습니다.

### 1. 버그 및 잠재적 문제점
*   **대소문자 민감도:** `VARCHAR(17)`은 대소문자를 구분합니다. `AA:BB...`와 `aa:bb...`를 서로 다른 주소로 인식하여 중복 데이터가 들어가거나 검색 시 누락될 위험이 있습니다.
*   **데이터 정합성:** MAC 주소 형식을 강제하는 제약 조건이 없어, `12345`와 같은 잘못된 문자열도 저장될 수 있습니다.

### 2. 코드 품질 및 개선점
*   **전용 데이터 타입 사용 권장:** PostgreSQL을 사용 중이라면 `macaddr` 타입을 사용하는 것이 가장 좋습니다.
    *   **장점:** 자동으로 형식을 검증하며, 내부적으로 6바이트로 저장되어 효율적입니다. 또한 조회 시 대소문자 구분 없이 처리됩니다.
*   **제약 조건 추가:** `VARCHAR`를 유지해야 한다면 `CHECK (mac_address ~* '^([0-9A-F]{2}:){5}[0-9A-F]{2}$')`와 같은 정규식 제약을 추가하는 것이 안전합니다.
*   **인덱스 최적화:** `UNIQUE` 제약 조건이 인덱스를 자동 생성하므로 검색 성능은 확보되어 있으나, `macaddr` 타입 사용 시 더 효율적인 인덱싱이 가능합니다.
*   **데이터 분리:** 테스트용 `INSERT` 문은 마이그레이션 파일(`V4`)에 포함하기보다, 별도의 시드 데이터 파일이나 프로파일링(`test`, `local`)에 따라 실행되도록 관리하는 것이 운영 환경 배포 시 안전합니다.

### 3. 개선된 코드 예시 (macaddr 타입 활용)
```sql
CREATE TABLE mac_whitelist (
    id          BIGSERIAL PRIMARY KEY,
    mac_address MACADDR NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

