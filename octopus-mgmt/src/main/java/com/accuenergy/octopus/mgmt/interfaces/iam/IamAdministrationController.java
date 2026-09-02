package com.accuenergy.octopus.mgmt.interfaces.iam;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.mgmt.application.iam.IamAdministrationRepository;
import com.accuenergy.octopus.mgmt.application.iam.IamAdministrationService;
import com.accuenergy.octopus.mgmt.application.identity.TotpLifecycleService;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedAccount;
import com.accuenergy.octopus.mgmt.domain.identity.ManagedResourceGrant;
import com.accuenergy.octopus.mgmt.domain.identity.NavigationMenu;
import com.accuenergy.octopus.mgmt.domain.identity.PermissionDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam")
public final class IamAdministrationController {
    private final IamAdministrationService iam;
    private final TotpLifecycleService totp;
    public IamAdministrationController(IamAdministrationService iam, TotpLifecycleService totp) {
        this.iam = iam;
        this.totp = totp;
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('PERM_account:create') or hasAuthority('PERM_platform:all')")
    public AccountResponse createAccount(Authentication authentication,
                                         @Valid @RequestBody CreateAccountRequest request) {
        ManagedAccount account = iam.createAccount(principal(authentication),
                new IamAdministrationService.CreateAccount(request.loginName(), request.email(),
                        request.phoneE164(), request.password().toCharArray()));
        return new AccountResponse(account.id(), account.loginName(), account.email().orElse(null),
                account.phoneE164().orElse(null), account.status(), account.createdAt());
    }

    @PostMapping("/accounts/{accountId}/status")
    @PreAuthorize("hasAuthority('PERM_platform:all')")
    public AccountStatusChangeResponse changeAccountStatus(Authentication authentication,
            @PathVariable UUID accountId, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return AccountStatusChangeResponse.from(iam.changeAccountStatus(principal(authentication),
                accountId, request.status(), request.reason()));
    }

    @PutMapping("/accounts/me/password")
    public PasswordChangeResponse changeOwnPassword(Authentication authentication,
            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        char[] currentPassword = request.currentPassword().toCharArray();
        char[] newPassword = request.newPassword().toCharArray();
        try {
            return PasswordChangeResponse.from(iam.changeOwnPassword(principal(authentication),
                    new IamAdministrationService.ChangeOwnPassword(currentPassword, newPassword)));
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }

    @PostMapping("/accounts/{accountId}/password/reset")
    @PreAuthorize("hasAuthority('PERM_platform:all')")
    public PasswordChangeResponse resetAccountPassword(Authentication authentication,
            @PathVariable UUID accountId, @Valid @RequestBody ResetAccountPasswordRequest request) {
        char[] newPassword = request.newPassword().toCharArray();
        try {
            return PasswordChangeResponse.from(iam.resetAccountPassword(principal(authentication), accountId,
                    new IamAdministrationService.ResetAccountPassword(newPassword, request.reason())));
        } finally {
            Arrays.fill(newPassword, '\0');
        }
    }

    @PostMapping("/accounts/{accountId}/totp/reset")
    @PreAuthorize("hasAuthority('PERM_platform:all')")
    public TotpResetResponse resetAccountTotp(Authentication authentication,
            @PathVariable UUID accountId, @Valid @RequestBody ResetTotpRequest request) {
        var change = totp.platformReset(principal(authentication), accountId, request.reason());
        return new TotpResetResponse(change.accountId(), change.sessionGeneration());
    }

    @PostMapping("/memberships")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public MembershipResponse addMembership(Authentication authentication,
                                            @Valid @RequestBody AddMembershipRequest request) {
        return new MembershipResponse(iam.addMembership(principal(authentication),
                request.accountId(), request.organizationId()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('PERM_role:manage') or hasAuthority('PERM_platform:all')")
    public RoleResponse createRole(Authentication authentication, @Valid @RequestBody CreateRoleRequest request) {
        UUID roleId = iam.createRole(principal(authentication), new IamAdministrationService.CreateRole(
                request.organizationId(), request.code(), request.displayName(), request.roleType(),
                request.permissionCodes()));
        return new RoleResponse(roleId);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('PERM_role:view') or hasAuthority('PERM_platform:all')")
    public List<RoleDetailResponse> listRoles(Authentication authentication,
                                              @RequestParam UUID organizationId) {
        return iam.listRoles(principal(authentication), organizationId).stream()
                .map(RoleDetailResponse::from).toList();
    }

    @GetMapping("/memberships")
    @PreAuthorize("hasAuthority('PERM_account:view') or hasAuthority('PERM_platform:all')")
    public List<MembershipDetailResponse> listMemberships(Authentication authentication,
                                                          @RequestParam UUID organizationId) {
        return iam.listMemberships(principal(authentication), organizationId).stream()
                .map(MembershipDetailResponse::from).toList();
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('PERM_role:manage') or hasAuthority('PERM_platform:all')")
    public RoleAuthorizationChangeResponse replaceRolePermissions(Authentication authentication,
            @PathVariable UUID roleId, @Valid @RequestBody ReplaceRolePermissionsRequest request) {
        return RoleAuthorizationChangeResponse.from(iam.replaceRolePermissions(principal(authentication),
                roleId, request.permissionCodes(), request.reason()));
    }

    @PostMapping("/roles/{roleId}/retire")
    @PreAuthorize("hasAuthority('PERM_role:manage') or hasAuthority('PERM_platform:all')")
    public RoleAuthorizationChangeResponse retireRole(Authentication authentication,
            @PathVariable UUID roleId, @Valid @RequestBody RevokeAccessRequest request) {
        return RoleAuthorizationChangeResponse.from(iam.retireRole(
                principal(authentication), roleId, request.reason()));
    }

    @PutMapping("/memberships/{membershipId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public AssignmentResponse assignRole(Authentication authentication, @PathVariable UUID membershipId,
                                         @PathVariable UUID roleId) {
        return new AssignmentResponse(iam.assignRole(principal(authentication), membershipId, roleId));
    }

    @PostMapping("/memberships/{membershipId}/roles/{roleId}/revoke")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public AssignmentResponse revokeRole(Authentication authentication, @PathVariable UUID membershipId,
                                         @PathVariable UUID roleId,
                                         @Valid @RequestBody RevokeAccessRequest request) {
        return new AssignmentResponse(iam.revokeRole(principal(authentication), membershipId,
                roleId, request.reason()));
    }

    @PostMapping("/memberships/{membershipId}/suspend")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public AssignmentResponse suspendMembership(Authentication authentication, @PathVariable UUID membershipId,
                                                @Valid @RequestBody RevokeAccessRequest request) {
        return new AssignmentResponse(iam.suspendMembership(principal(authentication),
                membershipId, request.reason()));
    }

    @PostMapping("/memberships/{membershipId}/activate")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public AssignmentResponse activateMembership(Authentication authentication, @PathVariable UUID membershipId,
                                                 @Valid @RequestBody RevokeAccessRequest request) {
        return new AssignmentResponse(iam.activateMembership(principal(authentication),
                membershipId, request.reason()));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_permission:view') or hasAuthority('PERM_platform:all')")
    public List<PermissionResponse> listPermissions(Authentication authentication) {
        return iam.listPermissionCatalog(principal(authentication)).stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('PERM_menu:view') or hasAuthority('PERM_platform:all')")
    public List<MenuResponse> listMenus(Authentication authentication) {
        return iam.listVisibleMenus(principal(authentication)).stream()
                .map(MenuResponse::from)
                .toList();
    }

    @PostMapping("/memberships/{membershipId}/resource-grants")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public ResourceGrantChangeResponse grantResourceAccess(Authentication authentication,
            @PathVariable UUID membershipId, @Valid @RequestBody GrantResourceAccessRequest request) {
        IamAdministrationService.ResourceGrantChange result = iam.grantResourceAccess(
                principal(authentication), membershipId,
                new IamAdministrationService.GrantResourceAccess(request.resourceType(),
                        request.resourceId(), request.action()));
        return ResourceGrantChangeResponse.from(result);
    }

    @GetMapping("/memberships/{membershipId}/resource-grants")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public List<ResourceGrantResponse> listResourceGrants(Authentication authentication,
                                                           @PathVariable UUID membershipId) {
        return iam.listResourceGrants(principal(authentication), membershipId).stream()
                .map(ResourceGrantResponse::from)
                .toList();
    }

    @PostMapping("/resource-grants/{grantId}/revoke")
    @PreAuthorize("hasAuthority('PERM_account:manage') or hasAuthority('PERM_platform:all')")
    public ResourceGrantChangeResponse revokeResourceAccess(Authentication authentication,
            @PathVariable UUID grantId, @Valid @RequestBody RevokeResourceGrantRequest request) {
        return ResourceGrantChangeResponse.from(iam.revokeResourceAccess(
                principal(authentication), grantId, request.reason()));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CreateAccountRequest(@NotBlank String loginName, @Email String email,
                                       String phoneE164, @NotBlank @Size(min = 12, max = 1024) String password) { }
    public record ChangeAccountStatusRequest(@NotNull ManagedAccount.Status status,
                                             @NotBlank @Size(max = 500) String reason) { }
    public record ChangeOwnPasswordRequest(@NotBlank @Size(min = 12, max = 1024) String currentPassword,
                                           @NotBlank @Size(min = 12, max = 1024) String newPassword) {
        @Override public String toString() { return "ChangeOwnPasswordRequest[credentials=REDACTED]"; }
    }
    public record ResetAccountPasswordRequest(@NotBlank @Size(min = 12, max = 1024) String newPassword,
                                              @NotBlank @Size(max = 500) String reason) {
        @Override public String toString() { return "ResetAccountPasswordRequest[credentials=REDACTED]"; }
    }
    public record ResetTotpRequest(@NotBlank @Size(max = 500) String reason) { }
    public record AddMembershipRequest(@NotNull UUID accountId, @NotNull UUID organizationId) { }
    public record CreateRoleRequest(@NotNull UUID organizationId, @NotBlank String code,
                                    @NotBlank String displayName,
                                    @NotNull IamAdministrationRepository.RoleType roleType,
                                    @NotNull Set<@NotBlank String> permissionCodes) { }
    public record ReplaceRolePermissionsRequest(@NotNull Set<@NotBlank String> permissionCodes,
                                                @NotBlank @Size(max = 500) String reason) { }
    public record AccountResponse(UUID id, String loginName, String email, String phoneE164,
                                  ManagedAccount.Status status, Instant createdAt) { }
    public record AccountStatusChangeResponse(UUID accountId, ManagedAccount.Status status,
                                              long sessionGeneration) {
        private static AccountStatusChangeResponse from(IamAdministrationService.AccountStatusChange change) {
            return new AccountStatusChangeResponse(change.accountId(), change.status(),
                    change.sessionGeneration());
        }
    }
    public record PasswordChangeResponse(UUID accountId, long sessionGeneration) {
        private static PasswordChangeResponse from(IamAdministrationService.PasswordChange change) {
            return new PasswordChangeResponse(change.accountId(), change.sessionGeneration());
        }
    }
    public record TotpResetResponse(UUID accountId, long sessionGeneration) { }
    public record MembershipResponse(UUID membershipId) { }
    public record RoleResponse(UUID roleId) { }
    public record RoleDetailResponse(UUID roleId, UUID organizationId, String code, String displayName,
                                     IamAdministrationRepository.RoleType roleType, String status,
                                     long version, Set<String> permissionCodes) {
        private static RoleDetailResponse from(IamAdministrationRepository.RoleView role) {
            return new RoleDetailResponse(role.roleId(), role.organizationId(), role.code(), role.displayName(),
                    role.roleType(), role.status(), role.version(), role.permissionCodes());
        }
    }
    public record MembershipDetailResponse(UUID membershipId, UUID accountId, UUID organizationId,
                                           String organizationPath, String loginName, String email,
                                           IamAdministrationRepository.MembershipStatus status,
                                           Set<UUID> roleIds) {
        private static MembershipDetailResponse from(IamAdministrationRepository.MembershipView membership) {
            return new MembershipDetailResponse(membership.membershipId(), membership.accountId(),
                    membership.organizationId(), membership.organizationPath(), membership.loginName(),
                    membership.email().orElse(null), membership.status(), membership.roleIds());
        }
    }
    public record RoleAuthorizationChangeResponse(UUID roleId, int affectedAccountCount) {
        private static RoleAuthorizationChangeResponse from(
                IamAdministrationService.RoleAuthorizationChange change) {
            return new RoleAuthorizationChangeResponse(change.roleId(), change.affectedAccountCount());
        }
    }
    public record AssignmentResponse(long authorizationGeneration) { }
    public record PermissionResponse(String code, String resourceType, String action, String description) {
        private static PermissionResponse from(PermissionDefinition permission) {
            return new PermissionResponse(permission.code(), permission.resourceType(), permission.action(),
                    permission.description());
        }
    }
    public record MenuResponse(UUID id, UUID parentId, String code, String route,
                               String requiredPermissionCode, int sortOrder) {
        private static MenuResponse from(NavigationMenu menu) {
            return new MenuResponse(menu.id(), menu.parentId().orElse(null), menu.code(),
                    menu.route().orElse(null), menu.requiredPermissionCode().orElse(null), menu.sortOrder());
        }
    }
    public record GrantResourceAccessRequest(@NotNull ManagedResourceGrant.ResourceType resourceType,
                                             @NotNull UUID resourceId,
                                             @NotNull ResourceAction action) { }
    public record RevokeResourceGrantRequest(@NotBlank @Size(max = 500) String reason) { }
    public record RevokeAccessRequest(@NotBlank @Size(max = 500) String reason) { }
    public record ResourceGrantResponse(UUID id, UUID membershipId,
                                        ManagedResourceGrant.ResourceType resourceType,
                                        UUID resourceId, ResourceAction action,
                                        UUID createdByAccountId, Instant createdAt) {
        private static ResourceGrantResponse from(ManagedResourceGrant grant) {
            return new ResourceGrantResponse(grant.id(), grant.membershipId(), grant.resourceType(),
                    grant.resourceId(), grant.action(), grant.createdByAccountId().orElse(null),
                    grant.createdAt());
        }
    }
    public record ResourceGrantChangeResponse(ResourceGrantResponse grant, long authorizationGeneration) {
        private static ResourceGrantChangeResponse from(IamAdministrationService.ResourceGrantChange change) {
            return new ResourceGrantChangeResponse(ResourceGrantResponse.from(change.grant()),
                    change.authorizationGeneration());
        }
    }
}
