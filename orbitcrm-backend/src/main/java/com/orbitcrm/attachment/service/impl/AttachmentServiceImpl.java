package com.orbitcrm.attachment.service.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.orbitcrm.attachment.dto.AttachmentDownload;
import com.orbitcrm.attachment.dto.AttachmentView;
import com.orbitcrm.attachment.service.AttachmentService;
import com.orbitcrm.attachment.util.FileTypeDetector;
import com.orbitcrm.identity.service.UserDirectory;
import com.orbitcrm.record.event.RecordsRelinked;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.service.StorageService;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.AfterCommit;
import com.orbitcrm.shared.util.UuidV7;

/**
 * Implementation of file attachments on records (PRD section 39).
 *
 * <p>Purpose: store files safely and tie their visibility to the owning record:
 * <ul>
 *   <li><b>Upload</b> requires edit access to the record. The content type is decided from the file's leading bytes
 *   by {@link com.orbitcrm.attachment.util.FileTypeDetector} (allow-list, extension must agree); the file name is
 *   sanitized; the content is streamed to object storage under a server-generated, tenant-prefixed key
 *   ({@code tenants/<tenantId>/attachments/<id>}) while its SHA-256 is computed. If the transaction rolls back, the
 *   stored object is removed again.</li>
 *   <li><b>List and download</b> require read access to the record.</li>
 *   <li><b>Delete</b> is a soft delete of the row, allowed for the uploader (with read access) or anyone who can edit
 *   the record; the stored object is deleted after commit.</li>
 *   <li>On {@code RecordsRelinked} (e.g. lead conversion) attachments move to the target record.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);
    private static final int SNIFF_BYTES = 8192;
    private static final String COLUMNS = "id, module_key, record_id, file_name, content_type, size_bytes, storage_key, "
            + "sha256, uploaded_by, created_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final RecordService records;
    private final StorageService storage;
    private final UserDirectory users;

    AttachmentServiceImpl(NamedParameterJdbcTemplate jdbc, RecordService records, StorageService storage,
                          UserDirectory users) {
        this.jdbc = jdbc;
        this.records = records;
        this.storage = storage;
        this.users = users;
    }

    @Transactional
    public AttachmentView upload(String moduleKey, UUID recordId, MultipartFile file) {
        records.requireAccess(moduleKey, recordId, Action.UPDATE);
        CurrentUser user = AccessControl.requireUser();
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file", "REQUIRED", "Choose a file to upload");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        UUID id = UuidV7.generate();
        UUID tenantId = TenantContext.requireTenantId();
        String key = "tenants/" + tenantId + "/attachments/" + id;
        String contentType;
        String sha256;
        try (InputStream raw = new BufferedInputStream(file.getInputStream(), SNIFF_BYTES)) {
            raw.mark(SNIFF_BYTES);
            byte[] head = raw.readNBytes(SNIFF_BYTES);
            raw.reset();
            contentType = FileTypeDetector.detect(head, fileName).orElseThrow(() -> new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Allowed: PDF, images (PNG, JPEG, GIF, WebP), Office documents, CSV and text files"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream hashing = new DigestInputStream(raw, digest)) {
                storage.put(key, hashing, file.getSize(), contentType);
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        // If the transaction rolls back, remove the orphaned object.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            deleteQuietly(key);
                        }
                    }
                });
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO attachments (id, tenant_id, module_key, record_id, file_name, content_type, size_bytes,
                                         storage_key, sha256, uploaded_by, created_at, updated_at, version)
                VALUES (:id, :tenantId, :module, :recordId, :fileName, :contentType, :size, :key, :sha, :user, :now, :now, 0)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", tenantId).addValue("module", moduleKey)
                .addValue("recordId", recordId).addValue("fileName", fileName).addValue("contentType", contentType)
                .addValue("size", file.getSize()).addValue("key", key).addValue("sha", sha256)
                .addValue("user", user.userId()).addValue("now", now));
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> list(String moduleKey, UUID recordId) {
        records.requireAccess(moduleKey, recordId, Action.READ);
        return withNames(jdbc.query("SELECT " + COLUMNS + " FROM attachments WHERE tenant_id = :tenantId "
                + "AND module_key = :module AND record_id = :recordId AND deleted_at IS NULL ORDER BY created_at DESC",
                forRecord(moduleKey, recordId), mapper()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentView> recent(String moduleKey, UUID recordId, Instant before, int limit) {
        return withNames(jdbc.query("SELECT " + COLUMNS + " FROM attachments WHERE tenant_id = :tenantId "
                        + "AND module_key = :module AND record_id = :recordId AND deleted_at IS NULL AND created_at < :before "
                        + "ORDER BY created_at DESC LIMIT :limit",
                forRecord(moduleKey, recordId).addValue("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC))
                        .addValue("limit", limit), mapper()));
    }

    /** Opens the file for streaming after checking read access to the owning record. */
    @Transactional(readOnly = true)
    public AttachmentDownload open(UUID attachmentId) {
        Stored stored = find(attachmentId);
        records.requireAccess(stored.view().moduleKey(), stored.view().recordId(), Action.READ);
        return new AttachmentDownload(stored.view(), storage.get(stored.key()));
    }

    @Transactional
    public void delete(UUID attachmentId) {
        Stored stored = find(attachmentId);
        CurrentUser user = AccessControl.requireUser();
        if (!user.userId().equals(stored.view().uploadedBy())) {
            records.requireAccess(stored.view().moduleKey(), stored.view().recordId(), Action.UPDATE);
        } else {
            records.requireAccess(stored.view().moduleKey(), stored.view().recordId(), Action.READ);
        }
        jdbc.update("UPDATE attachments SET deleted_at = :now WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", attachmentId).addValue("tenantId", TenantContext.requireTenantId())
                        .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));
        AfterCommit.run(() -> deleteQuietly(stored.key()));
    }

    @EventListener
    void onRelinked(RecordsRelinked event) {
        jdbc.update("UPDATE attachments SET module_key = :toModule, record_id = :toId WHERE tenant_id = :tenantId "
                        + "AND module_key = :fromModule AND record_id = :fromId",
                new MapSqlParameterSource("tenantId", event.tenantId()).addValue("toModule", event.toModule())
                        .addValue("toId", event.toId()).addValue("fromModule", event.fromModule())
                        .addValue("fromId", event.fromId()));
    }

    private AttachmentView get(UUID id) {
        return find(id).view();
    }

    private Stored find(UUID id) {
        List<Stored> rows = jdbc.query("SELECT " + COLUMNS + " FROM attachments WHERE id = :id AND tenant_id = :tenantId "
                        + "AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId()),
                (rs, n) -> new Stored(mapper().mapRow(rs, n), rs.getString("storage_key")));
        if (rows.isEmpty()) {
            throw ApiException.notFound("ATTACHMENT_NOT_FOUND", "Attachment does not exist");
        }
        Stored stored = rows.get(0);
        return new Stored(withNames(List.of(stored.view())).get(0), stored.key());
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Could not delete stored object {}: {}", key, ex.getMessage());
        }
    }

    private List<AttachmentView> withNames(List<AttachmentView> views) {
        Set<UUID> uploaders = new HashSet<>();
        views.forEach(v -> uploaders.add(v.uploadedBy()));
        Map<UUID, String> names = users.displayNames(uploaders);
        return views.stream().map(v -> new AttachmentView(v.id(), v.moduleKey(), v.recordId(), v.fileName(),
                v.contentType(), v.sizeBytes(), v.sha256(), v.uploadedBy(), names.get(v.uploadedBy()), v.createdAt()))
                .toList();
    }

    public static String cleanFileName(String original) {
        String name = original == null ? "file" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static MapSqlParameterSource forRecord(String moduleKey, UUID recordId) {
        return new MapSqlParameterSource("tenantId", TenantContext.requireTenantId())
                .addValue("module", moduleKey).addValue("recordId", recordId);
    }

    private static RowMapper<AttachmentView> mapper() {
        return (rs, n) -> new AttachmentView(rs.getObject("id", UUID.class), rs.getString("module_key"),
                rs.getObject("record_id", UUID.class), rs.getString("file_name"), rs.getString("content_type"),
                rs.getLong("size_bytes"), rs.getString("sha256"), rs.getObject("uploaded_by", UUID.class), null,
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    /**
     * An attachment row together with its internal storage key.
     *
     * <p>Purpose: keep the object storage key next to the public {@code AttachmentView} inside the service, so the key is
     * available for streaming and deletion but never exposed through the API.
     *
     * @param view the attachment metadata returned to clients
     * @param key  the object storage key of the file content
     */
    private record Stored(AttachmentView view, String key) {
    }
}
