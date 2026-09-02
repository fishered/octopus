package com.accuenergy.octopus.mgmt.domain.catalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable-after-publication device contract. Each database row represents one model version. */
public final class ThingModel {
    private final UUID id;
    private final Optional<UUID> tenantId;
    private final UUID deviceTypeId;
    private final String code;
    private final String displayName;
    private final long modelVersion;
    private final String schemaDocument;
    private final List<ParameterBinding> parameters;
    private Status status;
    private Instant publishedAt;
    private final Instant createdAt;

    private ThingModel(UUID id, Optional<UUID> tenantId, UUID deviceTypeId, String code,
                       String displayName, long modelVersion, String schemaDocument,
                       List<ParameterBinding> parameters, Status status, Instant publishedAt,
                       Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deviceTypeId = Objects.requireNonNull(deviceTypeId, "deviceTypeId");
        this.code = requireText(code, "code", 64);
        this.displayName = requireText(displayName, "displayName", 200);
        if (modelVersion < 1) throw new IllegalArgumentException("modelVersion must be positive");
        this.modelVersion = modelVersion;
        this.schemaDocument = requireText(schemaDocument, "schemaDocument", 1_000_000);
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        validateBindings(this.parameters);
        this.status = Objects.requireNonNull(status, "status");
        this.publishedAt = publishedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (status == Status.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("Published model requires publishedAt");
        }
        if (status == Status.DRAFT && publishedAt != null) {
            throw new IllegalArgumentException("Draft model cannot have publishedAt");
        }
    }

    public static ThingModel draft(UUID id, UUID tenantId, UUID deviceTypeId, String code,
                                   String displayName, long modelVersion, String schemaDocument,
                                   List<ParameterBinding> parameters, Instant now) {
        return new ThingModel(id, Optional.of(Objects.requireNonNull(tenantId, "tenantId")), deviceTypeId,
                code, displayName, modelVersion, schemaDocument, parameters, Status.DRAFT, null, now);
    }

    public static ThingModel restore(UUID id, UUID tenantId, UUID deviceTypeId, String code,
                                     String displayName, long modelVersion, String schemaDocument,
                                     List<ParameterBinding> parameters, Status status,
                                     Instant publishedAt, Instant createdAt) {
        return new ThingModel(id, Optional.ofNullable(tenantId), deviceTypeId, code, displayName,
                modelVersion, schemaDocument, parameters, status, publishedAt, createdAt);
    }

    public void publish(Instant now) {
        if (status != Status.DRAFT) throw new IllegalStateException("Only draft models can be published");
        if (parameters.isEmpty()) throw new IllegalStateException("A published model requires parameters");
        status = Status.PUBLISHED;
        publishedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() { return id; }
    public Optional<UUID> tenantId() { return tenantId; }
    public UUID deviceTypeId() { return deviceTypeId; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public long modelVersion() { return modelVersion; }
    public String schemaDocument() { return schemaDocument; }
    public List<ParameterBinding> parameters() { return parameters; }
    public Status status() { return status; }
    public Optional<Instant> publishedAt() { return Optional.ofNullable(publishedAt); }
    public Instant createdAt() { return createdAt; }

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    public record ParameterBinding(UUID parameterId, UUID unitId, boolean required, int sortOrder,
            AccessMode accessMode) {
        public ParameterBinding(UUID parameterId, UUID unitId, boolean required, int sortOrder) {
            this(parameterId, unitId, required, sortOrder, AccessMode.READ_ONLY);
        }
        public ParameterBinding {
            Objects.requireNonNull(parameterId, "parameterId");
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(accessMode, "accessMode");
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must be non-negative");
        }
    }

    public enum AccessMode {
        READ_ONLY, WRITE_ONLY, READ_WRITE;
        public boolean writable() { return this == WRITE_ONLY || this == READ_WRITE; }
    }

    private static void validateBindings(List<ParameterBinding> bindings) {
        Set<UUID> parameters = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (ParameterBinding binding : bindings) {
            Objects.requireNonNull(binding, "parameter binding");
            if (!parameters.add(binding.parameterId())) {
                throw new IllegalArgumentException("Duplicate parameter binding: " + binding.parameterId());
            }
            if (!sortOrders.add(binding.sortOrder())) {
                throw new IllegalArgumentException("Duplicate parameter sort order: " + binding.sortOrder());
            }
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}
