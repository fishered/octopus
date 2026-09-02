package com.accuenergy.octopus.mgmt.domain.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Presentation-only navigation metadata; it never grants access to an API. */
public record NavigationMenu(UUID id, Optional<UUID> parentId, String code, Optional<String> route,
                             Optional<String> requiredPermissionCode, int sortOrder) {
    public NavigationMenu {
        Objects.requireNonNull(id, "id");
        parentId = Objects.requireNonNull(parentId, "parentId");
        code = requireText(code, "code", 64);
        route = normalizeOptional(route, "route", 300);
        requiredPermissionCode = normalizeOptional(requiredPermissionCode,
                "requiredPermissionCode", 128);
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must be non-negative");
        if (parentId.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("Menu cannot be its own parent");
        }
    }

    public boolean isVisibleTo(java.util.Set<String> permissions) {
        return requiredPermissionCode.isEmpty() || permissions.contains(requiredPermissionCode.orElseThrow());
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) return Optional.empty();
        return Optional.of(requireText(value.orElseThrow(), name, maximumLength));
    }
}
