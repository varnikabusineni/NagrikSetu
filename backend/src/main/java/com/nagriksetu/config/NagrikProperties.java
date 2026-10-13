package com.nagriksetu.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("nagrik")
public record NagrikProperties(
        Security security,
        Evidence evidence,
        Storage storage,
        WardMatching wardMatching,
        Demo demo) {

    public record Security(
            String jwtSecret,
            @DefaultValue("480") long jwtExpiryMinutes,
            @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
            @DefaultValue("600") long evidenceUrlTtlSeconds,
            RateLimit rateLimit) {
    }

    public record RateLimit(
            @DefaultValue("20") int authPerMinute,
            @DefaultValue("20") int complaintsPerHour,
            @DefaultValue("120") int writesPerMinute) {
    }

    public record Evidence(
            @DefaultValue("6") int maxFilesPerRequest,
            @DefaultValue("8388608") long maxFileSizeBytes) {
    }

    public record Storage(
            @DefaultValue("local") String provider,
            Local local,
            S3 s3,
            OrphanCleanup orphanCleanup) {
    }

    public record Local(@DefaultValue("./storage/evidence") String directory) {
    }

    public record S3(
            String bucket,
            @DefaultValue("ap-south-1") String region,
            String endpoint,
            @DefaultValue("false") boolean pathStyleAccess,
            String accessKey,
            String secretKey,
            @DefaultValue("evidence/") String keyPrefix) {
    }

    public record OrphanCleanup(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("60") long minAgeMinutes,
            @DefaultValue("0 17 3 * * *") String cron) {
    }

    public record WardMatching(@DefaultValue("true") boolean enabled) {
    }

    public record Demo(@DefaultValue("false") boolean enabled, String password) {
    }
}
