package com.rke.backend.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.config.StorageProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Uploads files to S3-compatible object storage and returns the public URL.
 *
 * <p>The {@link S3Client} bean is {@code null} when storage credentials are not
 * configured — the upload method throws 501 in that case so callers get a clear
 * error rather than an NPE.
 */
@Service
public class StorageService {

    private final S3Client s3Client;
    private final StorageProperties props;

    public StorageService(StorageProperties props, @Autowired(required = false) @Nullable S3Client s3Client) {
        this.props = props;
        this.s3Client = s3Client;
    }

    /**
     * Uploads {@code bytes} to the configured S3 bucket under the {@code logos/} prefix
     * and returns the full public URL.
     *
     * @param originalFilename used only to preserve the file extension
     * @param bytes            raw file content
     * @param contentType      MIME type (e.g. {@code image/png})
     * @return public URL of the uploaded object
     */
    public String uploadLogo(String originalFilename, byte[] bytes, String contentType) {
        if (s3Client == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "S3 storage is not configured. Set S3_ACCESS_KEY, S3_BUCKET, and "
                            + "S3_PUBLIC_URL_BASE environment variables.");
        }

        String ext = StringUtils.getFilenameExtension(originalFilename);
        String key = "logos/" + UUID.randomUUID() + (ext != null ? "." + ext : "");

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes));

        String base = props.getPublicUrlBase().replaceAll("/$", "");
        return base + "/" + key;
    }
}
