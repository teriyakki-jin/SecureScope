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
 * 동일 IP에서 LOGIN_FAIL 이 windowSeconds 내에 threshold 이상 발생 시 탐지.
 * Redis INCR + EXPIRE 로 슬라이딩 윈도우 구현.
 */
@Component
@RequiredArgsConstructor
public class BruteForceRule implements DetectionRule {

    private final StringRedisTemplate redis;
    private final DetectionProperties props;

    @Override
    public Optional<DetectionAlert> evaluate(SecurityEvent event) {
        if (event.getEventType() != EventType.LOGIN_FAIL) {
            return Optional.empty();
        }

        String key    = RedisKeyPrefix.BRUTE_FORCE + event.getSourceIp();
        int threshold = props.bruteForce().threshold();
        int window    = props.bruteForce().windowSeconds();

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window, TimeUnit.SECONDS);
        }

        if (count != null && count >= threshold) {
            redis.delete(key); // 탐지 후 카운터 리셋
            return Optional.of(DetectionAlert.of(
                    AlertType.ALERT_BRUTE_FORCE,
                    Severity.HIGH,
                    event.getSourceIp(),
                    "Login failure count: %d in %ds window".formatted(count, window),
                    event
            ));
        }
        return Optional.empty();
    }
}
