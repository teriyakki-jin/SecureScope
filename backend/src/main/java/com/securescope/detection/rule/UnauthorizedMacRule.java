package com.securescope.detection.rule;

import com.securescope.detection.*;
import com.securescope.event.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * MAC 주소가 whitelist 에 없을 경우 탐지.
 * macAddress 가 null 이면 평가 대상이 아님.
 */
@Component
@RequiredArgsConstructor
public class UnauthorizedMacRule implements DetectionRule {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<DetectionAlert> evaluate(SecurityEvent event) {
        String mac = event.getMacAddress();
        if (mac == null || mac.isBlank()) {
            return Optional.empty();
        }

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mac_whitelist WHERE mac_address = ?",
                Integer.class, mac
        );

        if (count == null || count == 0) {
            return Optional.of(DetectionAlert.of(
                    AlertType.ALERT_UNAUTHORIZED_MAC,
                    Severity.HIGH,
                    event.getSourceIp(),
                    "Unregistered MAC address: " + mac,
                    event
            ));
        }
        return Optional.empty();
    }
}
