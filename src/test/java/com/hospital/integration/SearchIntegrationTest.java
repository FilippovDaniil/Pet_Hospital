package com.hospital.integration;

import com.hospital.search.DoctorDocument;
import com.hospital.search.PatientDocument;
import com.hospital.search.SearchService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест: реальный OpenSearch через Testcontainers.
 * Профиль "test" НЕ используем, чтобы не отключалась OpenSearch (enabled=false в test profile).
 * Вместо этого используем отдельный профиль "search-test".
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "opensearch.enabled=true",
                // Отключаем Redis, Kafka, Loki для этого теста через inline properties
                "spring.cache.type=none",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "spring.kafka.bootstrap-servers=localhost:19092",
                "spring.kafka.producer.transaction-id-prefix=",
                "logging.loki.url=http://localhost:13100",
                "spring.datasource.url=jdbc:tc:postgresql:15:///hospital_search_test",
                "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
                "spring.main.allow-bean-definition-overriding=true"
        })
class SearchIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.17.0"))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Skipping SearchIntegrationTest: Docker not available");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("opensearch.url",
                () -> "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200));
    }

    @Autowired
    private SearchService searchService;

    @Test
    void indexAndSearchPatient() throws InterruptedException {
        searchService.indexPatient(PatientDocument.builder()
                .id("test-p-1")
                .fullName("Иванов Сергей Петрович")
                .department("Кардиология")
                .active(true)
                .build());

        // OpenSearch индексирует асинхронно — даём секунду
        Thread.sleep(1500);

        List<PatientDocument> results = searchService.searchPatients("Иванов");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getFullName()).contains("Иванов");
    }

    @Test
    void indexAndSearchDoctor() throws InterruptedException {
        searchService.indexDoctor(DoctorDocument.builder()
                .id("test-d-1")
                .fullName("Захаров Андрей Михайлович")
                .specialization("CARDIOLOGIST")
                .department("Кардиология")
                .active(true)
                .build());

        Thread.sleep(1500);

        List<DoctorDocument> results = searchService.searchDoctors("Захаров");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getFullName()).contains("Захаров");
    }

    @Test
    void deletePatient_removesFromIndex() throws InterruptedException {
        searchService.indexPatient(PatientDocument.builder()
                .id("test-p-del")
                .fullName("Удалённый Пациент")
                .active(false)
                .build());

        Thread.sleep(1000);
        searchService.deletePatient("test-p-del");
        Thread.sleep(1000);

        List<PatientDocument> results = searchService.searchPatients("Удалённый Пациент");
        assertThat(results).isEmpty();
    }
}
