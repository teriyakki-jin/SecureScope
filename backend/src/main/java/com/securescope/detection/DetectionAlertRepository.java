package com.securescope.detection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetectionAlertRepository extends JpaRepository<DetectionAlert, Long> {

    Page<DetectionAlert> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<DetectionAlert> findBySeverityOrderByDetectedAtDesc(Severity severity, Pageable pageable);

    Page<DetectionAlert> findByAlertTypeOrderByDetectedAtDesc(AlertType alertType, Pageable pageable);

    Page<DetectionAlert> findBySourceIpOrderByDetectedAtDesc(String sourceIp, Pageable pageable);

    Optional<DetectionAlert> findTopByOrderByDetectedAtDesc();
}
