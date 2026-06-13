package com.arshkapoor.portfolio;

import com.arshkapoor.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// AutoConfigureMockMvc is in the webmvc-test module in Spring Boot 4.x.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test — loads the entire Spring ApplicationContext
 * (all beans, Hibernate, H2, DispatcherServlet) and dispatches real HTTP
 * requests through Spring MVC's DispatcherServlet in-process.
 *
 * @SpringBootTest(webEnvironment = MOCK):
 *   • Boots the full context (services, repositories, DataSeeder all run).
 *   • Configures a MockDispatcherServlet — no TCP socket, much faster than
 *     RANDOM_PORT but still exercises every layer end-to-end.
 *   • Spring Security (if added later) is included; @WebMvcTest would not be.
 *
 * @AutoConfigureMockMvc:
 *   In Spring Boot 4.x this annotation (org.springframework.boot.webmvc.test.autoconfigure)
 *   wires the MockMvc bean into the test context.  It replaces the older
 *   @AutoConfigureMockMvc from spring-boot-test-autoconfigure.
 *
 * WHY not TestRestTemplate here?
 *   TestRestTemplate requires spring-boot-restclient on the classpath, which is
 *   an optional module not included in this project's starter set.  MockMvc tests
 *   the identical code path and is preferred for servlet-based apps.
 *
 * Seeded data:
 *   DataSeeder runs because spring.profiles.active=dev is in application.properties,
 *   so "My Tech Portfolio" with 3 holdings is always present.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("PortfolioController — full-stack integration tests")
class PortfolioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioRepository portfolioRepo;

    // ── GET /api/portfolios ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/portfolios returns 200 and the seeded portfolio name")
    void getAllPortfolios_returns200AndSeededPortfolioName() throws Exception {
        mockMvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].name", hasItem("My Tech Portfolio")));
    }

    // ── GET /api/portfolios/{id}/summary ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/portfolios/{id}/summary returns 200 with all financial fields")
    void getSummary_seededPortfolio_returns200WithAllFields() throws Exception {
        Long id = portfolioRepo
                .findByName("My Tech Portfolio")
                .orElseThrow(() -> new AssertionError("DataSeeder did not create the expected portfolio"))
                .getId();

        mockMvc.perform(get("/api/portfolios/{id}/summary", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                // All five analytics fields must be present
                .andExpect(jsonPath("$.portfolioName").value("My Tech Portfolio"))
                .andExpect(jsonPath("$.currentValue").isNumber())
                .andExpect(jsonPath("$.costBasis").isNumber())
                .andExpect(jsonPath("$.unrealizedGain").isNumber())
                .andExpect(jsonPath("$.unrealizedGainPct").isNumber())
                // Allocation map must contain at least one ticker key
                .andExpect(jsonPath("$.allocation").isMap())
                .andExpect(jsonPath("$.allocation", aMapWithSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/portfolios/99999/summary returns 404 for an unknown portfolio")
    void getSummary_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/portfolios/99999/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("99999")));
    }

    @Test
    @DisplayName("POST /api/portfolios returns 201 — diagnostic test")
    void createPortfolio_validName_returns201() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Diagnostic Portfolio\"}"))
                // print() dumps the full request + response to stdout so we can see any 500 body
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Diagnostic Portfolio"))
                .andExpect(jsonPath("$.id").isNumber());
    }
}
