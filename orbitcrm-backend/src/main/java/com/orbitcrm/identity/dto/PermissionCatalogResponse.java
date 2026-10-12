package com.orbitcrm.identity.dto;

import java.util.List;

import com.orbitcrm.shared.security.AccessScope;

/**
 * Response body of {@code GET /api/v1/roles/permission-catalog}.
 *
 * <p>Purpose: everything the role editor needs to render the permission matrix.
 *
 * @author Abhay
 * @param resources every grantable resource with its actions
 * @param scopes    the available scopes ({@code NONE}, {@code OWN}, {@code TEAM}, {@code ALL})
 */
public record PermissionCatalogResponse(List<CatalogResource> resources, List<AccessScope> scopes) {
}
