package com.orbitcrm.audit.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.dto.AuditLogView;
import com.orbitcrm.audit.dto.AuditQuery;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;

/**
 * Public API of the audit module.
 *
 * <p>Purpose: the only way to write and read the audit log. {@link #record} writes in the caller's transaction, so an
 * audited change and its audit entry commit or roll back together - there is never a change without its trail or a
 * trail entry for a change that did not happen. Read methods search the tenant's log, page through one record's
 * history, and return the latest entries of a record before a point in time for activity timelines.
 *
 * @author Abhay
 */
public interface AuditService {

    void record(AuditEntry entry);

    PageResponse<AuditLogView> search(AuditQuery query, PageParams page);

    PageResponse<AuditLogView> forRecord(String moduleKey, UUID recordId, PageParams page);

    /** Latest entries for a record, used to build activity timelines. */
    List<AuditLogView> recentForRecord(String moduleKey, UUID recordId, Instant before, int limit);
}
