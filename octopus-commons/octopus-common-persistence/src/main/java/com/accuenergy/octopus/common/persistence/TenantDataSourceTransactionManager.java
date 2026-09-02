package com.accuenergy.octopus.common.persistence;

import com.accuenergy.octopus.common.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;

/** Establishes PostgreSQL RLS tenant state on the transaction-bound connection. */
public final class TenantDataSourceTransactionManager extends DataSourceTransactionManager {
    public TenantDataSourceTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareTransactionalConnection(Connection connection, TransactionDefinition definition)
            throws SQLException {
        super.prepareTransactionalConnection(connection, definition);
        String tenantId = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new CannotCreateTransactionException(
                        "Platform scope cannot use the tenant transaction manager"))
                .toString();
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenantId);
            statement.execute();
        }
    }
}
