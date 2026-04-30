package com.securescope.detection;

import java.time.Instant;

public record DetectionAlertResponse(
        Long id,
        AlertType alertType,
        Severity severity,
        String sourceIp,
        String detail,
        Long triggerEventId,
        Instant detectedAt
) {
    public static DetectionAlertResponse from(DetectionAlert a) {
        return new DetectionAlertResponse(
                a.getId(),
                a.getAlertType(),
                a.getSeverity(),
                a.getSourceIp(),
                a.getDetail(),
                a.getTriggerEvent() != null ? a.getTriggerEvent().getId() : null,
                a.getDetectedAt()
        );
    }
}
