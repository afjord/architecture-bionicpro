package com.bionicpro.auth.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ReportView> findUserReport(String keycloakUserId, LocalDate periodStart, LocalDate periodEnd) {
        return jdbcTemplate.query("""
                        SELECT
                            keycloak_user_id,
                            report_period_start,
                            report_period_end,
                            processed_until,
                            username,
                            full_name,
                            email,
                            prosthesis_model,
                            prosthesis_serial,
                            assigned_at,
                            telemetry_events,
                            total_steps,
                            total_grip_cycles,
                            avg_battery_level,
                            min_battery_level,
                            max_load_kg,
                            error_events,
                            generated_at
                        FROM reporting.user_report_mart
                        WHERE keycloak_user_id = ?
                          AND report_period_start = ?
                          AND report_period_end <= ?
                        ORDER BY report_period_end DESC
                        LIMIT 1
                        """,
                ps -> {
                    ps.setString(1, keycloakUserId);
                    ps.setDate(2, Date.valueOf(periodStart));
                    ps.setDate(3, Date.valueOf(periodEnd));
                },
                (rs, rowNum) -> mapReport(rs)
        ).stream().findFirst();
    }

    private ReportView mapReport(ResultSet rs) throws SQLException {
        return new ReportView(
                rs.getString("keycloak_user_id"),
                rs.getDate("report_period_start").toLocalDate(),
                rs.getDate("report_period_end").toLocalDate(),
                toInstant(rs.getObject("processed_until")),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("prosthesis_model"),
                rs.getString("prosthesis_serial"),
                rs.getDate("assigned_at").toLocalDate(),
                rs.getInt("telemetry_events"),
                rs.getInt("total_steps"),
                rs.getInt("total_grip_cycles"),
                rs.getBigDecimal("avg_battery_level"),
                rs.getBigDecimal("min_battery_level"),
                rs.getBigDecimal("max_load_kg"),
                rs.getInt("error_events"),
                toInstant(rs.getObject("generated_at"))
        );
    }

    private Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case LocalDateTime localDateTime -> localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalArgumentException("Unsupported timestamp value: " + value);
        };
    }
}
