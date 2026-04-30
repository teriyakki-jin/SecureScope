package com.securescope.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.securescope.detection.DetectionAlert;
import com.securescope.detection.DetectionAlertCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW,
                   isolation   = Isolation.SERIALIZABLE)
    public void onAlertCreated(DetectionAlertCreatedEvent event) {
        append(event.alert());
    }

    /**
     * 탐지 이벤트를 해시체인에 추가.
     * prevHash = 직전 레코드의 currentHash (없으면 "0" * 64)
     * currentHash = SHA-256(data + prevHash)
     *
     * prev_hash 에 UNIQUE 제약(V5 마이그레이션)이 있으므로
     * 동시 호출 시 하나만 성공하고 나머지는 DataIntegrityViolationException.
     * SERIALIZABLE isolation 으로 SELECT + INSERT 사이 phantom 방지.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
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
     * 두 가지를 모두 확인:
     * 1) 각 레코드의 currentHash = SHA-256(data + prevHash) — 개별 해시 검증
     * 2) row[i].prevHash == row[i-1].currentHash — 연결 무결성 검증
     */
    @Transactional(readOnly = true)
    public VerifyResult verify() {
        List<AuditLog> logs = auditLogRepository.findAllOrdered();
        String expectedPrev = "0".repeat(64);

        for (AuditLog log : logs) {
            // (2) 연결 무결성: 이 레코드의 prevHash 가 직전 레코드의 currentHash 와 일치해야 함
            if (!log.getPrevHash().equals(expectedPrev)) {
                return new VerifyResult(false, log.getId(),
                        "Chain linkage broken at id=%d: expected prevHash=%s but was %s"
                                .formatted(log.getId(), expectedPrev, log.getPrevHash()));
            }
            // (1) 개별 해시 검증
            String expected = sha256(log.getData() + log.getPrevHash());
            if (!expected.equals(log.getCurrentHash())) {
                return new VerifyResult(false, log.getId(),
                        "Hash mismatch at id=%d".formatted(log.getId()));
            }
            expectedPrev = log.getCurrentHash();
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
