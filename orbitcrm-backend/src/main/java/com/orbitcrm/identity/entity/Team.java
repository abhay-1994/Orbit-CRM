package com.orbitcrm.identity.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.TenantScopedEntity;

/**
 * JPA entity for the {@code teams} table: a group of users, optionally nested under a parent team.
 *
 * <p>Purpose: model the sales organization (teams, regions, departments). TEAM-scoped permissions cover records
 * owned by members of the user's team and all its descendant teams, so a parent team works like a department.
 *
 * @author Abhay
 */
@Entity
@Table(name = "teams")
public class Team extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    protected Team() {
    }

    public Team(String name, String description, UUID parentId) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void update(String name, String description, UUID parentId) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
    }
}
