# Задание 2. Сервис отчётов

## Архитектура

Draw.io-модель в нотации C4 Container: `task2/BionicPRO_reports_architecture.drawio.xml`.

Решение разделено на два потока:

- Batch ETL: Apache Airflow ежедневно в `02:00 Europe/Moscow` запускает DAG `prepare_user_report_mart`, читает `crm.customers` и `telemetry.sensor_events`, агрегирует телеметрию по пользователям и периоду, затем делает upsert в `reporting.user_report_mart`.
- Online API: frontend вызывает `GET /reports`; backend проверяет HTTP-only сессию, извлекает `sub` из Keycloak access token и читает из OLAP только строку текущего пользователя.

## Витрина

Витрина `reporting.user_report_mart` имеет ключ:

- `keycloak_user_id`
- `report_period_start`
- `report_period_end`

Для быстрого доступа по пользователю и периоду добавлен индекс `idx_user_report_mart_user_period`.

Витрина содержит клиентские атрибуты из CRM, данные протеза и уже рассчитанные метрики: количество событий, шаги, циклы хвата, средний/минимальный заряд, максимальную нагрузку и количество ошибок. Поле `processed_until` показывает границу периода, который уже обработан Airflow.

## Запуск

```bash
docker compose up -d reports_olap_db airflow_db airflow keycloak bionicpro-auth frontend
```

Airflow UI доступен на `http://localhost:8081`. DAG можно запустить вручную или дождаться расписания. После подготовки витрины UI на `http://localhost:3000` вызывает `/reports` и показывает отчёт текущего пользователя.

Локально Airflow запускается под `${AIRFLOW_UID:-1000}:0`, чтобы bind-mounted каталоги `airflow/logs` и `airflow/plugins` были доступны на запись. Если uid пользователя хоста отличается от `1000`, запустите compose с нужным значением `AIRFLOW_UID`.

Для локального стенда можно войти в Airflow как `admin` / `admin`.

Если после изменения Airflow-конфига браузер показывает `The CSRF session token is missing`, очистите cookies/site data для `localhost:8081` или откройте Airflow в приватном окне. В compose зафиксированы `AIRFLOW__WEBSERVER__BASE_URL` и `AIRFLOW__WEBSERVER__SECRET_KEY`, поэтому новые cookies будут валидны между перезапусками.

Если пользователь запросит период, которого нет в `reporting.user_report_mart`, backend вернёт `404` со статусом `not_ready`; отчёт в реальном времени не пересчитывается.
