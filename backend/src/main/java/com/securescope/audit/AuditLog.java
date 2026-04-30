package com.securescope.audit;

import com.securescope.detection.DetectionAlert;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id", nullable = false)
    private DetectionAlert alert;

    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "current_hash", nullable = false, length = 64, unique = true)
    private String currentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }

    public static AuditLog of(DetectionAlert alert, String data,
                               String prevHash, String currentHash) {
        AuditLog log = new AuditLog();
        log.alert       = alert;
        log.data        = data;
        log.prevHash    = prevHash;
        log.currentHash = currentHash;
        return log;
    }
}
