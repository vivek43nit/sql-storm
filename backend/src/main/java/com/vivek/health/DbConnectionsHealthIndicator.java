package com.vivek.health;

import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Readiness health indicator that verifies connectivity to every configured
 * database group.
 *
 * <p>Reported under {@code /actuator/health/dbConnections}. If any group
 * cannot produce a valid connection the overall status is {@code DOWN}, which
 * will cause the K8s readiness probe to fail and prevent traffic being sent
 * to a pod that cannot reach its databases.</p>
 */
@Component("dbConnections")
public class DbConnectionsHealthIndicator implements HealthIndicator {

  private final DatabaseConnectionManager connectionManager;

  public DbConnectionsHealthIndicator(DatabaseConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    boolean allUp = true;

    Set<String> groups = connectionManager.getGroupNames();
    if (groups.isEmpty()) {
      return Health.unknown().withDetail("message", "No database groups configured").build();
    }

    for (String group : groups) {
      try {
        Set<String> dbs = connectionManager.getDbNames(group);
        Map<String, String> groupStatus = new LinkedHashMap<>();
        for (String db : dbs) {
          try {
            Connection con = connectionManager.getConnection(group, db);
            boolean valid = con != null && con.isValid(2);
            groupStatus.put(db, valid ? "UP" : "DOWN");
            if (!valid) allUp = false;
          } catch (Exception e) {
            groupStatus.put(db, "DOWN: " + e.getMessage());
            allUp = false;
          }
        }
        details.put(group, groupStatus);
      } catch (Exception e) {
        details.put(group, "ERROR: " + e.getMessage());
        allUp = false;
      }
    }

    Health.Builder builder = allUp ? Health.up() : Health.down();
    details.forEach(builder::withDetail);
    return builder.build();
  }
}
