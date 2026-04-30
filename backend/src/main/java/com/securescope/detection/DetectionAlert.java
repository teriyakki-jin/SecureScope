package com.securescope.detection;

import com.securescope.event.SecurityEvent;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "detection_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class DetectionAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "source_ip", nullable = false, length = 45)
    private String sourceIp;

    @Column(name = "detail")
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_event_id")
    private SecurityEvent triggerEvent;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @PrePersist
    private void prePersist() {
        detectedAt = Instant.now();
    }

    public static DetectionAlert of(AlertType alertType, Severity severity,
                                    String sourceIp, String detail,
                                    SecurityEvent triggerEvent) {
        DetectionAlert a = new DetectionAlert();
        a.alertType    = alertType;
        a.severity     = severity;
        a.sourceIp     = sourceIp;
        a.detail       = detail;
        a.triggerEvent = triggerEvent;
        return a;
    }
}
