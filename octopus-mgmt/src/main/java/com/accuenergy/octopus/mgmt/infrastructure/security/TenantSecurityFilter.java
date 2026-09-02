package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class TenantSecurityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof AuthenticatedPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        TenantScope scope;
        if (principal.tenantId().isPresent()) {
            scope = new TenantScope.Scoped(principal.tenantId().orElseThrow());
        } else {
            String targetTenant = request.getHeader("X-Octopus-Tenant-Id");
            String reason = request.getHeader("X-Octopus-Support-Reason");
            if (targetTenant != null && !targetTenant.isBlank()) {
                if (reason == null || reason.isBlank()) {
                    response.sendError(400, "Platform tenant scope requires a support reason");
                    return;
                }
                scope = new TenantScope.Scoped(com.accuenergy.octopus.common.tenant.TenantId.parse(targetTenant));
            } else {
                scope = new TenantScope.Platform(reason == null ? "platform administration" : reason);
            }
        }
        try {
            TenantContext.call(scope, () -> {
                filterChain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServletException("Tenant request scope failed", exception);
        }
    }
}
