package com.dvcs.openapi;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke tests verifying that the OpenAPI 3 JSON endpoint and Swagger UI are reachable.
 *
 * <p>Both endpoints are publicly accessible (no authentication required) as configured
 * in {@code SecurityConfig}. The tests assert:
 * <ul>
 *   <li>{@code GET /api/docs} returns HTTP 200 and a JSON body containing the
 *       {@code "openapi"} field (confirming it is a valid OpenAPI 3 document).</li>
 *   <li>{@code GET /api/swagger-ui/index.html} returns HTTP 200, confirming the
 *       Swagger UI HTML is served.</li>
 * </ul>
 */
@DisplayName("OpenAPI / Swagger UI Smoke Tests")
class OpenApiIT extends AbstractIntegrationTest {

    /**
     * GET /api/docs should return HTTP 200 with an OpenAPI 3 JSON document.
     *
     * <p>springdoc-openapi is configured via {@code springdoc.api-docs.path=/api/docs}
     * in {@code application.yml}.
     */
    @Test
    @DisplayName("GET /api/docs returns HTTP 200 with OpenAPI 3 JSON")
    void getApiDocs_returns200WithOpenApiJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/docs"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("Response body should contain the 'openapi' field")
                .contains("openapi");
    }

    /**
     * GET /api/swagger-ui/index.html should return HTTP 200, confirming the
     * Swagger UI HTML page is served.
     *
     * <p>springdoc-openapi is configured via {@code springdoc.swagger-ui.path=/api/swagger-ui}
     * in {@code application.yml}. The UI is served at {@code /api/swagger-ui/index.html}
     * after the redirect from the configured path.
     */
    @Test
    @DisplayName("GET /api/swagger-ui/index.html returns HTTP 200")
    void getSwaggerUi_returns200() throws Exception {
        mockMvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
