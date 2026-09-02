package com.accuenergy.octopus.common.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Callers must establish the scope from verified authentication, never a raw request header. */
public final class TenantContext {
    private static final ThreadLocal<TenantScope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<TenantScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantScope requireCurrent() {
        return current().orElseThrow(() -> new MissingTenantScopeException("No tenant scope is bound"));
    }

    public static void run(TenantScope scope, Runnable operation) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        TenantScope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            operation.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> T call(TenantScope scope, Callable<T> operation) throws Exception {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        TenantScope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            return operation.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
