package com.orbitcrm.identity.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.orbitcrm.shared.security.SecuredResource;
import com.orbitcrm.shared.security.SecuredResourceProvider;

/**
 * Registry of every resource a role can be granted permissions on.
 *
 * <p>Purpose: collect the resources contributed by all {@code SecuredResourceProvider}s (CRM modules from the
 * metadata registry, administrative resources from {@code IdentityResources}) into one ordered catalog, failing at
 * startup on duplicate keys. It is used to render the role editor, validate role permissions, seed default roles
 * and spell out administrator permissions.
 *
 * @author Abhay
 */
@Component
public class PermissionCatalog {

    private final Map<String, SecuredResource> resources;
    private final List<SecuredResource> ordered;

    PermissionCatalog(List<SecuredResourceProvider> providers) {
        Map<String, SecuredResource> map = new LinkedHashMap<>();
        providers.stream()
                .flatMap(p -> p.securedResources().stream())
                .sorted(Comparator.comparing(SecuredResource::category).thenComparing(SecuredResource::label))
                .forEach(r -> {
                    if (map.putIfAbsent(r.key(), r) != null) {
                        throw new IllegalStateException("Duplicate secured resource key: " + r.key());
                    }
                });
        this.resources = Map.copyOf(map);
        this.ordered = List.copyOf(map.values());
    }

    public List<SecuredResource> all() {
        return ordered;
    }

    public List<SecuredResource> byCategory(String category) {
        return ordered.stream().filter(r -> r.category().equals(category)).toList();
    }

    public Optional<SecuredResource> find(String key) {
        return Optional.ofNullable(resources.get(key));
    }
}
