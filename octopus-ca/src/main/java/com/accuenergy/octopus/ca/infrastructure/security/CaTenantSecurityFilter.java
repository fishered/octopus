package com.accuenergy.octopus.ca.infrastructure.security;

import com.accuenergy.octopus.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class CaTenantSecurityFilter extends OncePerRequestFilter {
    private final CaTenantScopeResolver scopeResolver;

    public CaTenantSecurityFilter(CaTenantScopeResolver scopeResolver) {
        this.scopeResolver = scopeResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            filterChain.doFilter(request, response);
            return;
        }
        final var scope = resolveScope(jwt, request, response);
        if (scope == null) {
            return;
        }
        try {
            TenantContext.call(scope, () -> { filterChain.doFilter(request, response); return null; });
        } catch (ServletException | IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServletException("CA tenant request scope failed", exception);
        }
    }

    private com.accuenergy.octopus.common.tenant.TenantScope resolveScope(
            JwtAuthenticationToken jwt, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return scopeResolver.resolve(jwt.getToken(), request.getHeader("X-Octopus-Tenant-Id"),
                    request.getHeader("X-Octopus-Support-Reason"));
        } catch (IllegalArgumentException invalidScope) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CA tenant scope");
            return null;
        }
    }
}
