package com.accuenergy.octopus.mgmt.interfaces.alarm;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.alarm.AlarmManagementService;
import com.accuenergy.octopus.mgmt.domain.alarm.AlarmRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
@RequestMapping("/api/v1/alarm-rules")
public final class AlarmRuleController {
    private final AlarmManagementService alarms;

    public AlarmRuleController(AlarmManagementService alarms) {
        this.alarms = alarms;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_alarm:configure') or hasAuthority('PERM_platform:all')")
    public AlarmRuleResponse create(Authentication authentication, @Valid @RequestBody CreateAlarmRuleRequest request) {
        return response(alarms.createRule(principal(authentication), new AlarmManagementService.CreateRule(
                request.meterId(), request.parameterId(), request.code(), request.displayName(),
                request.valueSelector(), request.comparison(), request.triggerThreshold(), request.clearThreshold(),
                request.severity())));
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('PERM_alarm:view') or hasAuthority('PERM_platform:all')")
    public AlarmRuleResponse get(Authentication authentication, @PathVariable UUID ruleId) {
        return response(alarms.getRule(principal(authentication), ruleId));
    }

    private static AlarmRuleResponse response(AlarmRule rule) {
        return new AlarmRuleResponse(rule.id(), rule.organizationId(), rule.deviceId(), rule.meterId(),
                rule.parameterId(), rule.code(), rule.displayName(), rule.valueSelector(), rule.comparison(),
                rule.triggerThreshold(), rule.clearThreshold(), rule.severity(), rule.status(), rule.version(),
                rule.createdAt(), rule.updatedAt());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CreateAlarmRuleRequest(@NotNull UUID meterId, @NotNull UUID parameterId,
            @NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String displayName,
            @NotNull AlarmRule.ValueSelector valueSelector, @NotNull AlarmRule.Comparison comparison,
            @NotNull BigDecimal triggerThreshold, BigDecimal clearThreshold,
            @NotNull AlarmRule.Severity severity) { }

    public record AlarmRuleResponse(UUID id, UUID organizationId, UUID deviceId, UUID meterId,
            UUID parameterId, String code, String displayName, AlarmRule.ValueSelector valueSelector,
            AlarmRule.Comparison comparison, BigDecimal triggerThreshold, BigDecimal clearThreshold,
            AlarmRule.Severity severity, AlarmRule.Status status, long version,
            Instant createdAt, Instant updatedAt) { }
}
