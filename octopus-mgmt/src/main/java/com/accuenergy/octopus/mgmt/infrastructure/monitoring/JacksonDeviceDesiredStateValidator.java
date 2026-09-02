package com.accuenergy.octopus.mgmt.infrastructure.monitoring;

import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredStateValidator;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition;
import com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring.DesiredStateModelMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class JacksonDeviceDesiredStateValidator implements DeviceDesiredStateValidator {
    private final DesiredStateModelMapper models;
    private final ObjectMapper objectMapper;

    public JacksonDeviceDesiredStateValidator(DesiredStateModelMapper models, ObjectMapper objectMapper) {
        this.models = models;
        this.objectMapper = objectMapper;
    }

    @Override
    public ValidatedState validate(UUID deviceId, String stateJson) {
        Long modelVersion = models.findPublishedModelVersion(deviceId);
        if (modelVersion == null) throw new IllegalArgumentException("Device has no active published thing model");
        Map<String, ParameterDefinition.DataType> writable = new LinkedHashMap<>();
        for (DesiredStateModelMapper.PropertyRow row : models.findWritableProperties(deviceId)) {
            if (writable.put(row.code(), ParameterDefinition.DataType.valueOf(row.dataType())) != null) {
                throw new IllegalStateException("Thing model contains duplicate writable property code " + row.code());
            }
        }
        if (writable.isEmpty()) throw new IllegalArgumentException("Thing model has no writable properties");
        try {
            JsonNode state = objectMapper.readTree(stateJson);
            if (state == null || !state.isObject() || state.size() == 0) {
                throw new IllegalArgumentException("Desired state must be a non-empty JSON object");
            }
            state.fieldNames().forEachRemaining(code -> validateProperty(code, state.get(code), writable));
            String canonical = objectMapper.writeValueAsString(state);
            if (canonical.getBytes(StandardCharsets.UTF_8).length > 61_440) {
                throw new IllegalArgumentException("Desired state exceeds 60 KiB");
            }
            return new ValidatedState(modelVersion, canonical);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("Desired state is invalid JSON", invalidJson);
        }
    }

    private static void validateProperty(String code, JsonNode value,
            Map<String, ParameterDefinition.DataType> writable) {
        ParameterDefinition.DataType type = writable.get(code);
        if (type == null) throw new IllegalArgumentException("Property is not writable: " + code);
        if (value == null || value.isNull()) throw new IllegalArgumentException("Desired property cannot be null: " + code);
        boolean valid = switch (type) {
            case DECIMAL -> value.isNumber();
            case INTEGER -> value.isIntegralNumber();
            case BOOLEAN -> value.isBoolean();
            case STRING -> value.isTextual();
        };
        if (!valid) throw new IllegalArgumentException("Desired property has wrong type: " + code);
    }
}
