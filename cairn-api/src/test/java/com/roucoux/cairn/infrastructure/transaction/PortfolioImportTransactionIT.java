package com.roucoux.cairn.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PortfolioImportTransactionIT.RecordingResolver.class)
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=test-password"
        })
@Testcontainers
class PortfolioImportTransactionIT {

    static final List<Boolean> TRANSACTION_DURING_RESOLUTION = new ArrayList<>();
    static final List<Boolean> TRANSACTION_DURING_WRITE = new ArrayList<>();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void resolvesUnknownTitlesOutsideAnyTransactionThenWritesEverything() throws Exception {
        TRANSACTION_DURING_RESOLUTION.clear();
        TRANSACTION_DURING_WRITE.clear();

        mockMvc.perform(post("/portfolio/import")
                        .with(user("alex"))
                        .with(csrf())
                        .contentType("text/csv")
                        .content("account;accountType;institution;instrument;isinOrTicker;quantity;averageCost\r\n"
                                + "Northwind PEA;PEA;Northwind Bank;Contoso Tracker;CTT.PA;10;5\r\n"))
                .andExpect(status().isOk());

        assertThat(TRANSACTION_DURING_RESOLUTION).containsExactly(false);
        assertThat(TRANSACTION_DURING_WRITE).containsExactly(true);
        assertThat(jdbc.queryForObject("select count(*) from holdings", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from instruments where symbol = 'CTT'", Integer.class))
                .isEqualTo(1);
    }

    @TestConfiguration
    static class RecordingResolver {

        @Bean
        static BeanPostProcessor recordingSaveHolding() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof SaveHoldingPort)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            ClassUtils.getAllInterfaces(bean),
                            (proxy, method, args) -> {
                                if (method.getName().equals("save") && args[0] instanceof Holding) {
                                    TRANSACTION_DURING_WRITE.add(
                                            TransactionSynchronizationManager.isActualTransactionActive());
                                }
                                try {
                                    return method.invoke(bean, args);
                                } catch (InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
                }
            };
        }

        @Bean
        @Primary
        ResolveInstrumentUseCase recordingResolveInstrument() {
            return query -> {
                TRANSACTION_DURING_RESOLUTION.add(TransactionSynchronizationManager.isActualTransactionActive());
                return List.of(new InstrumentCandidate(
                        "Contoso Tracker",
                        PriceSource.YAHOO,
                        "CTT.PA",
                        AssetClass.ETF,
                        "Paris",
                        null,
                        "CTT",
                        new BigDecimal("12"),
                        "EUR"));
            };
        }
    }
}
