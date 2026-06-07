CREATE DATABASE IF NOT EXISTS cdc;
CREATE DATABASE IF NOT EXISTS crm;
CREATE DATABASE IF NOT EXISTS telemetry;
CREATE DATABASE IF NOT EXISTS reporting;

CREATE TABLE IF NOT EXISTS cdc.kafka_crm_customers
(
    message String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'bionicpro_crm.crm.customers',
    kafka_group_name = 'clickhouse_crm_customers_v3',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1,
    kafka_skip_broken_messages = 1000;

CREATE TABLE IF NOT EXISTS cdc.kafka_telemetry_sensor_events
(
    message String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'bionicpro_crm.telemetry.sensor_events',
    kafka_group_name = 'clickhouse_telemetry_sensor_events',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1,
    kafka_skip_broken_messages = 1000;

CREATE TABLE IF NOT EXISTS crm.customers
(
    keycloak_user_id String,
    username String,
    full_name String,
    email String,
    prosthesis_model String,
    prosthesis_serial String,
    assigned_at Date,
    cdc_op LowCardinality(String),
    is_deleted UInt8,
    cdc_version UInt64
)
ENGINE = ReplacingMergeTree(cdc_version)
ORDER BY keycloak_user_id;

CREATE TABLE IF NOT EXISTS telemetry.sensor_events
(
    event_id UInt64,
    keycloak_user_id String,
    event_time DateTime64(3, 'UTC'),
    steps_count Int32,
    grip_cycles Int32,
    battery_level Decimal(5, 2),
    load_kg Decimal(6, 2),
    error_code Nullable(String),
    cdc_op LowCardinality(String),
    is_deleted UInt8,
    cdc_version UInt64
)
ENGINE = ReplacingMergeTree(cdc_version)
ORDER BY (event_id, keycloak_user_id, event_time);

CREATE MATERIALIZED VIEW IF NOT EXISTS cdc.mv_crm_customers
TO crm.customers
AS
WITH
    JSONExtractString(message, 'op') AS op,
    if(op = 'd', JSONExtractRaw(message, 'before'), JSONExtractRaw(message, 'after')) AS row
SELECT
    JSONExtractString(row, 'keycloak_user_id') AS keycloak_user_id,
    JSONExtractString(row, 'username') AS username,
    JSONExtractString(row, 'full_name') AS full_name,
    JSONExtractString(row, 'email') AS email,
    JSONExtractString(row, 'prosthesis_model') AS prosthesis_model,
    JSONExtractString(row, 'prosthesis_serial') AS prosthesis_serial,
    toDate('1970-01-01') + toInt32(JSONExtractInt(row, 'assigned_at')) AS assigned_at,
    op AS cdc_op,
    if(op = 'd', 1, 0) AS is_deleted,
    if(JSONExtractUInt(message, 'ts_ms') = 0, toUInt64(toUnixTimestamp64Milli(now64(3))), JSONExtractUInt(message, 'ts_ms')) AS cdc_version
FROM cdc.kafka_crm_customers
WHERE row != '' AND JSONExtractString(row, 'keycloak_user_id') != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS cdc.mv_telemetry_sensor_events
TO telemetry.sensor_events
AS
WITH
    JSONExtractString(message, 'op') AS op,
    if(op = 'd', JSONExtractRaw(message, 'before'), JSONExtractRaw(message, 'after')) AS row,
    JSONExtractString(row, 'event_time') AS event_time_text,
    JSONExtractInt(row, 'event_time') AS event_time_number
SELECT
    toUInt64(JSONExtractUInt(row, 'event_id')) AS event_id,
    JSONExtractString(row, 'keycloak_user_id') AS keycloak_user_id,
    if(
        event_time_text = '',
        fromUnixTimestamp64Micro(toInt64(event_time_number)),
        parseDateTime64BestEffort(event_time_text, 3, 'UTC')
    ) AS event_time,
    toInt32(JSONExtractInt(row, 'steps_count')) AS steps_count,
    toInt32(JSONExtractInt(row, 'grip_cycles')) AS grip_cycles,
    toDecimal32(JSONExtractFloat(row, 'battery_level'), 2) AS battery_level,
    toDecimal32(JSONExtractFloat(row, 'load_kg'), 2) AS load_kg,
    nullIf(JSONExtractString(row, 'error_code'), '') AS error_code,
    op AS cdc_op,
    if(op = 'd', 1, 0) AS is_deleted,
    if(JSONExtractUInt(message, 'ts_ms') = 0, toUInt64(toUnixTimestamp64Milli(now64(3))), JSONExtractUInt(message, 'ts_ms')) AS cdc_version
FROM cdc.kafka_telemetry_sensor_events
WHERE row != '' AND JSONExtractUInt(row, 'event_id') != 0;

CREATE TABLE IF NOT EXISTS reporting.user_report_telemetry_monthly
(
    keycloak_user_id String,
    report_period_start Date,
    telemetry_events_state AggregateFunction(count),
    total_steps_state AggregateFunction(sum, Int32),
    total_grip_cycles_state AggregateFunction(sum, Int32),
    avg_battery_level_state AggregateFunction(avg, Decimal(5, 2)),
    min_battery_level_state AggregateFunction(min, Decimal(5, 2)),
    max_load_kg_state AggregateFunction(max, Decimal(6, 2)),
    error_events_state AggregateFunction(sum, UInt8),
    max_event_time_state AggregateFunction(max, DateTime64(3, 'UTC'))
)
ENGINE = AggregatingMergeTree
ORDER BY (keycloak_user_id, report_period_start);

CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_user_report_telemetry_monthly
TO reporting.user_report_telemetry_monthly
AS
SELECT
    keycloak_user_id,
    toStartOfMonth(toDate(event_time)) AS report_period_start,
    countState() AS telemetry_events_state,
    sumState(steps_count) AS total_steps_state,
    sumState(grip_cycles) AS total_grip_cycles_state,
    avgState(battery_level) AS avg_battery_level_state,
    minState(battery_level) AS min_battery_level_state,
    maxState(load_kg) AS max_load_kg_state,
    sumState(toUInt8(isNotNull(error_code))) AS error_events_state,
    maxState(event_time) AS max_event_time_state
FROM telemetry.sensor_events
WHERE is_deleted = 0
GROUP BY
    keycloak_user_id,
    report_period_start;

CREATE VIEW IF NOT EXISTS reporting.user_report_mart
AS
SELECT
    c.keycloak_user_id AS keycloak_user_id,
    a.report_period_start AS report_period_start,
    toDate(maxMerge(a.max_event_time_state)) AS report_period_end,
    maxMerge(a.max_event_time_state) AS processed_until,
    c.username AS username,
    c.full_name AS full_name,
    c.email AS email,
    c.prosthesis_model AS prosthesis_model,
    c.prosthesis_serial AS prosthesis_serial,
    c.assigned_at AS assigned_at,
    toInt32(countMerge(a.telemetry_events_state)) AS telemetry_events,
    toInt32(sumMerge(a.total_steps_state)) AS total_steps,
    toInt32(sumMerge(a.total_grip_cycles_state)) AS total_grip_cycles,
    round(avgMerge(a.avg_battery_level_state), 2) AS avg_battery_level,
    minMerge(a.min_battery_level_state) AS min_battery_level,
    maxMerge(a.max_load_kg_state) AS max_load_kg,
    toInt32(sumMerge(a.error_events_state)) AS error_events,
    now() AS generated_at
FROM reporting.user_report_telemetry_monthly AS a
INNER JOIN
(
    SELECT *
    FROM crm.customers FINAL
    WHERE is_deleted = 0
) AS c ON c.keycloak_user_id = a.keycloak_user_id
GROUP BY
    c.keycloak_user_id,
    a.report_period_start,
    c.username,
    c.full_name,
    c.email,
    c.prosthesis_model,
    c.prosthesis_serial,
    c.assigned_at;
