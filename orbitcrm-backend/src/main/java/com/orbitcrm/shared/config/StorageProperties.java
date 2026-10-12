package com.orbitcrm.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding of the {@code orbitcrm.storage.*} settings consumed by {@link StorageConfig}.
 *
 * <p>Purpose: select and configure the object storage backend for attachments and import files.
 *
 * @author Abhay
 * @param type      {@code local} (files on disk) or {@code s3} (any S3-compatible store)
 * @param localPath root directory for local storage
 * @param s3        bucket, region, optional endpoint (MinIO/R2), credentials and path-style flag for S3 storage
 */
@ConfigurationProperties("orbitcrm.storage")
public record StorageProperties(String type, String localPath, S3 s3) {

    /**
     * S3 storage settings ({@code orbitcrm.storage.s3.*}).
     *
     * <p>Purpose: connect to AWS S3 or an S3-compatible store (MinIO, Cloudflare R2).
     *
     * @param bucket    bucket name
     * @param region    region (default {@code ap-south-1})
     * @param endpoint  custom endpoint for S3-compatible stores; empty for AWS
     * @param accessKey access key; empty to use the default AWS credential chain
     * @param secretKey secret key
     * @param pathStyle whether to use path-style URLs (needed by MinIO)
     */
    public record S3(String bucket, String region, String endpoint, String accessKey, String secretKey,
                     boolean pathStyle) {
    }
}
