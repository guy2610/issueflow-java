package io.github.guyeven.issueflow;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRESQL = startPostgresql();

    private static PostgreSQLContainer<?> startPostgresql() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("issueflow_test")
                .withUsername("issueflow")
                .withPassword("issueflow");
        container.start();
        return container;
    }
}
