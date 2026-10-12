package com.orbitcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.orbitcrm.support.IntegrationTest;

/**
 * Integration test of the application's foundation (phase 0).
 *
 * <p>Purpose: prove that the application boots against a real database and that the basic platform features work
 * end to end:
 * <ul>
 *   <li>the Actuator health endpoint reports {@code UP};</li>
 *   <li>the OpenAPI document is published with the title "OrbitCRM API";</li>
 *   <li>every response carries the request correlation id, honouring a well-formed inbound {@code X-Request-Id}.</li>
 * </ul>
 *
 * @author Abhay
 */
class FoundationIntegrationTest extends IntegrationTest {

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocumentIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("OrbitCRM API"));
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "test-correlation-123"))
                .andExpect(header().string("X-Request-Id", "test-correlation-123"));
    }
}
