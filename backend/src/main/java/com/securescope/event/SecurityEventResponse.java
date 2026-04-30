package com.securescope.event;

import java.time.Instant;

public record SecurityEventResponse(
        Long id,
        EventType eventType,
        String sourceIp,
        String macAddress,
        Integer targetPort,
        String userId,
        Instant occurredAt
) {
    public static SecurityEventResponse from(SecurityEvent e) {
        return new SecurityEventResponse(
                e.getId(), e.getEventType(), e.getSourceIp(),
                e.getMacAddress(), e.getTargetPort(), e.getUserId(),
                e.getOccurredAt()
        );
    }
}
