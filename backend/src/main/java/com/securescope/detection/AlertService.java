package com.securescope.detection;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final DetectionAlertRepository alertRepository;

    @Transactional(readOnly = true)
    public Page<DetectionAlertResponse> findAll(String severity, String alertType,
                                                String sourceIp, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        if (severity != null && !severity.isBlank()) {
            return alertRepository
                    .findBySeverityOrderByDetectedAtDesc(Severity.valueOf(severity.toUpperCase()), pageable)
                    .map(DetectionAlertResponse::from);
        }
        if (alertType != null && !alertType.isBlank()) {
            return alertRepository
                    .findByAlertTypeOrderByDetectedAtDesc(AlertType.valueOf(alertType.toUpperCase()), pageable)
                    .map(DetectionAlertResponse::from);
        }
        if (sourceIp != null && !sourceIp.isBlank()) {
            return alertRepository
                    .findBySourceIpOrderByDetectedAtDesc(sourceIp, pageable)
                    .map(DetectionAlertResponse::from);
        }
        return alertRepository
                .findAllByOrderByDetectedAtDesc(pageable)
                .map(DetectionAlertResponse::from);
    }
}
