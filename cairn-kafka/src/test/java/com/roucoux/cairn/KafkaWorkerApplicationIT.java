package com.roucoux.cairn;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.kafka.config.SchemaRegistrar;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

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

    @Container
    static GenericContainer<?> registry = new GenericContainer<>("apicurio/apicurio-registry:3.3.3")
            .withExposedPorts(8080, 9000)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(120)));

    private static Admin admin;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private SchemaRegistrar schemaRegistrar;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void registerRegistryUrl(DynamicPropertyRegistry properties) {
        properties.add("APICURIO_REGISTRY_URL", KafkaWorkerApplicationIT::registryUrl);
    }

    static String registryUrl() {
        return "http://" + registry.getHost() + ":" + registry.getMappedPort(8080) + "/apis/registry/v3";
    }

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
    void registersBothSchemasOnStartup() throws Exception {
        assertThat(versionCount("PriceUpdatedEvent")).isEqualTo(1);
        assertThat(versionCount("RefreshCompletedEvent")).isEqualTo(1);
    }

    @Test
    void aSecondRegistrationAddsNoVersion() throws Exception {
        schemaRegistrar.registerAll();

        assertThat(versionCount("PriceUpdatedEvent")).isEqualTo(1);
        assertThat(versionCount("RefreshCompletedEvent")).isEqualTo(1);
    }

    @Test
    void bindsTheYahooAndCoinGeckoClientProperties() {
        assertThat(environment.getProperty("app.client.yahoo.base-url")).isNotNull();
        assertThat(environment.getProperty("app.client.coingecko.base-url")).isNotNull();
    }

    private static int versionCount(String artifactId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        registryUrl() + "/groups/com.roucoux.cairn.events/artifacts/" + artifactId + "/versions"))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).path("count").asInt();
    }

    @Test
    void resolvesTheCoinGeckoAdapterAsAPlainPrototypeWithNoScopedProxyLeftOver() {
        assertThat(applicationContext.getBean(RefreshQuotesUseCase.class)).isNotNull();

        String[] fetchQuotePortNames = applicationContext.getBeanNamesForType(FetchQuotePort.class);
        assertThat(fetchQuotePortNames).contains("coinGeckoQuoteAdapter");
        assertThat(fetchQuotePortNames).doesNotContain("scopedTarget.coinGeckoQuoteAdapter");

        assertThat(AopUtils.isAopProxy(applicationContext.getBean("coinGeckoQuoteAdapter")))
                .isFalse();
        assertThat(applicationContext.getBean("coinGeckoQuoteAdapter"))
                .isNotSameAs(applicationContext.getBean("coinGeckoQuoteAdapter"));
    }
}
