package com.accuenergy.octopus.common.persistence;

import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

/** Fail-closed MyBatis tenant handler. Global tables must be explicitly allowlisted. */
public final class OctopusTenantLineHandler implements TenantLineHandler {
    private final Set<String> globalTables;

    public OctopusTenantLineHandler(Set<String> globalTables) {
        this.globalTables = globalTables.stream()
                .map(table -> table.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Expression getTenantId() {
        TenantScope scope = TenantContext.requireCurrent();
        return scope.tenantId()
                .<Expression>map(id -> new StringValue(id.toString()))
                .orElseThrow(() -> new IllegalStateException(
                        "Platform queries require a dedicated audited repository, not tenant interceptor bypass"));
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return globalTables.contains(tableName.toLowerCase(Locale.ROOT));
    }
}

