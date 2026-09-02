package com.accuenergy.octopus.mgmt.interfaces.operations;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.SourcePosition;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayService;
import com.accuenergy.octopus.mgmt.domain.operations.DltReplayJob;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/kafka-dlt/replays")
public final class DltReplayController {
    private final DltReplayService service;

    public DltReplayController(DltReplayService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_platform:all')")
    public ReplayResponse replay(Authentication authentication, @Valid @RequestBody ReplayRequest request) {
        DltReplayJob job = service.replay(principal(authentication),
                new SourcePosition(request.dltTopic(), request.partition(), request.offset()), request.reason());
        return ReplayResponse.from(job);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAuthority('PERM_platform:all')")
    public ReplayResponse get(Authentication authentication, @PathVariable UUID jobId) {
        return ReplayResponse.from(service.get(principal(authentication), jobId));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record ReplayRequest(@NotBlank @Size(max = 255) String dltTopic,
                                @Min(0) int partition,
                                @Min(0) long offset,
                                @NotBlank @Size(max = 500) String reason) { }

    public record ReplayResponse(UUID id, UUID actorAccountId, String sourceDltTopic,
                                 int sourcePartition, long sourceOffset, Instant sourceTimestamp,
                                 String sourceKeySha256, int payloadSize, String destinationTopic,
                                 int replayAttempt, String replayRoot, String reason,
                                 DltReplayJob.Status status, Integer destinationPartition,
                                 Long destinationOffset, Instant destinationTimestamp,
                                 String failureReason, Instant createdAt, Instant completedAt) {
        static ReplayResponse from(DltReplayJob job) {
            return new ReplayResponse(job.id(), job.actorAccountId(), job.sourceDltTopic(),
                    job.sourcePartition(), job.sourceOffset(), job.sourceTimestamp(), job.sourceKeySha256(),
                    job.payloadSize(), job.destinationTopic(), job.replayAttempt(), job.replayRoot(), job.reason(),
                    job.status(), job.destinationPartition().orElse(null), job.destinationOffset().orElse(null),
                    job.destinationTimestamp().orElse(null), job.failureReason().orElse(null), job.createdAt(),
                    job.completedAt().orElse(null));
        }
    }
}
