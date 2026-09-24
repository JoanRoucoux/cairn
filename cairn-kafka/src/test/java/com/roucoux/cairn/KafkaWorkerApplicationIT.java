package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Boots the full worker context against real PostgreSQL and Kafka: the schema is migrated here
 * with the schema module's real changelog (test scope only — see that module's pom for why), JPA
 * mappings are then validated (ddl-auto: validate), and a plain AdminClient checks that
 * {@code TopicsConfig}'s two {@code NewTopic} beans were actually created on the broker by
 * Spring Boot's own {@code KafkaAdmin}, not merely declared.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml")
@Testcontainers
class KafkaWorkerApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    private static Admin admin;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeAll
    static void connectAdminClient() {
        admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }

    @AfterAll
    static void closeAdminClient() {
        admin.close();
    }

    @Test
    void declaresBothTopicsOnTheBroker() throws Exception {
        Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);

        assertThat(topics).contains("cairn.prices", "cairn.portfolio");
    }

    @Test
    void bindsTheYahooAndCoinGeckoClientProperties() {
        assertThat(environment.getProperty("app.client.yahoo.base-url")).isNotNull();
        assertThat(environment.getProperty("app.client.coingecko.base-url")).isNotNull();
    }

    @Test
    void resolvesTheCoinGeckoAdapterAsAPlainPrototypeWithNoScopedProxyLeftOver() {
        assertThat(applicationContext.getBean(RefreshQuotesUseCase.class)).isNotNull();

        String[] fetchQuotePortNames = applicationContext.getBeanNamesForType(FetchQuotePort.class);
        assertThat(fetchQuotePortNames).contains("coinGeckoQuoteAdapter");
        assertThat(fetchQuotePortNames).doesNotContain("scopedTarget.coinGeckoQuoteAdapter");

        assertThat(AopUtils.isAopProxy(applicationContext.getBean("coinGeckoQuoteAdapter")))
                .isFalse();
    }
}
