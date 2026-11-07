package com.accuenergy.octopus.agent.interfaces;

import com.accuenergy.octopus.agent.application.GovernedDeviceIntegrationService;
import com.accuenergy.octopus.ai.spi.AiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-scoped review-only endpoint. No operation in this controller mutates platform state. */
@RestController
@RequestMapping("/api/v1/ai/device-integration-drafts")
public final class DeviceIntegrationController {
    private final GovernedDeviceIntegrationService service;
    private final ObjectMapper json;

    public DeviceIntegrationController(GovernedDeviceIntegrationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ai:integration:draft') or hasAuthority('PERM_platform:all')"
            + " or hasAuthority('SCOPE_ai:integration:draft')")
    public CompletionStage<DraftResponse> draft(JwtAuthenticationToken authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
            @Valid @RequestBody DraftRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID correlationId = correlationId(correlationHeader);
        return service.draft(new GovernedDeviceIntegrationService.DraftCommand(tenantId,
                actorId(authentication), correlationId, request.vendor(), request.protocol(),
                request.samplePayload(), request.requirements(), request.installedPlugins()))
                .thenApply(this::response);
    }

    private DraftResponse response(AiResponse response) {
        try {
            return new DraftResponse(response.correlationId(), response.providerId(), response.model(),
                    json.readTree(response.output()), response.usage().inputTokens(),
                    response.usage().outputTokens(), response.completedAt(), true);
        } catch (JsonProcessingException impossibleAfterValidation) {
            throw new IllegalStateException("Validated AI proposal could not be decoded", impossibleAfterValidation);
        }
    }

    private static UUID tenantId(JwtAuthenticationToken authentication) {
        String value = authentication.getToken().getClaimAsString("tenant_id");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A tenant-scoped token is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("tenant_id claim is invalid", invalid);
        }
    }

    private static UUID correlationId(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("X-Correlation-Id must be a UUID", invalid);
        }
    }

    private static String actorId(JwtAuthenticationToken authentication) {
        String subject = authentication.getToken().getSubject();
        if (subject == null || !subject.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}")) {
            throw new IllegalArgumentException("JWT subject is invalid");
        }
        return subject;
    }

    public record DraftRequest(@NotBlank @Size(max = 128) String vendor,
            @NotBlank @Size(max = 128) String protocol,
            @NotBlank @Size(max = 65536) String samplePayload,
            @NotBlank @Size(max = 8192) String requirements,
            @NotEmpty @Size(max = 64)
            List<@Pattern(regexp = "[a-z][a-z0-9._-]{0,63}") String> installedPlugins) { }

    public record DraftResponse(UUID correlationId, String providerId, String model, JsonNode proposal,
            long inputTokens, long outputTokens, Instant completedAt, boolean reviewRequired) { }
}
