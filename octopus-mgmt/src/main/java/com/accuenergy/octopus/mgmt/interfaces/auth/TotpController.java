package com.accuenergy.octopus.mgmt.interfaces.auth;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.identity.TotpLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/totp")
public final class TotpController {
    private final TotpLifecycleService totp;

    public TotpController(TotpLifecycleService totp) {
        this.totp = totp;
    }

    @PostMapping("/enrollments")
    public ResponseEntity<EnrollmentResponse> startEnrollment(Authentication authentication,
            @Valid @RequestBody StartEnrollmentRequest request) {
        char[] currentPassword = request.currentPassword().toCharArray();
        try {
            var enrollment = totp.startEnrollment(principal(authentication),
                    new TotpLifecycleService.StartEnrollment(currentPassword));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                    new EnrollmentResponse(enrollment.factorId(), enrollment.secret(),
                            enrollment.otpauthUri(), enrollment.expiresAt()));
        } finally {
            Arrays.fill(currentPassword, '\0');
        }
    }

    @PostMapping("/enrollments/{factorId}/activate")
    public ResponseEntity<ActivationResponse> activate(Authentication authentication,
            @PathVariable UUID factorId, @Valid @RequestBody ActivateRequest request) {
        var activation = totp.activate(principal(authentication), factorId, request.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new ActivationResponse(activation.factorId(), activation.recoveryCodes(),
                        activation.sessionGeneration()));
    }

    @PostMapping("/remove")
    public ResponseEntity<ChangeResponse> remove(Authentication authentication,
            @Valid @RequestBody RemoveRequest request) {
        char[] currentPassword = request.currentPassword().toCharArray();
        try {
            var change = totp.remove(principal(authentication),
                    new TotpLifecycleService.RemoveTotp(currentPassword, request.code()));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new ChangeResponse(change.accountId(), change.sessionGeneration()));
        } finally {
            Arrays.fill(currentPassword, '\0');
        }
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record StartEnrollmentRequest(
            @NotBlank @Size(min = 12, max = 1024) String currentPassword) {
        @Override public String toString() { return "StartEnrollmentRequest[credentials=REDACTED]"; }
    }
    public record ActivateRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }
    public record RemoveRequest(@NotBlank @Size(min = 12, max = 1024) String currentPassword,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
        @Override public String toString() { return "RemoveRequest[credentials=REDACTED]"; }
    }
    public record EnrollmentResponse(UUID factorId, String secret, String otpauthUri, Instant expiresAt) {
        @Override public String toString() {
            return "EnrollmentResponse[factorId=" + factorId + ", credentials=REDACTED, expiresAt="
                    + expiresAt + "]";
        }
    }
    public record ActivationResponse(UUID factorId, List<String> recoveryCodes, long sessionGeneration) {
        @Override public String toString() {
            return "ActivationResponse[factorId=" + factorId + ", recoveryCodes=REDACTED, sessionGeneration="
                    + sessionGeneration + "]";
        }
    }
    public record ChangeResponse(UUID accountId, long sessionGeneration) { }
}
