package com.securescope.event;

import com.securescope.common.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EventService {

    private final SecurityEventRepository eventRepository;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SecurityEventResponse ingest(CreateEventRequest req) {
        Instant occurredAt = req.occurredAt() != null ? req.occurredAt() : Instant.now();
        SecurityEvent event = SecurityEvent.of(
                req.eventType(), req.sourceIp(), req.macAddress(),
                req.targetPort(), req.userId(), occurredAt
        );
        SecurityEvent saved = eventRepository.save(event);

        String ipCountKey = RedisKeyPrefix.IP_EVENT_COUNT + saved.getSourceIp();
        redis.opsForValue().increment(ipCountKey);
        // TTL 이 없는 경우(신규 키) 24시간으로 설정 — 무한 누적 방지
        Long ttl = redis.getExpire(ipCountKey, TimeUnit.SECONDS);
        if (ttl != null && ttl < 0) {
            redis.expire(ipCountKey, 24, TimeUnit.HOURS);
        }

        eventPublisher.publishEvent(new SecurityEventCreatedEvent(saved));
        return SecurityEventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<SecurityEventResponse> findAll(int page, int size) {
        return eventRepository
                .findAllByOrderByOccurredAtDesc(PageRequest.of(page, size))
                .map(SecurityEventResponse::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> ipStats() {
        Map<String, Long> result = new LinkedHashMap<>();
        eventRepository.countBySourceIp()
                .forEach(row -> result.put((String) row[0], (Long) row[1]));
        return result;
    }
}
