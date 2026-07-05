package com.rke.backend.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Creates an {@link S3Client} bean only when {@code storage.s3.access-key} is
 * present in the environment. When the property is absent (local dev, or
 * storage not configured), the bean is not registered and {@link StorageService}
 * receives a {@code null} client via {@code @Autowired(required = false)},
 * causing logo-upload calls to return 501 instead of crashing at startup.
 */
@Configuration
public class S3Config {

    @Bean
    // Only create the S3 client when a non-empty access key is configured.
    // application.yml resolves the key to an empty string when the env var is
    // absent (local dev), so a plain @ConditionalOnProperty would still match
    // and fail on the blank key — hence the explicit non-empty check here.
    @ConditionalOnExpression("'${storage.s3.access-key:}' != ''")
    public S3Client s3Client(StorageProperties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));

        if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpoint()))
                    .forcePathStyle(true);  // required for MinIO / R2 with custom endpoint
        }

        return builder.build();
    }
}
