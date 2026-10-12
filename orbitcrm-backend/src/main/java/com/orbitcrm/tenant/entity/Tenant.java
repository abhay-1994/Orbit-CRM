package com.orbitcrm.tenant.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.BaseEntity;
import com.orbitcrm.tenant.dto.TenantInfo;

/**
 * JPA entity for the {@code tenants} table: one customer organization (workspace) of the SaaS platform.
 *
 * <p>Purpose: the root of tenant isolation; every business record references a tenant. It stores the unique
 * workspace slug used at login, the status ({@code ACTIVE} or {@code SUSPENDED}; suspended tenants cannot sign in)
 * and regional settings: IANA time zone, ISO currency, locale and fiscal year start month (default April, as in
 * India). The table is not under row-level security because tenants are resolved by slug before authentication;
 * the API only ever exposes the caller's own tenant.
 *
 * @author Abhay
 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    /**
     * Lifecycle status of a tenant.
     *
     * <p>Purpose: {@code ACTIVE} tenants work normally; users of {@code SUSPENDED} tenants cannot sign in, and existing
     * tokens stop working.
     */
    public enum Status { ACTIVE, SUSPENDED }

    @Column(nullable = false, unique = true, updatable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String locale;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth = 4;

    protected Tenant() {
    }

    public Tenant(UUID id, String slug, String name, String timezone, String currency, String locale) {
        assignId(id);
        this.slug = slug;
        this.name = name;
        this.timezone = timezone;
        this.currency = currency;
        this.locale = locale;
    }

    public TenantInfo toInfo() {
        return new TenantInfo(getId(), slug, name, status.name(), timezone, currency, locale,
                fiscalYearStartMonth, getCreatedAt());
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void changeCurrency(String currency) {
        this.currency = currency;
    }

    public void changeLocale(String locale) {
        this.locale = locale;
    }

    public void changeFiscalYearStartMonth(int month) {
        this.fiscalYearStartMonth = month;
    }

    public String getSlug() {
        return slug;
    }
}
