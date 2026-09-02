package com.accuenergy.octopus.mgmt.interfaces.control;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.control.DeviceCommandManagementService;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class DeviceCommandController {
    private final DeviceCommandManagementService commands;
    private final ObjectMapper objectMapper;

    public DeviceCommandController(DeviceCommandManagementService commands, ObjectMapper objectMapper) {
        this.commands = commands;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/devices/{deviceId}/commands")
    @PreAuthorize("hasAuthority('PERM_device:operate') or hasAuthority('PERM_platform:all')")
    public CommandResponse request(Authentication authentication, @PathVariable UUID deviceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CommandRequestBody request) {
        DeviceCommandRequest command = commands.request(principal(authentication), deviceId,
                new DeviceCommandManagementService.RequestCommand(idempotencyKey, request.operation(),
                        serializePayload(request.payload()), Duration.ofSeconds(request.ttlSeconds())));
        return response(command);
    }

    @GetMapping("/device-commands/{commandId}")
    @PreAuthorize("hasAuthority('PERM_device:operate') or hasAuthority('PERM_platform:all')")
    public CommandResponse get(Authentication authentication, @PathVariable UUID commandId) {
        return response(commands.get(principal(authentication), commandId));
    }

    private byte[] serializePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Command payload is invalid", failure);
        }
    }

    private static CommandResponse response(DeviceCommandRequest command) {
        return new CommandResponse(command.commandId(), command.deviceId(), command.operation(), command.status(),
                command.failureCode().orElse(null), command.requestedAt(), command.expiresAt(),
                command.dispatchedAt().orElse(null), command.acknowledgedAt().orElse(null),
                command.completedAt().orElse(null), command.version());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CommandRequestBody(@NotBlank String operation, @NotNull JsonNode payload,
                                     @Min(1) @Max(600) long ttlSeconds) { }
    public record CommandResponse(UUID commandId, UUID deviceId, String operation,
            DeviceCommandStatusChanged.Status status, String failureCode, Instant requestedAt,
            Instant expiresAt, Instant dispatchedAt, Instant acknowledgedAt, Instant completedAt,
            long version) { }
}
