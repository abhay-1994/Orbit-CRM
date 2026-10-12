package com.orbitcrm.shared.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.orbitcrm.shared.service.StorageService;

/**
 * {@link com.orbitcrm.shared.service.StorageService} implementation that stores objects as files on the local disk.
 *
 * <p>Purpose: provide object storage without external infrastructure for local development, tests and
 * single-server installations. Storage keys map to relative paths under the configured root directory; every
 * resolved path is checked to stay inside the root, so a malformed key can never escape it. Created by
 * {@code StorageConfig} when {@code orbitcrm.storage.type=local}.
 *
 * @author Abhay
 */
public final class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(Path root) {
        this.root = root;
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store " + key, ex);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete " + key, ex);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
