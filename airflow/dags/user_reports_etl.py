from __future__ import annotations

import pendulum
from airflow.decorators import dag, task
from airflow.providers.postgres.hooks.postgres import PostgresHook


REPORT_SQL = """
WITH bounds AS (
    SELECT
        %(period_start)s::date AS period_start,
        %(period_end)s::date AS period_end,
        (%(period_end)s::date + INTERVAL '1 day') AS exclusive_end
),
telemetry_agg AS (
    SELECT
        se.keycloak_user_id,
        COUNT(*)::int AS telemetry_events,
        COALESCE(SUM(se.steps_count), 0)::int AS total_steps,
        COALESCE(SUM(se.grip_cycles), 0)::int AS total_grip_cycles,
        COALESCE(ROUND(AVG(se.battery_level), 2), 0)::numeric(5, 2) AS avg_battery_level,
        COALESCE(MIN(se.battery_level), 0)::numeric(5, 2) AS min_battery_level,
        COALESCE(MAX(se.load_kg), 0)::numeric(6, 2) AS max_load_kg,
        COUNT(*) FILTER (WHERE se.error_code IS NOT NULL)::int AS error_events
    FROM telemetry.sensor_events se
    CROSS JOIN bounds b
    WHERE se.event_time >= b.period_start
      AND se.event_time < b.exclusive_end
    GROUP BY se.keycloak_user_id
)
INSERT INTO reporting.user_report_mart (
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
)
SELECT
    c.keycloak_user_id,
    b.period_start,
    b.period_end,
    b.exclusive_end,
    c.username,
    c.full_name,
    c.email,
    c.prosthesis_model,
    c.prosthesis_serial,
    c.assigned_at,
    COALESCE(t.telemetry_events, 0),
    COALESCE(t.total_steps, 0),
    COALESCE(t.total_grip_cycles, 0),
    COALESCE(t.avg_battery_level, 0),
    COALESCE(t.min_battery_level, 0),
    COALESCE(t.max_load_kg, 0),
    COALESCE(t.error_events, 0),
    now()
FROM crm.customers c
CROSS JOIN bounds b
LEFT JOIN telemetry_agg t ON t.keycloak_user_id = c.keycloak_user_id
ON CONFLICT (keycloak_user_id, report_period_start, report_period_end) DO UPDATE SET
    processed_until = EXCLUDED.processed_until,
    username = EXCLUDED.username,
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    prosthesis_model = EXCLUDED.prosthesis_model,
    prosthesis_serial = EXCLUDED.prosthesis_serial,
    assigned_at = EXCLUDED.assigned_at,
    telemetry_events = EXCLUDED.telemetry_events,
    total_steps = EXCLUDED.total_steps,
    total_grip_cycles = EXCLUDED.total_grip_cycles,
    avg_battery_level = EXCLUDED.avg_battery_level,
    min_battery_level = EXCLUDED.min_battery_level,
    max_load_kg = EXCLUDED.max_load_kg,
    error_events = EXCLUDED.error_events,
    generated_at = EXCLUDED.generated_at;
"""


@dag(
    dag_id="prepare_user_report_mart",
    description="Builds the OLAP report mart from CRM customers and prosthesis telemetry.",
    schedule="0 2 * * *",
    start_date=pendulum.datetime(2026, 6, 1, tz="Europe/Moscow"),
    catchup=False,
    tags=["bionicpro", "reports", "etl"],
)
def prepare_user_report_mart():
    @task
    def build_daily_report_mart(**context):
        logical_date = context["logical_date"].in_timezone("Europe/Moscow")
        period_end = logical_date.subtract(days=1).date()
        period_start = period_end.replace(day=1)

        hook = PostgresHook(postgres_conn_id="bionicpro_olap")
        hook.run(
            REPORT_SQL,
            parameters={
                "period_start": period_start.isoformat(),
                "period_end": period_end.isoformat(),
            },
        )

        return {
            "period_start": period_start.isoformat(),
            "period_end": period_end.isoformat(),
        }

    build_daily_report_mart()


prepare_user_report_mart()
