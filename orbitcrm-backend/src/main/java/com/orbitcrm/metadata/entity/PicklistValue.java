package com.orbitcrm.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.TenantScopedEntity;

/**
 * JPA entity for the {@code picklist_values} table: one value of a tenant's picklist.
 *
 * <p>Purpose: store the tenant-specific values of every picklist (lead source, status, industry, ...). The
 * {@code valueKey} is immutable and is what records store; label, color, position, active and default flags are
 * editable by administrators. {@code system} values carry platform meaning and stay active. Values are seeded from
 * the {@code PicklistDefinition} defaults when a tenant is created (or when a release adds a new picklist).
 *
 * @author Abhay
 */
@Entity
@Table(name = "picklist_values")
public class PicklistValue extends TenantScopedEntity {

    @Column(name = "picklist_key", nullable = false, updatable = false)
    private String picklistKey;

    @Column(name = "value_key", nullable = false, updatable = false)
    private String valueKey;

    @Column(nullable = false)
    private String label;

    private String color;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean system;

    protected PicklistValue() {
    }

    public PicklistValue(String picklistKey, String valueKey, String label, String color, int position,
                  boolean isDefault, boolean system) {
        this.picklistKey = picklistKey;
        this.valueKey = valueKey;
        this.label = label;
        this.color = color;
        this.position = position;
        this.isDefault = isDefault;
        this.system = system;
    }

    public String getPicklistKey() {
        return picklistKey;
    }

    public String getValueKey() {
        return valueKey;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public int getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isSystem() {
        return system;
    }

    public void relabel(String label) {
        this.label = label;
    }

    public void recolor(String color) {
        this.color = color;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
