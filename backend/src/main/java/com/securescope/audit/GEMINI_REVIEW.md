## [2026-04-30 10:09:15] `D:\develop\bangsan\backend\src\main\java\com\securescope\audit\AuditLog.java`

간결하게 분석한 `AuditLog.java` 코드 리뷰 결과입니다.

### 1. 버그 및 잠재적 문제
*   **`@ToString`과 지연 로딩(Lazy Loading):** `alert` 필드가 `FetchType.LAZY`로 설정되어 있습니다. `@ToString`을 그대로 사용하면 `alert` 객체를 조회하기 위해 불필요한 SQL이 실행되거나, 트랜잭션 외부에서 호출 시 `LazyInitializationException`이 발생할 수 있습니다.
    *   **개선:** `@ToString(exclude = "alert")`를 사용하세요.

### 2. 코드 품질 및 유지보수
*   **생성자 캡슐화:** `of` 정적 팩토리 메서드에서 필드에 직접 접근하여 값을 설정하고 있습니다. 가독성과 불변성 보장을 위해 모든 필드를 인자로 받는 `private` 생성자를 만들고 `of`에서 이를 호출하는 방식이 더 객체지향적입니다.
*   **Lombok `@Builder` 권장:** 필드가 늘어날 경우를 대비해 `@Builder` 패턴을 적용하면 객체 생성 시 가독성을 높일 수 있습니다.
*   **데이터 무결성:** `prevHash`와 `currentHash`를 통해 체이닝을 구현한 점은 좋으나, 엔티티 내부에서 해시 값을 계산하거나 검증하는 로직이 없어 외부 주입에만 의존하고 있습니다. (비즈니스 로직에서 처리 중이라면 무방합니다.)

### 3. 개선 제안
*   **Spring Data JPA Auditing 활용:** `@PrePersist` 대신 `@CreatedDate`와 `@EntityListeners(AuditingEntityListener.class)`를 사용하면 프로젝트 전반의 감사(Auditing) 설정을 일관되게 관리할 수 있습니다.
*   **`data` 필드 타입:** `TEXT` 타입은 대용량 데이터를 담기에 적합하지만, 검색이나 구조적 분석이 필요하다면 DB에 따라 `JSON` 타입을 고려해 볼 수 있습니다.

### 개선된 코드 예시 (요약)
```java
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "alert") // 지연 로딩 필드 제외
@AllArgsConstructor(access = AccessLevel.PRIVATE) // private 생성자 추가
@Builder
public class AuditLog {
    // ... 필드 동일 ...

    public static AuditLog of(DetectionAlert alert, String data, String prevHash, String currentHash) {
        return AuditLog.builder()
                .alert(alert)
                .data(data)
                .prevHash(prevHash)
                .currentHash(currentHash)
                .build();
    }
}
```

---

## [2026-04-30 10:09:51] `D:\develop\bangsan\backend\src\main\java\com\securescope\audit\AuditLogRepository.java`

`AuditLogRepository.java` 파일에 대한 리뷰 결과입니다. 버그, 코드 품질, 개선점 중심으로 정리했습니다.

### 1. 주요 개선점: Spring Data JPA 메서드 명명 규칙 활용
현재 `@Query`를 통해 작성된 쿼리들은 Spring Data JPA의 **쿼리 메서드 명명 규칙(Query Method Naming Convention)**을 사용하면 훨씬 직관적이고 간결하게 바꿀 수 있습니다.

*   **`findLatest()`**: `findFirstByOrderByIdDesc()`로 대체 가능합니다.
*   **`findAllOrdered()`**: `findAllByOrderByIdAsc()`로 대체 가능합니다.

### 2. 기술적 검토: JPQL의 `LIMIT` 지원 여부
작성하신 `SELECT a FROM AuditLog a ORDER BY a.id DESC LIMIT 1`에서 **`LIMIT` 구문은 표준 JPQL에서 지원하지 않습니다.** (특정 Hibernate 버전이나 Dialect에 따라 동작할 수 있으나 이식성이 떨어집니다.)
*   Spring Data JPA에서는 `findFirstBy...`, `findTopBy...`를 사용하거나 `Pageable` 파라미터를 사용하는 것이 표준 방식입니다.

### 3. 잠재적 위험 및 권장 사항
*   **정렬 기준**: `id`를 기준으로 정렬하고 있습니다. 만약 ID가 생성 순서를 보장하지 않는 전략(예: UUID 등)으로 변경될 경우 순서가 꼬일 수 있습니다. 생성 일시를 나타내는 필드(예: `createdAt`)가 있다면 해당 필드를 기준으로 정렬하는 것이 더 안전합니다.
*   **대량 데이터 처리**: `findAllOrdered()`는 전체 로그를 메모리에 로드합니다. `AuditLog`는 데이터가 계속 쌓이는 성격이므로, 실제 서비스에서는 `Pageable`을 활용해 **페이징 처리**를 하는 것을 강력히 권장합니다.

---

### 개선된 코드 제안

```java
package com.securescope.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // JPQL 없이 메서드 이름만으로 최신 로그 1건 조회
    Optional<AuditLog> findFirstByOrderByIdDesc();

    // ID 오름차순으로 전체 조회 (데이터가 많아질 경우 Pageable 사용 권장)
    List<AuditLog> findAllByOrderByIdAsc();
}
```

---

## [2026-04-30 10:10:26] `D:\develop\bangsan\backend\src\main\java\com\securescope\audit\AuditService.java`

`AuditService.java`에 대한 코드 리뷰 결과입니다. 해시 체인을 이용한 무결성 검증 로직에서 보완이 필요한 몇 가지 핵심 사항을 정리했습니다.

### 1. 주요 버그 및 보안 결함
*   **동시성 이슈 (Race Condition):** 
    *   `append` 메서드에서 `findLatest()`를 호출한 뒤 새로운 로그를 저장하기까지의 과정이 원자적(Atomic)이지 않습니다. 
    *   동시에 여러 알람이 발생할 경우, 두 스레드가 동일한 `prevHash`를 읽어 동일한 순번의 체인을 생성하려고 시도하여 체인이 깨지거나 데이터 정합성이 어긋날 수 있습니다.
    *   **해결책:** `findLatest` 시 비관적 락(`SELECT ... FOR UPDATE`)을 사용하거나, DB 수준에서 순차성을 보장할 수 있는 메커니즘이 필요합니다.
*   **검증 로직(verify)의 불완전성:**
    *   현재 `verify`는 각 레코드 내부의 `data + prevHash == currentHash`만 확인합니다. 
    *   **정작 중요한 "현재 레코드의 `prevHash`가 이전 레코드의 `currentHash`와 일치하는가"에 대한 연결 고리 검증이 누락**되어 있습니다. 이대로라면 중간의 레코드를 통째로 갈아치워도 개별 레코드의 해시만 맞으면 검증을 통과하게 됩니다.
    *   **해결책:** 루프를 돌 때 이전 루프의 `currentHash`를 저장해두고 현재 루프의 `prevHash`와 비교하는 로직을 추가해야 합니다.

### 2. 코드 품질 및 성능 개선
*   **메모리 부족(OOM) 위험:** 
    *   `auditLogRepository.findAllOrdered()`는 모든 로그를 한 번에 메모리에 올립니다. 로그가 수만 건 이상 쌓이면 서버가 다운될 수 있습니다.
    *   **해결책:** `Stream<AuditLog>`를 반환받아 처리하거나, 페이징(Chunk) 단위로 읽어서 검증해야 합니다.
*   **ObjectMapper 정적 선언:**
    *   `private static final ObjectMapper MAPPER`를 직접 생성하고 있습니다. 스프링 환경에서는 설정이 공유되도록 `ObjectMapper`를 의존성 주입(DI)받아 사용하는 것이 관례이며 테스트 용이성 측면에서도 좋습니다.
*   **SHA-256 유틸리티화:**
    *   `MessageDigest` 생성 및 헥사 변환 로직은 별도의 `CryptoUtils` 등으로 분리하여 재사용성을 높이고 코드를 깔끔하게 유지할 수 있습니다.

### 3. 기타 제언
*   **트랜잭션 분리:** `onAlertCreated` 이벤트 리스너에서 `@Transactional`을 사용 중인데, 만약 감사 로그(Audit) 저장이 실패했을 때 원본 비즈니스 로직(알람 생성)까지 롤백할 것인지, 아니면 감사 로그 실패는 별도로 처리할 것인지 정책 결정이 필요합니다. (보안 요건에 따라 다름)
*   **데이터 직렬화 고정:** `AlertSnapshot`을 이용해 직렬화하는 것은 좋으나, `ObjectMapper`의 설정(필드 순서 등)이 바뀌면 해시값이 달라질 수 있습니다. `@JsonPropertyOrder` 등을 통해 필드 순서를 명시적으로 고정하는 것이 안전합니다.

---

## [2026-04-30 10:10:58] `D:\develop\bangsan\backend\src\main\java\com\securescope\audit\AuditController.java`

`AuditController.java` 코드에 대한 리뷰 결과입니다.

### 1. 주요 리뷰 사항

*   **엔드포인트 명확성**: `/verify`라는 경로명이 무엇을 검증하는지 다소 모호합니다. 감사 로그의 무결성 검증(Integrity Check)이라면 `/verify-integrity`와 같이 더 명확한 이름이 권장됩니다.
*   **보안 고려**: 감사(Audit) 데이터는 보안상 매우 민감합니다. 현재 코드에는 권한 확인 로직이 보이지 않으므로, Spring Security 등을 통해 관리자 권한(`ROLE_ADMIN`)을 가진 사용자만 접근할 수 있도록 제한되어 있는지 확인이 필요합니다.
*   **DTO 분리**: `AuditService.VerifyResult` 내부 클래스를 직접 반환하고 있습니다. 서비스 계층의 내부 구조가 외부 API에 노출되는 것을 방지하기 위해 별도의 Response DTO를 정의하는 것이 유지보수 측면에서 유리합니다.

### 2. 코드 품질 및 개선 제안

*   **HTTP 메서드 선택**: 검증 작업이 단순히 조회(Read-only)라면 `GET`이 적절하지만, 검증 과정에서 상태를 변경하거나 서버 자원을 많이 소모하는 무거운 작업이라면 상황에 따라 `POST` 고려가 필요할 수 있습니다.
*   **비동기 처리 검토**: 만약 `auditService.verify()`가 전체 감사 로그를 전수 조사하는 등 시간이 오래 걸리는 작업이라면, 사용자의 타임아웃을 방지하기 위해 비동기 처리나 배치 작업으로 전환하는 것이 좋습니다.

### 3. 총평
전반적으로 Spring Boot의 표준적인 패턴을 잘 따르고 있어 깔끔합니다. 다만, **인가(Authorization) 정책**과 **API의 목적을 드러내는 명명 규칙**을 보완하면 더욱 견고한 코드가 될 것입니다.

---

