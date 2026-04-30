package com.securescope.detection;

import com.securescope.detection.rule.AfterHoursRule;
import com.securescope.event.EventType;
import com.securescope.event.SecurityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AfterHoursRuleTest {

    private AfterHoursRule rule;

    private static final DetectionProperties PROPS = new DetectionProperties(
            new DetectionProperties.BruteForce(5, 60),
            new DetectionProperties.PortScan(10, 10),
            new DetectionProperties.AllowedHours(9, 18)
    );

    @BeforeEach
    void setUp() {
        rule = new AfterHoursRule(PROPS);
    }

    private SecurityEvent eventAt(int kstHour) {
        Instant ts = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                .withHour(kstHour).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
        return SecurityEvent.of(EventType.LOGIN_SUCCESS, "1.2.3.4",
                null, null, "user", ts);
    }

    @ParameterizedTest(name = "KST {0}시는 허용 시간대 내 → 알림 없음")
    @ValueSource(ints = {9, 10, 12, 17})
    @DisplayName("허용 시간대 이벤트는 탐지하지 않는다")
    void shouldNotAlertWithinAllowedHours(int hour) {
        Optional<DetectionAlert> result = rule.evaluate(eventAt(hour));
        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "KST {0}시는 허용 시간대 밖 → ALERT_AFTER_HOURS")
    @ValueSource(ints = {0, 2, 6, 8, 18, 22})
    @DisplayName("허용 시간대 밖 이벤트는 ALERT_AFTER_HOURS 를 반환한다")
    void shouldAlertOutsideAllowedHours(int hour) {
        Optional<DetectionAlert> result = rule.evaluate(eventAt(hour));
        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.ALERT_AFTER_HOURS);
        assertThat(result.get().getSeverity()).isEqualTo(Severity.MED);
    }
}
