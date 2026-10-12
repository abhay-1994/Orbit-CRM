package com.orbitcrm.tenant.service.impl;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.tenant.dto.NewTenant;
import com.orbitcrm.tenant.dto.TenantInfo;
import com.orbitcrm.tenant.dto.TenantSettingsUpdate;
import com.orbitcrm.tenant.entity.Tenant;
import com.orbitcrm.tenant.event.TenantCreatedEvent;
import com.orbitcrm.tenant.repository.TenantRepository;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Implementation of {@link com.orbitcrm.tenant.service.TenantService}.
 *
 * <p>Purpose: provision and maintain tenants.
 * <ul>
 *   <li>Provisioning validates the slug (3-40 characters, lowercase letters, digits and hyphens, not a reserved
 *   word such as {@code www} or {@code api}), checks uniqueness, applies regional defaults (Asia/Kolkata, INR,
 *   en-IN), saves the tenant and publishes {@link com.orbitcrm.tenant.event.TenantCreatedEvent}.</li>
 *   <li>Settings updates validate time zone, currency and locale and are recorded in the audit log.</li>
 *   <li>Lookups by id and slug, slug availability for the signup form and the list of active tenant ids.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class TenantServiceImpl implements TenantService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$");
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "api", "app", "admin", "auth", "login", "signup", "mail", "support", "status",
            "docs", "static", "assets", "cdn", "orbit", "orbitcrm", "system", "root");

    private final TenantRepository tenants;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    TenantServiceImpl(TenantRepository tenants, ApplicationEventPublisher events, AuditService audit) {
        this.tenants = tenants;
        this.events = events;
        this.audit = audit;
    }

    @Override
    @Transactional
    public TenantInfo create(NewTenant request) {
        UUID bound = TenantContext.requireTenantId();
        if (!bound.equals(request.id())) {
            throw new IllegalStateException("Tenant context must be bound to the tenant being created");
        }
        String slug = normalizeSlug(request.slug());
        validateSlug(slug);
        if (tenants.existsBySlug(slug)) {
            throw ApiException.conflict("TENANT_SLUG_TAKEN", "This workspace URL is already taken");
        }
        Tenant tenant = new Tenant(request.id(), slug, request.name().trim(),
                validTimezone(request.timezone()), validCurrency(request.currency()), validLocale(request.locale()));
        tenants.saveAndFlush(tenant);
        events.publishEvent(new TenantCreatedEvent(tenant.getId(), slug));
        return tenant.toInfo();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantInfo> findBySlug(String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return tenants.findBySlug(normalizeSlug(slug)).map(Tenant::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantInfo get(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::toInfo)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "Tenant does not exist"));
    }

    @Override
    @Transactional(readOnly = true)
    public TenantInfo current() {
        return get(TenantContext.requireTenantId());
    }

    @Override
    @Transactional
    public TenantInfo updateSettings(TenantSettingsUpdate update) {
        Tenant tenant = tenants.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "Tenant does not exist"));
        if (update.name() != null) {
            tenant.rename(update.name().trim());
        }
        if (update.timezone() != null) {
            tenant.changeTimezone(validTimezone(update.timezone()));
        }
        if (update.currency() != null) {
            tenant.changeCurrency(validCurrency(update.currency()));
        }
        if (update.locale() != null) {
            tenant.changeLocale(validLocale(update.locale()));
        }
        if (update.fiscalYearStartMonth() != null) {
            tenant.changeFiscalYearStartMonth(update.fiscalYearStartMonth());
        }
        TenantInfo saved = tenants.saveAndFlush(tenant).toInfo();
        audit.record(AuditEntry.security(AuditAction.SETTINGS_UPDATED, null, null, Map.of("tenant", saved.name())));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSlugAvailable(String slug) {
        String normalized = normalizeSlug(slug);
        return SLUG.matcher(normalized).matches()
                && !RESERVED_SLUGS.contains(normalized)
                && !tenants.existsBySlug(normalized);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> allTenantIds() {
        return tenants.findActiveIds();
    }

    public static String normalizeSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    }

    public static void validateSlug(String slug) {
        if (!SLUG.matcher(slug).matches()) {
            throw ApiException.validation("tenantSlug", "INVALID_FORMAT",
                    "Use 3-40 lowercase letters, digits or hyphens, starting and ending with a letter or digit");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw ApiException.validation("tenantSlug", "RESERVED", "This workspace URL is reserved");
        }
    }

    private static String validTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Asia/Kolkata";
        }
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException ex) {
            throw ApiException.validation("timezone", "INVALID", "Unknown time zone: " + timezone);
        }
    }

    private static String validCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "INR";
        }
        try {
            return Currency.getInstance(currency.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("currency", "INVALID", "Unknown ISO 4217 currency: " + currency);
        }
    }

    private static String validLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en-IN";
        }
        try {
            return new Locale.Builder().setLanguageTag(locale.trim()).build().toLanguageTag();
        } catch (IllformedLocaleException ex) {
            throw ApiException.validation("locale", "INVALID", "Invalid locale tag: " + locale);
        }
    }
}
