package com.bionicpro.auth.report;

import java.time.LocalDate;

public record ReportResponse(
        String status,
        String source,
        String reportUrl,
        String objectKey,
        LocalDate periodStart,
        LocalDate periodEnd,
        ReportView report
) {
}
