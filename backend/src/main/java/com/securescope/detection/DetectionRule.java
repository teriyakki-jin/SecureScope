package com.securescope.detection;

import com.securescope.event.SecurityEvent;

import java.util.Optional;

/**
 * 탐지 룰 전략 인터페이스.
 * 각 구현체는 이벤트 하나를 받아 탐지 여부를 Optional 로 반환한다.
 */
public interface DetectionRule {
    Optional<DetectionAlert> evaluate(SecurityEvent event);
}
