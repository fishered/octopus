package com.accuenergy.octopus.ca.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/** Explicitly marks audited manufacturing/bootstrap transactions for PostgreSQL RLS. */
public final class PlatformScopeDataSourceTransactionManager extends DataSourceTransactionManager {
    public PlatformScopeDataSourceTransactionManager(DataSource dataSource) { super(dataSource); }

    @Override
    protected void prepareTransactionalConnection(Connection connection, TransactionDefinition definition)
            throws SQLException {
        super.prepareTransactionalConnection(connection, definition);
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.platform_scope', 'true', true)")) {
            statement.execute();
        }
    }
}
