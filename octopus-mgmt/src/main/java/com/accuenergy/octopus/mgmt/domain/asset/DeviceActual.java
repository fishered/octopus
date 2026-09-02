package com.accuenergy.octopus.mgmt.domain.asset;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Physical hardware commissioned against one logical Device aggregate. */
public final class DeviceActual {
    private final UUID id;
    private final UUID tenantId;
    private final UUID deviceId;
    private final String hardwareSerial;
    private final String manufacturer;
    private String firmwareVersion;
    private UUID certificateId;
    private ConnectivityStatus connectivityStatus;
    private Instant lastSeenAt;
    private final Instant commissionedAt;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private DeviceActual(UUID id, UUID tenantId, UUID deviceId, String hardwareSerial,
                         String manufacturer, String firmwareVersion, UUID certificateId,
                         ConnectivityStatus connectivityStatus, Instant lastSeenAt,
                         Instant commissionedAt, long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.hardwareSerial = requireText(hardwareSerial, "hardwareSerial", 200);
        this.manufacturer = normalizeOptional(manufacturer, "manufacturer", 200);
        this.firmwareVersion = normalizeOptional(firmwareVersion, "firmwareVersion", 100);
        this.certificateId = certificateId;
        this.connectivityStatus = Objects.requireNonNull(connectivityStatus, "connectivityStatus");
        this.lastSeenAt = lastSeenAt;
        this.commissionedAt = Objects.requireNonNull(commissionedAt, "commissionedAt");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (connectivityStatus == ConnectivityStatus.NEVER_SEEN && lastSeenAt != null) {
            throw new IllegalArgumentException("NEVER_SEEN hardware cannot have lastSeenAt");
        }
        if (connectivityStatus != ConnectivityStatus.NEVER_SEEN && lastSeenAt == null) {
            throw new IllegalArgumentException("Observed connectivity requires lastSeenAt");
        }
    }

    public static DeviceActual commission(UUID id, UUID tenantId, UUID deviceId, String hardwareSerial,
                                          String manufacturer, String firmwareVersion, UUID certificateId,
                                          Instant now) {
        return new DeviceActual(id, tenantId, deviceId, hardwareSerial, manufacturer, firmwareVersion,
                certificateId, ConnectivityStatus.NEVER_SEEN, null, now, 0, now, now);
    }

    public static DeviceActual restore(UUID id, UUID tenantId, UUID deviceId, String hardwareSerial,
                                       String manufacturer, String firmwareVersion, UUID certificateId,
                                       ConnectivityStatus connectivityStatus, Instant lastSeenAt,
                                       Instant commissionedAt, long version, Instant createdAt, Instant updatedAt) {
        return new DeviceActual(id, tenantId, deviceId, hardwareSerial, manufacturer, firmwareVersion,
                certificateId, connectivityStatus, lastSeenAt, commissionedAt, version, createdAt, updatedAt);
    }

    /** Ignores delayed connectivity events so an older packet cannot move lastSeenAt backwards. */
    public boolean recordConnectivity(ConnectivityStatus status, Instant observedAt, Instant now) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(now, "now");
        if (status == ConnectivityStatus.NEVER_SEEN) {
            throw new IllegalArgumentException("NEVER_SEEN is not an observed connectivity state");
        }
        if (lastSeenAt != null && observedAt.isBefore(lastSeenAt)) return false;
        connectivityStatus = status;
        lastSeenAt = observedAt;
        updatedAt = now;
        return true;
    }

    public void bindCertificate(UUID newCertificateId, Instant now) {
        certificateId = Objects.requireNonNull(newCertificateId, "newCertificateId");
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void updateFirmware(String newFirmwareVersion, Instant now) {
        firmwareVersion = requireText(newFirmwareVersion, "firmwareVersion", 100);
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID deviceId() { return deviceId; }
    public String hardwareSerial() { return hardwareSerial; }
    public Optional<String> manufacturer() { return Optional.ofNullable(manufacturer); }
    public Optional<String> firmwareVersion() { return Optional.ofNullable(firmwareVersion); }
    public Optional<UUID> certificateId() { return Optional.ofNullable(certificateId); }
    public ConnectivityStatus connectivityStatus() { return connectivityStatus; }
    public Optional<Instant> lastSeenAt() { return Optional.ofNullable(lastSeenAt); }
    public Instant commissionedAt() { return commissionedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public enum ConnectivityStatus { NEVER_SEEN, ONLINE, OFFLINE, DEGRADED }

    private static String normalizeOptional(String value, String name, int maximumLength) {
        return value == null || value.isBlank() ? null : requireText(value, name, maximumLength);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}
