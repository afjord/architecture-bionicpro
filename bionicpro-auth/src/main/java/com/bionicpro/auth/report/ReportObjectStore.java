package com.bionicpro.auth.report;

import com.bionicpro.auth.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class ReportObjectStore {

    private static final String CONTENT_TYPE = "application/json";
    private static final String CACHE_CONTROL = "public, max-age=300";

    private final AuthProperties properties;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public ReportObjectStore(AuthProperties properties, S3Client s3Client, ObjectMapper objectMapper) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    public String objectKey(String keycloakUserId, LocalDate periodStart, LocalDate periodEnd) {
        return "reports/%s/%s_%s/report.json".formatted(keycloakUserId, periodStart, periodEnd);
    }

    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.reportStorage().bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    public void put(String objectKey, ReportView report) {
        try {
            byte[] reportBytes = objectMapper.writeValueAsBytes(report);
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.reportStorage().bucket())
                            .key(objectKey)
                            .contentType(CONTENT_TYPE)
                            .cacheControl(CACHE_CONTROL)
                            .build(),
                    RequestBody.fromBytes(reportBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Report cannot be written to object storage", ex);
        }
    }

    public String cdnUrl(String objectKey) {
        String encodedKey = URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
        return properties.reportStorage().cdnBaseUrl() + "/" + encodedKey;
    }
}
