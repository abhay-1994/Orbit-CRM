package com.orbitcrm.shared.tenant;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * DataSource wrapper that stamps every pooled connection with the caller's tenant before handing it out.
 *
 * <p>Purpose: make PostgreSQL row-level security aware of the current tenant. On each {@code getConnection()} it
 * runs {@code set_config('app.tenant_id', ...)} and {@code set_config('app.rls_bypass', ...)} from
 * {@link TenantContext}. RLS policies compare {@code tenant_id} with {@code app.tenant_id}; because the value is
 * written on every checkout, a connection can never carry a previous caller's tenant. With no tenant bound the
 * setting is empty and tenant tables return no rows (fail closed). The bypass flag is only switched on for
 * explicit cross-tenant platform jobs. Installed by {@code TenantPersistenceConfig}.
 *
 * @author Abhay
 */
public class TenantAwareDataSource extends DelegatingDataSource implements Closeable {

    private static final String SET_CONTEXT =
            "SELECT set_config('app.tenant_id', ?, false), set_config('app.rls_bypass', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return stamp(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return stamp(super.getConnection(username, password));
    }

    private Connection stamp(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
            UUID tenantId = TenantContext.currentTenantId().orElse(null);
            statement.setString(1, tenantId == null ? "" : tenantId.toString());
            statement.setString(2, TenantContext.isSystem() ? "on" : "off");
            statement.execute();
            return connection;
        } catch (SQLException | RuntimeException ex) {
            connection.close();
            throw ex;
        }
    }

    @Override
    public void close() throws IOException {
        if (getTargetDataSource() instanceof Closeable closeable) {
            closeable.close();
        }
    }
}
