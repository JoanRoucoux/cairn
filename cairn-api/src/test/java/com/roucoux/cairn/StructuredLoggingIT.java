package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=test-password",
            "logging.structured.format.console=ecs"
        })
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void writesTheUseCaseLineAsEcsJsonWithATraceId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/portfolio").with(user("alex"))).andExpect(status().isOk());

        JsonNode line = Arrays.stream(output.getOut().split("\\R"))
                .filter(candidate -> candidate.contains("\"useCase\":\"GetPortfolio\""))
                .map(candidate -> JsonMapper.shared().readTree(candidate))
                .findFirst()
                .orElseThrow();

        assertThat(line.path("log").path("logger").asString()).isEqualTo("cairn.usecase");
        assertThat(line.path("outcome").asString()).isEqualTo("success");
        assertThat(line.path("durationMs").isNumber()).isTrue();
        assertThat(line.path("service").path("name").asString()).isEqualTo("cairn-api");
        assertThat(line.path("traceId").asString()).hasSize(32);
    }

    @Test
    void routesTheDomainSystemLoggerThroughLogback(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/portfolio/import")
                        .with(user("alex"))
                        .with(csrf())
                        .contentType("text/csv")
                        .content("account;accountType;institution;instrument;isinOrTicker;quantity;averageCost\r\n"
                                + "Livret A;SAVINGS;Woodgrove Bank;Tracker;GGT.PA;10;\r\n"))
                .andExpect(status().isUnprocessableContent());

        JsonNode line = Arrays.stream(output.getOut().split("\\R"))
                .filter(candidate -> candidate.contains("portfolio import rejected"))
                .map(candidate -> JsonMapper.shared().readTree(candidate))
                .findFirst()
                .orElseThrow();

        assertThat(line.path("log").path("logger").asString())
                .isEqualTo("com.roucoux.cairn.domain.service.PortfolioImportService");
        assertThat(line.path("log").path("level").asString()).isEqualTo("INFO");
    }
}
