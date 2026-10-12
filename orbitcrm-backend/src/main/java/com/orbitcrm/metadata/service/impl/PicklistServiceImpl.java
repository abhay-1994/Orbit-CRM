package com.orbitcrm.metadata.service.impl;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbitcrm.metadata.dto.PicklistValueView;
import com.orbitcrm.metadata.entity.PicklistValue;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.model.PicklistOption;
import com.orbitcrm.metadata.repository.PicklistValueRepository;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.metadata.service.PicklistService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.AfterCommit;
import com.orbitcrm.tenant.event.TenantCreatedEvent;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Implementation of {@link com.orbitcrm.metadata.service.PicklistService} plus picklist administration and seeding.
 *
 * <p>Purpose: manage tenant picklist values efficiently and safely:
 * <ul>
 *   <li><b>Reads</b> are cached per tenant and picklist (5 minutes); the cache is invalidated immediately and again
 *   after commit whenever values change.</li>
 *   <li><b>Administration</b> (used by {@code PicklistController}): add a value with a unique label and a key derived
 *   from it, update label, color, active and default flags (system values cannot be deactivated; only one default
 *   per picklist), and reorder all values.</li>
 *   <li><b>Seeding</b>: when a tenant is created, the default values of every declared picklist are inserted; at
 *   application startup, picklists added by a new release are seeded into all existing tenants.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class PicklistServiceImpl implements PicklistService {

    private static final Logger log = LoggerFactory.getLogger(PicklistServiceImpl.class);

    private final PicklistValueRepository repository;
    private final ModuleRegistry registry;
    private final TenantService tenants;
    private final TransactionTemplate tx;
    private final Cache<CacheKey, List<PicklistValueView>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(20_000)
            .build();

    PicklistServiceImpl(PicklistValueRepository repository, ModuleRegistry registry, TenantService tenants,
                        PlatformTransactionManager txManager) {
        this.repository = repository;
        this.registry = registry;
        this.tenants = tenants;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ reads (cached per tenant)

    @Override
    public List<PicklistValueView> values(String picklistKey) {
        UUID tenantId = TenantContext.requireTenantId();
        return cache.get(new CacheKey(tenantId, picklistKey), key -> tx.execute(status ->
                repository.findByPicklistKeyOrderByPositionAsc(picklistKey).stream().map(PicklistServiceImpl::view)
                        .toList()));
    }

    @Override
    public boolean isActiveValue(String picklistKey, String valueKey) {
        return values(picklistKey).stream().anyMatch(v -> v.active() && v.key().equals(valueKey));
    }

    @Override
    public String label(String picklistKey, String valueKey) {
        if (valueKey == null) {
            return null;
        }
        return values(picklistKey).stream().filter(v -> v.key().equals(valueKey)).map(PicklistValueView::label)
                .findFirst().orElse(valueKey);
    }

    @Override
    public String defaultValue(String picklistKey) {
        return values(picklistKey).stream().filter(v -> v.isDefault() && v.active()).map(PicklistValueView::key)
                .findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ administration

    @Transactional
    public PicklistValueView addValue(String picklistKey, String label, String color, boolean isDefault) {
        requireKnownPicklist(picklistKey);
        List<PicklistValue> existing = repository.findByPicklistKeyOrderByPositionAsc(picklistKey);
        String cleanLabel = cleanLabel(label);
        if (existing.stream().anyMatch(v -> v.getLabel().equalsIgnoreCase(cleanLabel))) {
            throw ApiException.conflict("PICKLIST_VALUE_EXISTS", "A value with this label already exists");
        }
        Set<String> keys = existing.stream().map(PicklistValue::getValueKey).collect(Collectors.toSet());
        String key = uniqueKey(slug(cleanLabel), keys);
        if (isDefault) {
            existing.forEach(v -> v.setDefault(false));
        }
        int position = existing.stream().mapToInt(PicklistValue::getPosition).max().orElse(0) + 1;
        PicklistValue value = repository.save(new PicklistValue(picklistKey, key, cleanLabel, color, position,
                isDefault, false));
        evict(picklistKey);
        return view(value);
    }

    @Transactional
    public PicklistValueView updateValue(String picklistKey, UUID valueId, String label, String color, Boolean active,
                                  Boolean isDefault) {
        PicklistValue value = repository.findById(valueId)
                .filter(v -> v.getPicklistKey().equals(picklistKey))
                .orElseThrow(() -> ApiException.notFound("PICKLIST_VALUE_NOT_FOUND", "Picklist value does not exist"));
        if (label != null) {
            value.relabel(cleanLabel(label));
        }
        if (color != null) {
            value.recolor(color.isBlank() ? null : color);
        }
        if (active != null) {
            if (!active && value.isSystem()) {
                throw ApiException.conflict("PICKLIST_VALUE_SYSTEM", "System values cannot be deactivated");
            }
            value.setActive(active);
        }
        if (Boolean.TRUE.equals(isDefault)) {
            repository.findByPicklistKeyOrderByPositionAsc(picklistKey).forEach(v -> v.setDefault(false));
            value.setDefault(true);
        } else if (Boolean.FALSE.equals(isDefault)) {
            value.setDefault(false);
        }
        repository.flush();
        evict(picklistKey);
        return view(value);
    }

    @Transactional
    public List<PicklistValueView> reorder(String picklistKey, List<UUID> orderedIds) {
        List<PicklistValue> values = repository.findByPicklistKeyOrderByPositionAsc(picklistKey);
        Map<UUID, PicklistValue> byId = values.stream().collect(Collectors.toMap(PicklistValue::getId,
                Function.identity()));
        if (orderedIds.size() != values.size() || !byId.keySet().equals(new HashSet<>(orderedIds))) {
            throw ApiException.validation("valueIds", "INVALID", "Provide every value id of the picklist exactly once");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).moveTo(i + 1);
        }
        repository.flush();
        evict(picklistKey);
        return values(picklistKey);
    }

    public List<PicklistDefinition> definitions() {
        return registry.picklists();
    }

    public PicklistDefinition requireKnownPicklist(String picklistKey) {
        return registry.picklists().stream().filter(p -> p.key().equals(picklistKey)).findFirst()
                .orElseThrow(() -> ApiException.notFound("PICKLIST_NOT_FOUND", "Unknown picklist: " + picklistKey));
    }

    // ------------------------------------------------------------------ seeding

    @EventListener
    void onTenantCreated(TenantCreatedEvent event) {
        seedMissing();
    }

    /** New picklists shipped with a release are seeded into existing tenants at startup. */
    @EventListener(ApplicationReadyEvent.class)
    void syncExistingTenants() {
        List<UUID> tenantIds = TenantContext.callAsSystem(tenants::allTenantIds);
        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(status -> seedMissing()));
            } catch (RuntimeException ex) {
                log.error("Failed to sync picklists for tenant {}", tenantId, ex);
            }
        }
    }

    private void seedMissing() {
        Set<String> seeded = new HashSet<>(repository.findSeededKeys());
        for (PicklistDefinition definition : registry.picklists()) {
            if (seeded.contains(definition.key())) {
                continue;
            }
            int position = 1;
            for (PicklistOption option : definition.defaults()) {
                repository.save(new PicklistValue(definition.key(), option.key(), option.label(), option.color(),
                        position++, option.isDefault(), option.system()));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void evict(String picklistKey) {
        CacheKey key = new CacheKey(TenantContext.requireTenantId(), picklistKey);
        cache.invalidate(key);
        AfterCommit.run(() -> cache.invalidate(key));
    }

    private static String cleanLabel(String label) {
        if (label == null || label.isBlank()) {
            throw ApiException.validation("label", "REQUIRED", "Label is required");
        }
        String trimmed = label.trim();
        if (trimmed.length() > 120) {
            throw ApiException.validation("label", "INVALID_LENGTH", "Label must be at most 120 characters");
        }
        return trimmed;
    }

    public static String slug(String label) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "value";
        }
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }

    private static String uniqueKey(String base, Set<String> taken) {
        String candidate = base;
        int suffix = 2;
        while (taken.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static PicklistValueView view(PicklistValue v) {
        return new PicklistValueView(v.getId(), v.getValueKey(), v.getLabel(), v.getColor(), v.getPosition(),
                v.isActive(), v.isDefault(), v.isSystem());
    }

    /**
     * Cache key for the values of one picklist in one tenant.
     *
     * <p>Purpose: keep cached picklist values strictly separated per tenant.
     *
     * @param tenantId    tenant the values belong to
     * @param picklistKey picklist key, e.g. {@code lead_status}
     */
    private record CacheKey(UUID tenantId, String picklistKey) {
    }
}
