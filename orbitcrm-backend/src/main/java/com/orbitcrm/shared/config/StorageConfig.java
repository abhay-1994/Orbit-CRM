package com.orbitcrm.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import com.orbitcrm.shared.service.StorageService;
import com.orbitcrm.shared.service.impl.LocalStorageService;

/**
 * Creates the {@link com.orbitcrm.shared.service.StorageService} used for binary content (attachments and
 * uploaded import files).
 *
 * <p>Purpose: let the rest of the application store files without caring where they live. Based on
 * {@code orbitcrm.storage.type}:
 * <ul>
 *   <li>{@code local} (default): a {@link com.orbitcrm.shared.service.impl.LocalStorageService} rooted at
 *   {@code orbitcrm.storage.local-path}; suitable for development, tests and single-server installs.</li>
 *   <li>{@code s3}: an AWS SDK v2 S3 client (URLConnection HTTP client) that works with AWS S3 and S3-compatible
 *   stores such as MinIO or Cloudflare R2 via a custom endpoint and optional path-style access. Credentials come
 *   from explicit keys or, when absent, the default AWS credential chain.</li>
 * </ul>
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    StorageService storageService(StorageProperties properties) {
        if ("s3".equalsIgnoreCase(properties.type())) {
            return s3(properties.s3());
        }
        Path root = Path.of(properties.localPath() == null ? "./data/storage" : properties.localPath())
                .toAbsolutePath().normalize();
        log.info("Using local file storage at {}", root);
        return new LocalStorageService(root);
    }

    private static StorageService s3(StorageProperties.S3 config) {
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(config.region() == null ? "us-east-1" : config.region()))
                .forcePathStyle(config.pathStyle());
        if (config.endpoint() != null && !config.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(config.endpoint()));
        }
        builder.credentialsProvider(config.accessKey() != null && !config.accessKey().isBlank()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey(), config.secretKey()))
                : DefaultCredentialsProvider.builder().build());
        S3Client client = builder.build();
        String bucket = config.bucket();
        log.info("Using S3 storage bucket {}", bucket);
        return new StorageService() {
            @Override
            public void put(String key, InputStream content, long size, String contentType) {
                client.putObject(b -> b.bucket(bucket).key(key).contentType(contentType),
                        RequestBody.fromInputStream(content, size));
            }

            @Override
            public InputStream get(String key) {
                return client.getObject(b -> b.bucket(bucket).key(key));
            }

            @Override
            public void delete(String key) {
                client.deleteObject(b -> b.bucket(bucket).key(key));
            }
        };
    }
}
