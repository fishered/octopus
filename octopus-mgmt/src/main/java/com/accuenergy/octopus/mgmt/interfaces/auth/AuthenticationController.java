package com.accuenergy.octopus.mgmt.interfaces.auth;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.identity.AuthenticationService;
import com.accuenergy.octopus.mgmt.application.identity.ForceLogoutService;
import com.accuenergy.octopus.mgmt.application.identity.LoginCommand;
import com.accuenergy.octopus.mgmt.application.identity.TokenIssuer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthenticationController {
    private final AuthenticationService authentication;
    private final ForceLogoutService forceLogout;

    public AuthenticationController(AuthenticationService authentication, ForceLogoutService forceLogout) {
        this.authentication = authentication;
        this.forceLogout = forceLogout;
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication current) {
        if (!(current.getDetails() instanceof AuthenticatedPrincipal principal)) {
            return ResponseEntity.status(401).build();
        }
        forceLogout.revokeAll(principal.accountId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        char[] password = request.password().toCharArray();
        try {
            return tokenResponse(authentication.login(new LoginCommand(request.login(), password,
                    Optional.ofNullable(request.totpCode()), Optional.ofNullable(request.recoveryCode()),
                    Optional.ofNullable(request.tenantId()))));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return tokenResponse(authentication.refresh(request.sessionId(), request.refreshGeneration(), request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication current) {
        if (!(current.getDetails() instanceof AuthenticatedPrincipal principal)) {
            return ResponseEntity.status(401).build();
        }
        authentication.logout(principal.sessionId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ResponseEntity<TokenResponse> tokenResponse(TokenIssuer.TokenPair pair) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new TokenResponse(
                pair.accessToken(), pair.refreshToken(), "Bearer", pair.sessionId(), pair.refreshGeneration(),
                pair.accessExpiresAt(), pair.sessionExpiresAt()));
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String password,
                               String totpCode, String recoveryCode, UUID tenantId) {
        @Override public String toString() { return "LoginRequest[login=" + login + ", credentials=REDACTED]"; }
    }
    public record RefreshRequest(@NotNull UUID sessionId, @PositiveOrZero long refreshGeneration,
                                 @NotBlank String refreshToken) {
        @Override public String toString() { return "RefreshRequest[sessionId=" + sessionId + ", token=REDACTED]"; }
    }
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, UUID sessionId,
                                long refreshGeneration, Instant accessExpiresAt, Instant sessionExpiresAt) { }
}
