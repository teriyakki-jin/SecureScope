package com.securescope.detection.rule;

import com.securescope.detection.*;
import com.securescope.event.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 허용 시간대(start~end, 시 기준) 밖에서 이벤트 발생 시 탐지.
 */
@Component
@RequiredArgsConstructor
public class AfterHoursRule implements DetectionRule {

    private final DetectionProperties props;

    @Override
    public Optional<DetectionAlert> evaluate(SecurityEvent event) {
        int hour = ZonedDateTime.ofInstant(event.getOccurredAt(), ZoneId.of("Asia/Seoul"))
                .getHour();

        int start = props.allowedHours().start();
        int end   = props.allowedHours().end();
        boolean withinAllowed = hour >= start && hour < end;

        if (!withinAllowed) {
            return Optional.of(DetectionAlert.of(
                    AlertType.ALERT_AFTER_HOURS,
                    Severity.MED,
                    event.getSourceIp(),
                    "Access at %02d:xx KST — allowed window %02d:00-%02d:00".formatted(hour, start, end),
                    event
            ));
        }
        return Optional.empty();
    }
}
