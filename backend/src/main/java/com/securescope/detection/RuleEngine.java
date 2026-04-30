package com.securescope.detection;

import com.securescope.event.SecurityEvent;
import com.securescope.event.SecurityEventCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이벤트 수집 후 모든 룰을 순차 평가.
 * Spring ApplicationEvent 로 EventService 와 느슨하게 결합.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final List<DetectionRule> rules;
    private final DetectionAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void onSecurityEvent(SecurityEventCreatedEvent domainEvent) {
        SecurityEvent event = domainEvent.securityEvent();

        rules.forEach(rule -> {
            try {
                rule.evaluate(event).ifPresent(alert -> {
                    DetectionAlert saved = alertRepository.save(alert);
                    log.info("[ALERT] {} | {} | ip={}", saved.getAlertType(),
                            saved.getSeverity(), saved.getSourceIp());
                    eventPublisher.publishEvent(new DetectionAlertCreatedEvent(saved));
                });
            } catch (Exception ex) {
                log.error("Rule evaluation error: rule={}, event={}",
                        rule.getClass().getSimpleName(), event.getId(), ex);
            }
        });
    }
}
