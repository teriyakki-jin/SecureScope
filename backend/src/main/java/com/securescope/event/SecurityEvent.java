package com.securescope.event;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "security_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "source_ip", nullable = false, length = 45)
    private String sourceIp;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (occurredAt == null) occurredAt = Instant.now();
        createdAt = Instant.now();
    }

    public static SecurityEvent of(EventType eventType, String sourceIp,
                                   String macAddress, Integer targetPort,
                                   String userId, Instant occurredAt) {
        SecurityEvent e = new SecurityEvent();
        e.eventType = eventType;
        e.sourceIp = sourceIp;
        e.macAddress = macAddress;
        e.targetPort = targetPort;
        e.userId = userId;
        e.occurredAt = occurredAt;
        return e;
    }
}
