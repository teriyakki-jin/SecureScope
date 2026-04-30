package com.securescope.audit;

import com.securescope.detection.AlertType;
import com.securescope.detection.DetectionAlert;
import com.securescope.detection.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditLogRepository repository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        repository   = mock(AuditLogRepository.class);
        auditService = new AuditService(repository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private DetectionAlert makeAlert(Long id) throws Exception {
        DetectionAlert alert = DetectionAlert.of(
                AlertType.ALERT_BRUTE_FORCE, Severity.HIGH,
                "1.2.3.4", "Test detail", null
        );
        // 리플렉션으로 id 설정 (테스트 전용)
        Field idField = DetectionAlert.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(alert, id);
        Field tsField = DetectionAlert.class.getDeclaredField("detectedAt");
        tsField.setAccessible(true);
        tsField.set(alert, Instant.now());
        return alert;
    }

    @Test
    @DisplayName("첫 번째 레코드의 prevHash 는 '0' * 64 이다")
    void firstRecordPrevHashIsZeros() throws Exception {
        when(repository.findLatest()).thenReturn(Optional.empty());

        AuditLog log = auditService.append(makeAlert(1L));

        assertThat(log.getPrevHash()).isEqualTo("0".repeat(64));
        assertThat(log.getCurrentHash()).hasSize(64);
    }

    @Test
    @DisplayName("두 번째 레코드의 prevHash 는 첫 레코드의 currentHash 이다")
    void secondRecordPrevHashEqualsFirstCurrentHash() throws Exception {
        when(repository.findLatest()).thenReturn(Optional.empty());
        AuditLog first = auditService.append(makeAlert(1L));

        // 첫 번째 레코드가 저장됐다고 가정하고 findLatest mock 업데이트
        when(repository.findLatest()).thenReturn(Optional.of(first));
        AuditLog second = auditService.append(makeAlert(2L));

        assertThat(second.getPrevHash()).isEqualTo(first.getCurrentHash());
    }

    @Test
    @DisplayName("체인 무결성이 유효하면 verify() 가 true 를 반환한다")
    void verifyReturnsTrueForValidChain() throws Exception {
        // 실제 해시 계산으로 정합한 체인 구성
        when(repository.findLatest()).thenReturn(Optional.empty());
        AuditLog first = auditService.append(makeAlert(1L));

        when(repository.findLatest()).thenReturn(Optional.of(first));
        AuditLog second = auditService.append(makeAlert(2L));

        when(repository.findAllOrdered()).thenReturn(List.of(first, second));

        AuditService.VerifyResult result = auditService.verify();
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("prevHash 연결이 끊어지면 verify() 가 false 를 반환한다 (C4 chain-linkage fix)")
    void verifyReturnsFalseWhenChainLinkageBroken() throws Exception {
        // first 레코드를 정상 생성
        when(repository.findLatest()).thenReturn(Optional.empty());
        AuditLog first = auditService.append(makeAlert(1L));

        when(repository.findLatest()).thenReturn(Optional.of(first));
        AuditLog second = auditService.append(makeAlert(2L));

        // second 의 prevHash 를 잘못된 값으로 교체 — 연결 끊김 시뮬레이션
        Field prevField = AuditLog.class.getDeclaredField("prevHash");
        prevField.setAccessible(true);
        prevField.set(second, "b".repeat(64)); // first.currentHash 와 다른 값

        when(repository.findAllOrdered()).thenReturn(List.of(first, second));

        AuditService.VerifyResult result = auditService.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("Chain linkage broken");
    }

    @Test
    @DisplayName("해시가 변조되면 verify() 가 false 를 반환한다")
    void verifyReturnsFalseWhenTampered() throws Exception {
        when(repository.findLatest()).thenReturn(Optional.empty());
        AuditLog log = auditService.append(makeAlert(1L));

        // currentHash 를 임의 값으로 변조
        Field hashField = AuditLog.class.getDeclaredField("currentHash");
        hashField.setAccessible(true);
        hashField.set(log, "a".repeat(64)); // 변조

        when(repository.findAllOrdered()).thenReturn(List.of(log));

        AuditService.VerifyResult result = auditService.verify();
        assertThat(result.valid()).isFalse();
    }
}
