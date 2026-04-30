package com.securescope.detection.rule;

import com.securescope.common.RedisKeyPrefix;
import com.securescope.detection.*;
import com.securescope.event.EventType;
import com.securescope.event.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 동일 IP 가 windowSeconds 내에 threshold 개 이상의 서로 다른 포트에 접근 시 탐지.
 * Redis SET(SADD) + SCARD + TTL 로 구현.
 */
@Component
@RequiredArgsConstructor
public class PortScanRule implements DetectionRule {

    private final StringRedisTemplate redis;
    private final DetectionProperties props;

    @Override
    public Optional<DetectionAlert> evaluate(SecurityEvent event) {
        if (event.getEventType() != EventType.PORT_SCAN || event.getTargetPort() == null) {
            return Optional.empty();
        }

        String key       = RedisKeyPrefix.PORT_SCAN + event.getSourceIp();
        int    threshold = props.portScan().threshold();
        int    window    = props.portScan().windowSeconds();

        Boolean isNew = redis.opsForSet().add(key, String.valueOf(event.getTargetPort())) != null
                && redis.getExpire(key) < 0;
        if (isNew) {
            redis.expire(key, window, TimeUnit.SECONDS);
        }

        Long portCount = redis.opsForSet().size(key);
        if (portCount != null && portCount >= threshold) {
            redis.delete(key);
            return Optional.of(DetectionAlert.of(
                    AlertType.ALERT_PORT_SCAN,
                    Severity.HIGH,
                    event.getSourceIp(),
                    "Distinct ports accessed: %d in %ds window".formatted(portCount, window),
                    event
            ));
        }
        return Optional.empty();
    }
}
