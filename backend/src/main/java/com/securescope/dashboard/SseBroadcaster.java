package com.securescope.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.securescope.detection.DetectionAlertCreatedEvent;
import com.securescope.detection.DetectionAlertResponse;
import com.securescope.event.SecurityEventCreatedEvent;
import com.securescope.event.SecurityEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 모든 SSE 구독자에게 이벤트/알림을 브로드캐스트.
 * CopyOnWriteArrayList 로 동시 접근 안전성 확보.
 */
@Component
@Slf4j
public class SseBroadcaster {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5분
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(e -> emitters.remove(emitter));
        log.debug("SSE subscriber added. total={}", emitters.size());
        return emitter;
    }

    /**
     * @Async: SSE 브로드캐스트를 Spring 공유 스레드풀에서 실행.
     * ingest() 요청 스레드를 블로킹하지 않으며,
     * 느린 SSE 구독자가 전체 처리량에 영향을 주지 않음.
     */
    @Async
    @EventListener
    public void onSecurityEvent(SecurityEventCreatedEvent domainEvent) {
        SsePayload payload = SsePayload.event(
                SecurityEventResponse.from(domainEvent.securityEvent()));
        broadcast(payload);
    }

    @Async
    @EventListener
    public void onAlertCreated(DetectionAlertCreatedEvent domainEvent) {
        SsePayload payload = SsePayload.alert(
                DetectionAlertResponse.from(domainEvent.alert()));
        broadcast(payload);
    }

    private void broadcast(SsePayload payload) {
        String json = toJson(payload);
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(payload.type().toLowerCase())
                        .data(json));
            } catch (IOException e) {
                emitter.completeWithError(e);
                emitters.remove(emitter);
            }
        });
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE serialization failed", e);
        }
    }
}
