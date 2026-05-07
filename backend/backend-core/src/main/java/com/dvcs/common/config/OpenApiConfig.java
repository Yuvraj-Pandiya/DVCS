package com.dvcs.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for the DVCS Platform API.
 *
 * <p>Exposes an {@link OpenAPI} bean that:
 * <ul>
 *   <li>Sets the API title to "DVCS Platform API" and version to "1.0.0"</li>
 *   <li>Registers a global HTTP Bearer (JWT) security scheme named {@code bearerAuth}</li>
 *   <li>Applies the {@code bearerAuth} requirement globally so every operation in the
 *       Swagger UI shows the lock icon and the "Authorize" button works out of the box</li>
 * </ul>
 *
 * <p>The Swagger UI is served at {@code /api/swagger-ui/index.html} and the raw
 * OpenAPI 3 JSON is available at {@code /api/docs} — both paths are configured via
 * {@code springdoc.*} properties in {@code application.yml}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Builds the {@link OpenAPI} descriptor used by springdoc to generate the
     * OpenAPI 3 specification and power the Swagger UI.
     *
     * @return a fully configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI dvcsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DVCS Platform API")
                        .version("1.0.0")
                        .description("REST API for the DVCS Platform — a production-grade, " +
                                "web-based Distributed Version Control System."))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Provide a valid JWT access token obtained " +
                                                "from POST /api/auth/login. " +
                                                "Format: Bearer <token>")));
    }
}
