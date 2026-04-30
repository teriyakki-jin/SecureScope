package com.securescope.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record CreateEventRequest(
        @NotNull EventType eventType,
        @NotBlank
        @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
                 message = "sourceIp must be a valid IPv4 address")
        String sourceIp,
        @Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$",
                 message = "MAC address format: AA:BB:CC:DD:EE:FF")
        String macAddress,
        Integer targetPort,
        String userId,
        Instant occurredAt
) {}
