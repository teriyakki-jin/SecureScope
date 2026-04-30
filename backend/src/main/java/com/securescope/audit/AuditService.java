package com.securescope.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.securescope.detection.DetectionAlert;
import com.securescope.detection.DetectionAlertCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @EventListener
    @Transactional
    public void onAlertCreated(DetectionAlertCreatedEvent event) {
        append(event.alert());
    }

    /**
     * 탐지 이벤트를 해시체인에 추가.
     * prevHash = 직전 레코드의 currentHash (없으면 "0" * 64)
     * currentHash = SHA-256(data + prevHash)
     */
    @Transactional
    public AuditLog append(DetectionAlert alert) {
        String data    = serialize(alert);
        String prevHash = auditLogRepository.findLatest()
                .map(AuditLog::getCurrentHash)
                .orElse("0".repeat(64));
        String currentHash = sha256(data + prevHash);

        AuditLog auditLog = AuditLog.of(alert, data, prevHash, currentHash);
        return auditLogRepository.save(auditLog);
    }

    /**
     * 전체 체인 무결성 검증.
     * 각 레코드의 currentHash = SHA-256(data + prevHash) 인지 확인.
     */
    @Transactional(readOnly = true)
    public VerifyResult verify() {
        List<AuditLog> logs = auditLogRepository.findAllOrdered();
        for (AuditLog auditLog : logs) {
            String expected = sha256(auditLog.getData() + auditLog.getPrevHash());
            if (!expected.equals(auditLog.getCurrentHash())) {
                return new VerifyResult(false, auditLog.getId(),
                        "Hash mismatch at record id=" + auditLog.getId());
            }
        }
        return new VerifyResult(true, null, "Chain integrity OK. Records: " + logs.size());
    }

    private String serialize(DetectionAlert alert) {
        try {
            return MAPPER.writeValueAsString(new AlertSnapshot(
                    alert.getId(), alert.getAlertType().name(),
                    alert.getSeverity().name(), alert.getSourceIp(),
                    alert.getDetail(), alert.getDetectedAt()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize alert", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record VerifyResult(boolean valid, Long failedAtId, String message) {}

    private record AlertSnapshot(Long id, String alertType, String severity,
                                  String sourceIp, String detail,
                                  java.time.Instant detectedAt) {}
}
