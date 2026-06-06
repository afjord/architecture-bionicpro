package com.bionicpro.auth.controller;

import com.bionicpro.auth.config.AuthProperties;
import com.bionicpro.auth.report.JwtSubjectExtractor;
import com.bionicpro.auth.report.ReportRepository;
import com.bionicpro.auth.session.AuthSessionStore;
import com.bionicpro.auth.session.RotatedSession;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportsController {

    private final AuthProperties properties;
    private final AuthSessionStore sessionStore;
    private final ReportRepository reportRepository;
    private final JwtSubjectExtractor jwtSubjectExtractor;

    public ReportsController(
            AuthProperties properties,
            AuthSessionStore sessionStore,
            ReportRepository reportRepository,
            JwtSubjectExtractor jwtSubjectExtractor
    ) {
        this.properties = properties;
        this.sessionStore = sessionStore;
        this.reportRepository = reportRepository;
        this.jwtSubjectExtractor = jwtSubjectExtractor;
    }

    @GetMapping("/reports")
    public ResponseEntity<?> reports(
            @CookieValue(name = "${bionicpro.session.cookie-name}", required = false) String sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            HttpServletResponse response
    ) {
        return sessionStore.rotateAndEnsureFreshAccessToken(sessionId)
                .map(rotated -> {
                    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(rotated).toString());
                    LocalDate resolvedPeriodEnd = periodEnd != null ? periodEnd : previousProcessedDay();
                    LocalDate resolvedPeriodStart = periodStart != null ? periodStart : resolvedPeriodEnd.withDayOfMonth(1);
                    if (resolvedPeriodStart.isAfter(resolvedPeriodEnd)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "invalid_period",
                                "message", "periodStart must be before or equal to periodEnd"
                        ));
                    }

                    String keycloakUserId = jwtSubjectExtractor.subject(rotated.accessToken());
                    return reportRepository.findUserReport(keycloakUserId, resolvedPeriodStart, resolvedPeriodEnd)
                            .<ResponseEntity<?>>map(report -> ResponseEntity.ok(Map.of(
                                    "status", "ready",
                                    "report", report
                            )))
                            .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                                    "status", "not_ready",
                                    "message", "Report for the requested period has not been prepared by Airflow yet",
                                    "periodStart", resolvedPeriodStart,
                                    "periodEnd", resolvedPeriodEnd
                            )));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of(
                        "status", "unauthorized",
                        "message", "Login is required"
                )));
    }

    private ResponseCookie sessionCookie(RotatedSession rotated) {
        return ResponseCookie.from(properties.session().cookieName(), rotated.sessionId())
                .httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.session().ttl())
                .build();
    }

    private LocalDate previousProcessedDay() {
        return LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(1);
    }
}
