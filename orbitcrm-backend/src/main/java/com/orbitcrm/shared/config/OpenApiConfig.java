package com.orbitcrm.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI (Swagger) description of the REST API.
 *
 * <p>Purpose: publish a machine-readable contract at {@code /v3/api-docs} and an interactive explorer at
 * {@code /swagger-ui.html}, used by frontend developers and API integrators. It sets the API title
 * ("OrbitCRM API"), the version (from {@code orbitcrm.version}) and a global HTTP bearer (JWT) security scheme,
 * so the Swagger UI "Authorize" button can call protected endpoints with an access token. The documentation
 * endpoints are disabled in the {@code prod} profile unless {@code ORBITCRM_API_DOCS_ENABLED=true}.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI orbitCrmOpenApi(@Value("${orbitcrm.version:0.1.0}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("OrbitCRM API")
                        .version(version)
                        .description("REST API for the OrbitCRM multi-tenant CRM platform. "
                                + "All endpoints except /api/v1/auth/** require a Bearer access token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
