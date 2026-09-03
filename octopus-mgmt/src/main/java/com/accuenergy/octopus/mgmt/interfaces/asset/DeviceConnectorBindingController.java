package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.asset.DeviceConnectorBindingManagementService;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceConnectorBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/connector-binding")
public final class DeviceConnectorBindingController {
    private final DeviceConnectorBindingManagementService service;
    public DeviceConnectorBindingController(DeviceConnectorBindingManagementService service) { this.service=service; }
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_device:view') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<BindingResponse> get(Authentication authentication,@PathVariable UUID deviceId) {
        return service.get(principal(authentication),deviceId).map(b -> ResponseEntity.ok(response(b))).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PutMapping
    @PreAuthorize("hasAuthority('PERM_device:configure') or hasAuthority('PERM_platform:all')")
    public ResponseEntity<BindingResponse> put(Authentication authentication,@PathVariable UUID deviceId,
            @RequestHeader(value="If-Match", required=false) String ifMatch,@Valid @RequestBody BindingRequest request) {
        Long expected=parseIfMatch(ifMatch); DeviceConnectorBinding b=service.upsert(principal(authentication),deviceId,
                request.pluginId(),request.codecId(),request.configRef(),request.status(),expected);
        return ResponseEntity.ok().eTag(String.valueOf(b.version())).body(response(b));
    }
    private static Long parseIfMatch(String value) {
        if (value==null || value.isBlank()) return null; String v=value.trim();
        if (v.length() >= 2 && v.charAt(0) == 34 && v.charAt(v.length()-1) == 34) v=v.substring(1,v.length()-1);
        try { return Long.valueOf(v); } catch(NumberFormatException e) { throw new IllegalArgumentException("If-Match must be a binding version"); }
    }
    private static BindingResponse response(DeviceConnectorBinding b) { return new BindingResponse(b.deviceId(),b.pluginId(),b.codecId(),b.configRef(),b.status(),b.version(),b.createdAt(),b.updatedAt()); }
    private static AuthenticatedPrincipal principal(Authentication a) { if (a.getDetails() instanceof AuthenticatedPrincipal p) return p; throw new IllegalStateException("Authenticated principal is unavailable"); }
    public record BindingRequest(@NotBlank String pluginId,@NotBlank String codecId,String configRef,@NotNull DeviceConnectorBinding.Status status) {}
    public record BindingResponse(UUID deviceId,String pluginId,String codecId,String configRef,DeviceConnectorBinding.Status status,long version,Instant createdAt,Instant updatedAt) {}
}
