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

        redis.opsForValue().increment(RedisKeyPrefix.IP_EVENT_COUNT + saved.getSourceIp());

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
