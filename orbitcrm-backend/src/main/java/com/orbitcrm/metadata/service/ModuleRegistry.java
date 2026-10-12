package com.orbitcrm.metadata.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.SecuredResource;
import com.orbitcrm.shared.security.SecuredResourceProvider;

/**
 * Registry of all record modules known to the platform.
 *
 * <p>Purpose: the lookup point for module metadata used by the record engine, controllers, search, import/export,
 * views and dashboards. Modules contribute {@link com.orbitcrm.metadata.model.ModuleDefinition} beans; at startup
 * the registry rejects duplicate module keys and lookup fields that point to unknown modules. It also collects the
 * picklists all modules declare and, as a {@code SecuredResourceProvider}, registers every module as a scoped
 * {@code crm} permission resource, so new modules automatically appear in the role editor. {@link #require} fails
 * with {@code 404 MODULE_NOT_FOUND} for unknown keys.
 *
 * @author Abhay
 */
@Component
public class ModuleRegistry implements SecuredResourceProvider {

    private final Map<String, ModuleDefinition> modules;
    private final List<ModuleDefinition> ordered;

    ModuleRegistry(ObjectProvider<ModuleDefinition> definitionProvider) {
        List<ModuleDefinition> definitions = definitionProvider.orderedStream().toList();
        Map<String, ModuleDefinition> map = new LinkedHashMap<>();
        for (ModuleDefinition definition : definitions) {
            if (map.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalStateException("Duplicate module key: " + definition.key());
            }
        }
        for (ModuleDefinition definition : definitions) {
            for (FieldDefinition field : definition.fields()) {
                if (field.type() == FieldType.LOOKUP && field.lookupModule() != null
                        && !map.containsKey(field.lookupModule())) {
                    throw new IllegalStateException("Field " + definition.key() + "." + field.key()
                            + " points to unknown module " + field.lookupModule());
                }
            }
        }
        this.modules = Map.copyOf(map);
        this.ordered = List.copyOf(map.values());
    }

    public Collection<ModuleDefinition> all() {
        return ordered;
    }

    public Optional<ModuleDefinition> find(String key) {
        return Optional.ofNullable(modules.get(key));
    }

    public ModuleDefinition require(String key) {
        return find(key).orElseThrow(() -> ApiException.notFound("MODULE_NOT_FOUND", "Unknown module: " + key));
    }

    public List<PicklistDefinition> picklists() {
        Map<String, PicklistDefinition> byKey = new LinkedHashMap<>();
        ordered.forEach(m -> m.picklists().forEach(p -> byKey.putIfAbsent(p.key(), p)));
        return List.copyOf(byKey.values());
    }

    @Override
    public List<SecuredResource> securedResources() {
        return ordered.stream().map(m -> SecuredResource.crm(m.key(), m.pluralLabel())).toList();
    }
}
