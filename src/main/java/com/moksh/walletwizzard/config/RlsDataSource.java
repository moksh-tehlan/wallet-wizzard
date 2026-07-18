package com.moksh.walletwizzard.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * DataSource wrapper that sets the PostgreSQL session variable app.current_user_id
 * on every connection borrow. The RLS policies in V12 read this variable to filter rows.
 *
 * Using set_config(..., false) (session-level, not transaction-local) means the value
 * persists on the physical connection. We set it on EVERY borrow — including clearing it
 * when no tenant is in context — so connections returned to HikariCP's pool never carry
 * a stale tenant from a previous request.
 *
 * This approach is simpler and more reliable than AOP around @Transactional because it
 * does not depend on Spring's AOP ordering and works for all connection acquisitions
 * including those made by JdbcTemplate, Flyway, and Hibernate internals.
 *
 * Note: Flyway connects via its own DataSource (configured separately in application.yaml
 * with the schema owner credentials) and does NOT go through this wrapper.
 */
public class RlsDataSource extends DelegatingDataSource {

    private static final String SET_CONFIG_SQL =
            "SELECT set_config('app.current_user_id', ?, false)";

    public RlsDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applyTenantContext(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = super.getConnection(username, password);
        applyTenantContext(conn);
        return conn;
    }

    private void applyTenantContext(Connection conn) throws SQLException {
        UUID userId = TenantContext.getCurrentUser();
        // Empty string when no tenant — RLS policy (user_id::TEXT = '') never matches any UUID
        String value = userId != null ? userId.toString() : "";
        try (PreparedStatement ps = conn.prepareStatement(SET_CONFIG_SQL)) {
            ps.setString(1, value);
            ps.execute();
        }
    }
}
