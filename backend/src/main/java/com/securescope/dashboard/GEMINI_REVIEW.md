## [2026-04-30 10:11:29] `D:\develop\bangsan\backend\src\main\java\com\securescope\dashboard\SsePayload.java`

제시하신 `SsePayload` 코드에 대한 리뷰 결과입니다.

### 1. 개선 권장 사항 (코드 품질 및 타입 안정성)

*   **Enum 사용 권장**: `type` 필드가 `"EVENT"`, `"ALERT"`로 제한되어 있음에도 `String` 타입을 사용하고 있습니다. 오타 방지와 타입 안정성을 위해 별도의 `Enum`을 정의하거나, 기존에 정의된 `EventType` 등을 활용하는 것이 좋습니다.
*   **제네릭(Generics) 도입**: `data` 필드가 `Object`로 선언되어 있어 런타임에 타입 캐스팅 오류가 발생할 위험이 있습니다. `public record SsePayload<T>(String type, T data)`와 같이 제네릭을 사용하면 컴파일 타임에 타입 체크가 가능해집니다.
*   **Null 검증 추가**: `record`의 컴팩트 생성자(Compact Constructor)를 사용하여 `type`이나 `data`가 `null`로 들어오는 것을 방지하는 검증 로직을 추가하면 더 견고한 코드가 됩니다.

### 2. 수정 제안 코드

```java
package com.securescope.dashboard;

import java.util.Objects;

public record SsePayload<T>(SseType type, T data) {
    
    public enum SseType {
        EVENT, ALERT
    }

    public SsePayload {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
    }

    public static <T> SsePayload<T> event(T data) {
        return new SsePayload<>(SseType.EVENT, data);
    }

    public static <T> SsePayload<T> alert(T data) {
        return new SsePayload<>(SseType.ALERT, data);
    }
}
```

### 3. 총평
현재 코드는 간결하게 잘 작성되어 있으나, Java의 강력한 타입 시스템을 활용하기 위해 **Enum**과 **Generic** 도입을 적극 추천합니다. 이를 통해 오동작 가능성을 줄이고 유지보수성을 높일 수 있습니다.

---

## [2026-04-30 10:13:44] `D:\develop\bangsan\backend\src\main\java\com\securescope\dashboard\SseBroadcaster.java`

`SseBroadcaster.java` 코드에 대한 리뷰 결과입니다.

### 1. 주요 버그 및 위험 요소
*   **이벤트 발행 스레드 블로킹:** `@EventListener`는 기본적으로 동기(Synchronous) 방식으로 동작합니다. 구독자가 많거나 네트워크 지연이 발생하면 보안 이벤트나 알림을 저장하는 비즈니스 로직 스레드가 브로드캐스트가 끝날 때까지 대기하게 되어 시스템 성능이 저하될 수 있습니다.
*   **연결 초기 에러(503) 가능성:** 일부 브라우저나 프록시(Nginx 등) 환경에서는 연결 직후 데이터를 보내지 않으면 503 Service Unavailable 에러가 발생하거나 연결이 즉시 끊길 수 있습니다. 구독 시점에 "dummy" 데이터를 전송하는 것이 안전합니다.

### 2. 코드 품질 및 개선점
*   **수동 직렬화 불필요:** `SseEmitter.send()`는 객체를 직접 인자로 받을 수 있으며, Spring의 `HttpMessageConverter`가 자동으로 JSON으로 변환합니다. `toJson` 메서드와 `ObjectMapper`를 직접 관리할 필요가 없습니다.
*   **ObjectMapper 빈 주입:** 만약 수동 직렬화가 꼭 필요하더라도, `new ObjectMapper()`로 직접 생성하기보다는 Spring 컨텍스트의 `ObjectMapper`를 주입받아 사용하는 것이 전역 설정(날짜 포맷 등)을 따르는 데 유리합니다.
*   **CopyOnWriteArrayList 성능:** `CopyOnWriteArrayList`는 읽기에는 효율적이지만 수정(add/remove) 시 리스트 전체를 복사하므로, 구독/해제가 빈번한 환경에서는 오버헤드가 발생할 수 있습니다. (현재 규모에서는 큰 문제 없으나 참고 필요)

### 3. 권장 수정 방향
*   **비동기 처리:** `@EventListener`와 함께 `@Async`를 사용하거나, 브로드캐스트 전용 `Executor`를 별도로 두어 비즈니스 로직과 분리하십시오.
*   **더미 데이터 전송:** `subscribe()` 메서드 내에서 `emitter.send(SseEmitter.event().name("connect").data("connected"))`와 같은 초기화 이벤트를 전송하십시오.
*   **Resource 정리:** `onTimeout`과 `onCompletion`에서 `emitters.remove(emitter)`를 호출하는 것은 좋으나, `onTimeout` 시에는 `complete()`를 명시적으로 호출하여 리소스를 확실히 반환해야 합니다 (현재 코드에는 포함되어 있음).

### 4. 개선된 코드 예시 (핵심 부분)
```java
@Async // 별도 스레드에서 실행
@EventListener
public void onSecurityEvent(SecurityEventCreatedEvent domainEvent) {
    broadcast(SsePayload.event(SecurityEventResponse.from(domainEvent.securityEvent())));
}

private void broadcast(SsePayload payload) {
    // List 복사본으로 순회하여 ConcurrentModification 방지
    for (SseEmitter emitter : emitters) {
        try {
            emitter.send(SseEmitter.event()
                    .name(payload.type().toLowerCase())
                    .data(payload.data())); // 직접 객체 전달 가능
        } catch (IOException e) {
            emitter.completeWithError(e);
            emitters.remove(emitter);
        }
    }
}
```

---

## [2026-04-30 10:14:14] `D:\develop\bangsan\backend\src\main\java\com\securescope\dashboard\DashboardController.java`

`DashboardController.java` 코드 리뷰 결과입니다.

### 1. 버그 및 안정성
*   **리소스 누수 위험**: `SseEmitter`는 타임아웃이나 클라이언트 종료 시 적절한 처리가 필요합니다. `sseBroadcaster.subscribe()` 내부에서 `onTimeout`, `onCompletion`, `onError` 콜백을 설정하여 `Emitters` 목록에서 제거하는 로직이 반드시 포함되어야 합니다.
*   **예외 처리 부족**: 구독 과정에서 발생할 수 있는 예외(예: 서버 부하로 인한 거부)에 대한 처리가 누락되어 있습니다.

### 2. 코드 품질 및 설계
*   **엔드포인트 경로 일관성**: 현재 `@RequestMapping("/api/events")`를 사용 중인데, `com.securescope.event` 패키지에 `EventController`가 별도로 존재합니다. 대시보드 전용 스트림이라면 `/api/dashboard/stream` 등으로 경로를 변경하여 관심사를 분리하는 것이 좋습니다.
*   **응답 헤더**: SSE의 경우 브라우저 캐싱으로 인해 실시간 데이터 수신에 문제가 생길 수 있습니다. 응답 헤더에 `Cache-Control: no-cache`가 보장되는지 확인이 필요합니다. (Spring Boot 기본 설정으로 처리되기도 하지만 명시적 확인 권장)

### 3. 개선 제안
*   **Swagger/OpenAPI 문서화**: API 문서 자동화를 위해 `@Operation` 등의 어노테이션을 추가하여 SSE 스트림임을 명시하면 협업에 도움이 됩니다.
*   **연결 확인용 더미 데이터**: 클라이언트가 연결되었음을 즉시 알 수 있도록, `subscribe()` 호출 직후 연결 성공 메시지(예: `{"status": "connected"}`)를 한 번 발송하는 패턴을 권장합니다.

**요약**: 코드는 간결하지만 SSE 특성상 **서버 리소스 관리(타임아웃/종료 처리)**가 `SseBroadcaster` 내에 철저히 구현되어 있는지 확인이 가장 중요합니다.

---

