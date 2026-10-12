package com.orbitcrm.metadata.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Registry of all {@link ReferenceResolver} beans, keyed by reference type.
 *
 * <p>Purpose: find the resolver for a field's {@code referenceType}. Duplicate types are rejected at startup;
 * {@link #require} fails fast when a field references a type that no module provides.
 *
 * @author Abhay
 */
@Component
public class ReferenceResolvers {

    private final Map<String, ReferenceResolver> byType;

    ReferenceResolvers(ObjectProvider<ReferenceResolver> resolvers) {
        Map<String, ReferenceResolver> map = new HashMap<>();
        resolvers.orderedStream().forEach(r -> {
            if (map.putIfAbsent(r.type(), r) != null) {
                throw new IllegalStateException("Duplicate reference resolver: " + r.type());
            }
        });
        this.byType = Map.copyOf(map);
    }

    public Optional<ReferenceResolver> find(String type) {
        return Optional.ofNullable(byType.get(type));
    }

    public ReferenceResolver require(String type) {
        return find(type).orElseThrow(() -> new IllegalStateException("No resolver for reference type " + type));
    }
}
