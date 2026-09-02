package com.accuenergy.octopus.control.domain.shadow;

import java.util.UUID;

/** Canonical device shadow report topic: octopus/{tenantId}/devices/{deviceId}/shadow/reported. */
public record DeviceShadowTopic(UUID tenantId, UUID deviceId) {
    public static boolean matches(String topic) {
        if (topic == null) return false;
        String[] parts = topic.split("/", -1);
        return parts.length == 6 && "octopus".equals(parts[0]) && "devices".equals(parts[2])
                && "shadow".equals(parts[4]) && "reported".equals(parts[5]);
    }

    public static DeviceShadowTopic parse(String topic) {
        if (!matches(topic)) throw new IllegalArgumentException("Unsupported shadow reported topic");
        String[] parts = topic.split("/", -1);
        try {
            return new DeviceShadowTopic(UUID.fromString(parts[1]), UUID.fromString(parts[3]));
        } catch (IllegalArgumentException malformedUuid) {
            throw new IllegalArgumentException("Shadow topic contains malformed identity", malformedUuid);
        }
    }
}
