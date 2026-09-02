package com.accuenergy.octopus.control.domain.telemetry;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical device telemetry topic: octopus/{tenantId}/devices/{deviceId}/telemetry. */
public record DeviceTelemetryTopic(UUID tenantId, UUID deviceId) {
    private static final Pattern PATTERN = Pattern.compile(
            "^octopus/([0-9a-fA-F-]{36})/devices/([0-9a-fA-F-]{36})/telemetry$");

    public static DeviceTelemetryTopic parse(String topic) {
        Matcher matcher = PATTERN.matcher(topic == null ? "" : topic);
        if (!matcher.matches()) throw new IllegalArgumentException("Unsupported telemetry topic");
        try {
            return new DeviceTelemetryTopic(UUID.fromString(matcher.group(1)), UUID.fromString(matcher.group(2)));
        } catch (IllegalArgumentException malformedUuid) {
            throw new IllegalArgumentException("Telemetry topic contains malformed identity", malformedUuid);
        }
    }
}
