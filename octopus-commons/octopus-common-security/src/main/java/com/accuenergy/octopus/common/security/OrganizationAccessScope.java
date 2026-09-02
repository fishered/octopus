package com.accuenergy.octopus.common.security;

import java.util.Objects;
import java.util.Set;

/** Permissions bound to one membership organization subtree. */
public record OrganizationAccessScope(String organizationPath, AccessLevel accessLevel,
                                      Set<String> permissions) {
    public OrganizationAccessScope {
        if (organizationPath == null || organizationPath.isBlank()) {
            throw new IllegalArgumentException("organizationPath is required");
        }
        organizationPath = normalizePath(organizationPath);
        Objects.requireNonNull(accessLevel, "accessLevel");
        permissions = Set.copyOf(permissions);
        if (accessLevel == AccessLevel.ADMINISTRATOR && !permissions.isEmpty()) {
            throw new IllegalArgumentException("Administrator scope does not require permission entries");
        }
    }

    public boolean allows(String permission, String candidatePath) {
        Objects.requireNonNull(permission, "permission");
        if (!contains(candidatePath)) return false;
        return accessLevel == AccessLevel.ADMINISTRATOR || permissions.contains(permission);
    }

    public boolean contains(String candidatePath) {
        if (candidatePath == null || candidatePath.isBlank()) return false;
        String candidate = normalizePath(candidatePath);
        return candidate.equals(organizationPath) || candidate.startsWith(organizationPath + "/");
    }

    private static String normalizePath(String path) {
        String normalized = path.strip();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("/")) throw new IllegalArgumentException("Organization path must be absolute");
        return normalized;
    }

    public enum AccessLevel { ADMINISTRATOR, OPERATOR }
}
