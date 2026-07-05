package com.rke.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * S3-compatible object storage configuration. All values read from environment
 * variables via application.yml — no credentials ever appear in source code.
 *
 * <p>When {@code accessKey} is blank the {@code S3Config} bean skips building
 * the client, and logo-upload calls will fail with a clear error instead of
 * silently misconfiguring.
 */
@Component
@ConfigurationProperties(prefix = "storage.s3")
@Getter
@Setter
public class StorageProperties {

    /** Optional endpoint override for non-AWS S3-compatible services (e.g., Cloudflare R2, MinIO). */
    private String endpoint = "";

    private String region = "us-east-1";

    private String accessKey = "";

    private String secretKey = "";

    private String bucket = "rke-assets";

    /**
     * Base URL prepended to the object key to form the public logo URL.
     * e.g. {@code https://pub-xyz.r2.dev} or
     * {@code https://rke-assets.s3.us-east-1.amazonaws.com}.
     */
    private String publicUrlBase = "";

    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank()
                && bucket != null && !bucket.isBlank()
                && publicUrlBase != null && !publicUrlBase.isBlank();
    }
}
