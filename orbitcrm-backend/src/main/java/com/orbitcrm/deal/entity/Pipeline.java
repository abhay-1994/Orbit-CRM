package com.orbitcrm.deal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.TenantScopedEntity;

/**
 * JPA entity for the {@code pipelines} table: a tenant's sales pipeline.
 *
 * <p>Purpose: a named sequence of stages that deals move through (e.g. a new-business pipeline and a renewals
 * pipeline). Exactly one pipeline per tenant is the default for new deals; inactive pipelines keep their deals but
 * accept no new ones. Every tenant starts with a seeded "Sales Pipeline".
 *
 * @author Abhay
 */
@Entity
@Table(name = "pipelines")
public class Pipeline extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    protected Pipeline() {
    }

    public Pipeline(String name, boolean isDefault, int position) {
        this.name = name;
        this.isDefault = isDefault;
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public int getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
