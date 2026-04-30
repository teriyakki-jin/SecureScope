package com.securescope.detection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "securescope.detection")
public record DetectionProperties(
        BruteForce bruteForce,
        PortScan portScan,
        AllowedHours allowedHours
) {
    public record BruteForce(int threshold, int windowSeconds) {}
    public record PortScan(int threshold, int windowSeconds) {}
    public record AllowedHours(int start, int end) {}
}
