package com.securescope.detection;

import com.securescope.detection.rule.BruteForceRule;
import com.securescope.event.EventType;
import com.securescope.event.SecurityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BruteForceRuleTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private BruteForceRule rule;

    private static final DetectionProperties PROPS = new DetectionProperties(
            new DetectionProperties.BruteForce(5, 60),
            new DetectionProperties.PortScan(10, 10),
            new DetectionProperties.AllowedHours(9, 18)
    );

    @BeforeEach
    void setUp() {
        redis    = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        rule = new BruteForceRule(redis, PROPS);
    }

    private SecurityEvent loginFailEvent(String ip) {
        return SecurityEvent.of(EventType.LOGIN_FAIL, ip, null, null, "admin", Instant.now());
    }

    private SecurityEvent loginSuccessEvent(String ip) {
        return SecurityEvent.of(EventType.LOGIN_SUCCESS, ip, null, null, "admin", Instant.now());
    }

    @Test
    @DisplayName("LOGIN_FAIL 이 아닌 이벤트는 평가하지 않는다")
    void shouldIgnoreNonLoginFailEvent() {
        Optional<DetectionAlert> result = rule.evaluate(loginSuccessEvent("1.2.3.4"));
        assertThat(result).isEmpty();
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("임계값 미만이면 알림이 발생하지 않는다")
    void shouldNotAlertBelowThreshold() {
        when(valueOps.increment(anyString())).thenReturn(3L);
        Optional<DetectionAlert> result = rule.evaluate(loginFailEvent("1.2.3.4"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("임계값 이상이면 ALERT_BRUTE_FORCE 가 반환된다")
    void shouldAlertAtThreshold() {
        when(valueOps.increment(anyString())).thenReturn(5L);
        Optional<DetectionAlert> result = rule.evaluate(loginFailEvent("1.2.3.4"));

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.ALERT_BRUTE_FORCE);
        assertThat(result.get().getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.get().getSourceIp()).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("탐지 후 Redis 카운터를 리셋한다")
    void shouldResetCounterAfterDetection() {
        when(valueOps.increment(anyString())).thenReturn(5L);
        rule.evaluate(loginFailEvent("1.2.3.4"));
        verify(redis).delete(contains("1.2.3.4"));
    }
}
