package com.bionicpro.auth.report;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportObjectStore objectStore;

    public ReportService(ReportRepository reportRepository, ReportObjectStore objectStore) {
        this.reportRepository = reportRepository;
        this.objectStore = objectStore;
    }

    public Optional<ReportResponse> getOrCreateReport(String keycloakUserId, LocalDate periodStart, LocalDate periodEnd) {
        String objectKey = objectStore.objectKey(keycloakUserId, periodStart, periodEnd);
        String cdnUrl = objectStore.cdnUrl(objectKey);

        if (objectStore.exists(objectKey)) {
            return Optional.of(new ReportResponse("ready", "s3", cdnUrl, objectKey, periodStart, periodEnd, null));
        }

        return reportRepository.findUserReport(keycloakUserId, periodStart, periodEnd)
                .map(report -> {
                    objectStore.put(objectKey, report);
                    return new ReportResponse("ready", "olap", cdnUrl, objectKey, periodStart, periodEnd, report);
                });
    }
}
