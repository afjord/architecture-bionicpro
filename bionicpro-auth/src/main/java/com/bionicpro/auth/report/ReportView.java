package com.bionicpro.auth.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ReportView(
        String keycloakUserId,
        LocalDate reportPeriodStart,
        LocalDate reportPeriodEnd,
        Instant processedUntil,
        String username,
        String fullName,
        String email,
        String prosthesisModel,
        String prosthesisSerial,
        LocalDate assignedAt,
        int telemetryEvents,
        int totalSteps,
        int totalGripCycles,
        BigDecimal avgBatteryLevel,
        BigDecimal minBatteryLevel,
        BigDecimal maxLoadKg,
        int errorEvents,
        Instant generatedAt
) {
}
