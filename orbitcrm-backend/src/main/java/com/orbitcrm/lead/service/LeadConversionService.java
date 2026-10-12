package com.orbitcrm.lead.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.lead.config.LeadModule;
import com.orbitcrm.lead.dto.ConversionPreview;
import com.orbitcrm.lead.dto.ConversionRequest;
import com.orbitcrm.lead.dto.ConversionResult;
import com.orbitcrm.lead.dto.ConversionSuggestion;
import com.orbitcrm.lead.dto.ConversionTarget;
import com.orbitcrm.lead.dto.TargetAction;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FilterOperator;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.event.RecordsRelinked;
import com.orbitcrm.record.model.ChangeOrigin;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.model.WriteOptions;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;

/**
 * Converts a lead into a contact, a company and optionally a deal (PRD section 10).
 *
 * <p>Purpose: implement the most important sales hand-over in one transaction - either everything is created and
 * linked, or nothing is:
 * <ol>
 *   <li>checks lead update permission and that the lead is not already converted;</li>
 *   <li>resolves each target: creates it from the lead's data (with user overrides) or links an existing record the
 *   user can see; created records go through the generic engine, so permissions, validation, audit and search apply;</li>
 *   <li>creates the optional deal as a system write so its read-only link to the source lead is set (overrides may
 *   not touch read-only fields);</li>
 *   <li>marks the lead converted and stores the links to the new records;</li>
 *   <li>publishes {@code RecordsRelinked} so the lead's notes, activities and attachments move to the new contact (or
 *   company);</li>
 *   <li>stores a snapshot of the lead in {@code lead_conversions} and writes a {@code CONVERT} audit entry.</li>
 * </ol>
 * It also builds the conversion preview with suggested existing companies and contacts.
 *
 * @author Abhay
 */
@Service
public class LeadConversionService {

    public static final String COMPANIES = "companies";
    public static final String CONTACTS = "contacts";
    public static final String DEALS = "deals";

    private final RecordService records;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final ModuleRegistry registry;

    LeadConversionService(RecordService records, AuditService audit, ApplicationEventPublisher events,
                          NamedParameterJdbcTemplate jdbc, JsonMapper json, ModuleRegistry registry) {
        this.records = records;
        this.audit = audit;
        this.events = events;
        this.jdbc = jdbc;
        this.json = json;
        this.registry = registry;
    }

    // ------------------------------------------------------------------ preview

    @Transactional(readOnly = true)
    public ConversionPreview preview(UUID leadId) {
        RecordView lead = records.get(LeadModule.KEY, leadId);
        CurrentUser user = AccessControl.requireUser();
        List<ConversionSuggestion> companies = List.of();
        String companyName = lead.string("companyName");
        if (companyName != null && user.can(COMPANIES, Action.READ)) {
            companies = suggestions(COMPANIES, FilterNode.condition("name", FilterOperator.CONTAINS, companyName));
        }
        List<ConversionSuggestion> contacts = List.of();
        String email = lead.string("email");
        if (email != null && user.can(CONTACTS, Action.READ)) {
            contacts = suggestions(CONTACTS, FilterNode.eq("email", email));
        }
        return new ConversionPreview(companyValues(lead), contactValues(lead, null), dealValues(lead, null, null),
                companies, contacts);
    }

    // ------------------------------------------------------------------ convert

    @Transactional
    public ConversionResult convert(UUID leadId, ConversionRequest request) {
        CurrentUser user = AccessControl.requireUser();
        AccessControl.require(LeadModule.KEY, Action.UPDATE);
        RecordView lead = records.get(LeadModule.KEY, leadId);
        if (LeadModule.STATUS_CONVERTED.equals(lead.value("status"))) {
            throw ApiException.conflict("LEAD_ALREADY_CONVERTED", "This lead has already been converted");
        }
        ConversionRequest effective = withDefaults(lead, request);
        if (effective.company().action() == TargetAction.NONE && effective.contact().action() == TargetAction.NONE) {
            throw ApiException.validation("contact", "CONVERSION_TARGET_REQUIRED",
                    "Create or link at least a contact or a company");
        }
        UUID owner = effective.ownerId() != null ? effective.ownerId() : lead.uuid("ownerId");
        WriteOptions options = WriteOptions.of(ChangeOrigin.CONVERSION);

        RecordView company = resolve(COMPANIES, effective.company(), () -> {
            Map<String, Object> values = companyValues(lead);
            if (values.get("name") == null && (effective.company().values() == null
                    || effective.company().values().get("name") == null)) {
                throw ApiException.validation("company.name", "REQUIRED",
                        "The lead has no company name; provide one or skip creating a company");
            }
            return values;
        }, owner, options);
        RecordView contact = resolve(CONTACTS, effective.contact(),
                () -> contactValues(lead, company == null ? null : company.id()), owner, options);

        RecordView deal = null;
        if (effective.deal().action() == TargetAction.CREATE) {
            // System write so the read-only link to the source lead is kept; user overrides may not touch
            // read-only fields.
            deal = records.create(DEALS, merge(dealValues(lead, company, contact),
                    withoutReadOnly(DEALS, effective.deal().values()), owner), WriteOptions.system(ChangeOrigin.CONVERSION));
        } else if (effective.deal().action() == TargetAction.EXISTING) {
            throw ApiException.validation("deal.action", "INVALID", "A deal can only be created or skipped");
        }

        Map<String, Object> leadChanges = new LinkedHashMap<>();
        leadChanges.put("status", LeadModule.STATUS_CONVERTED);
        leadChanges.put("convertedAt", Instant.now());
        leadChanges.put("convertedCompanyId", company == null ? null : company.id());
        leadChanges.put("convertedContactId", contact == null ? null : contact.id());
        leadChanges.put("convertedDealId", deal == null ? null : deal.id());
        records.update(LeadModule.KEY, leadId, leadChanges, null, WriteOptions.system(ChangeOrigin.CONVERSION));

        RecordView relinkTarget = contact != null ? contact : company;
        events.publishEvent(new RecordsRelinked(user.tenantId(), LeadModule.KEY, leadId,
                relinkTarget.module(), relinkTarget.id(), ChangeOrigin.CONVERSION));

        boolean companyCreated = effective.company().action() == TargetAction.CREATE;
        boolean contactCreated = effective.contact().action() == TargetAction.CREATE;
        saveHistory(lead, company, companyCreated, contact, contactCreated, deal, user);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("companyId", company == null ? null : company.id().toString());
        metadata.put("companyCreated", companyCreated);
        metadata.put("contactId", contact == null ? null : contact.id().toString());
        metadata.put("contactCreated", contactCreated);
        metadata.put("dealId", deal == null ? null : deal.id().toString());
        audit.record(AuditEntry.record(AuditAction.CONVERT, LeadModule.KEY, leadId, lead.title(), null)
                .withMetadata(metadata));
        return new ConversionResult(leadId, company, companyCreated, contact, contactCreated, deal, deal != null);
    }

    // ------------------------------------------------------------------ helpers

    private RecordView resolve(String module, ConversionTarget target, java.util.function.Supplier<Map<String, Object>> mapped,
                               UUID owner, WriteOptions options) {
        return switch (target.action()) {
            case NONE -> null;
            case EXISTING -> {
                if (target.id() == null) {
                    throw ApiException.validation(singular(module) + ".id", "REQUIRED",
                            "Choose the existing " + singular(module) + " to link");
                }
                yield records.find(module, target.id()).orElseThrow(() -> ApiException.validation(
                        singular(module) + ".id", "INVALID_REFERENCE", "The selected " + singular(module)
                                + " does not exist"));
            }
            case CREATE -> records.create(module, merge(mapped.get(), target.values(), owner), options);
        };
    }

    private Map<String, Object> withoutReadOnly(String module, Map<String, Object> overrides) {
        if (overrides == null) {
            return null;
        }
        ModuleDefinition definition = registry.require(module);
        Map<String, Object> filtered = new LinkedHashMap<>(overrides);
        filtered.keySet().removeIf(key -> definition.field(key).map(FieldDefinition::readOnly).orElse(false));
        return filtered;
    }

    private static Map<String, Object> merge(Map<String, Object> mapped, Map<String, Object> overrides, UUID owner) {
        Map<String, Object> values = new LinkedHashMap<>(mapped);
        values.values().removeIf(java.util.Objects::isNull);
        if (overrides != null) {
            values.putAll(overrides);
        }
        values.putIfAbsent("ownerId", owner);
        return values;
    }

    private static ConversionRequest withDefaults(RecordView lead, ConversionRequest request) {
        ConversionRequest r = request == null ? new ConversionRequest(null, null, null, null) : request;
        ConversionTarget company = r.company() != null && r.company().action() != null ? r.company()
                : new ConversionTarget(lead.value("companyName") != null ? TargetAction.CREATE : TargetAction.NONE, null, null);
        ConversionTarget contact = r.contact() != null && r.contact().action() != null ? r.contact()
                : new ConversionTarget(TargetAction.CREATE, null, null);
        ConversionTarget deal = r.deal() != null && r.deal().action() != null ? r.deal() : new ConversionTarget(TargetAction.NONE, null, null);
        return new ConversionRequest(company, contact, deal, r.ownerId());
    }

    private static Map<String, Object> companyValues(RecordView lead) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", lead.value("companyName"));
        values.put("industry", lead.value("industry"));
        values.put("website", lead.value("website"));
        values.put("phone", lead.value("phone"));
        values.put("billingStreet", lead.value("street"));
        values.put("billingCity", lead.value("city"));
        values.put("billingState", lead.value("state"));
        values.put("billingPostalCode", lead.value("postalCode"));
        values.put("billingCountry", lead.value("country"));
        values.put("tags", lead.value("tags"));
        return values;
    }

    private static Map<String, Object> contactValues(RecordView lead, UUID companyId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("firstName", lead.value("firstName"));
        values.put("lastName", lead.value("lastName"));
        values.put("email", lead.value("email"));
        values.put("phone", lead.value("phone"));
        values.put("mobile", lead.value("mobile"));
        values.put("jobTitle", lead.value("jobTitle"));
        values.put("leadSource", lead.value("leadSource"));
        values.put("mailingStreet", lead.value("street"));
        values.put("mailingCity", lead.value("city"));
        values.put("mailingState", lead.value("state"));
        values.put("mailingPostalCode", lead.value("postalCode"));
        values.put("mailingCountry", lead.value("country"));
        values.put("emailOptOut", lead.value("emailOptOut"));
        values.put("description", lead.value("description"));
        values.put("tags", lead.value("tags"));
        values.put("companyId", companyId);
        return values;
    }

    private static Map<String, Object> dealValues(RecordView lead, RecordView company, RecordView contact) {
        Map<String, Object> values = new LinkedHashMap<>();
        String base = lead.value("companyName") != null ? lead.string("companyName") : lead.title();
        values.put("name", base + " - Deal");
        values.put("companyId", company == null ? null : company.id());
        values.put("contactId", contact == null ? null : contact.id());
        values.put("leadSource", lead.value("leadSource"));
        values.put("leadId", lead.id());
        return values;
    }

    private List<ConversionSuggestion> suggestions(String module, FilterNode filter) {
        return records.search(module, new SearchRequest(filter, null, null, 0, 5)).content().stream()
                .map(r -> new ConversionSuggestion(r.id(), r.title(), r.recordNumber()))
                .toList();
    }

    private void saveHistory(RecordView lead, RecordView company, boolean companyCreated, RecordView contact,
                             boolean contactCreated, RecordView deal, CurrentUser user) {
        jdbc.update("""
                INSERT INTO lead_conversions (id, tenant_id, lead_id, contact_id, company_id, deal_id, contact_created,
                                              company_created, deal_created, converted_by, converted_at, lead_snapshot)
                VALUES (:id, :tenantId, :leadId, :contactId, :companyId, :dealId, :contactCreated, :companyCreated,
                        :dealCreated, :by, :at, CAST(:snapshot AS jsonb))
                """, new MapSqlParameterSource()
                .addValue("id", UuidV7.generate())
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("leadId", lead.id())
                .addValue("contactId", contact == null ? null : contact.id())
                .addValue("companyId", company == null ? null : company.id())
                .addValue("dealId", deal == null ? null : deal.id())
                .addValue("contactCreated", contactCreated)
                .addValue("companyCreated", companyCreated)
                .addValue("dealCreated", deal != null)
                .addValue("by", user.userId())
                .addValue("at", OffsetDateTime.now(ZoneOffset.UTC))
                .addValue("snapshot", json.writeValueAsString(lead.values())));
    }

    private static String singular(String module) {
        return switch (module) {
            case COMPANIES -> "company";
            case CONTACTS -> "contact";
            default -> module.toLowerCase(Locale.ROOT);
        };
    }
}
