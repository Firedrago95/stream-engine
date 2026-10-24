package io.slice.stream.testcontainer.postgres;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@Testcontainers
public interface DwPostgresTestSupport {

    PostgreSQLContainer DW_POSTGRES_CONTAINER = createAndStartContainer();

    private static PostgreSQLContainer createAndStartContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("cheesepick_dw_test")
            .withUsername("test")
            .withPassword("test");

        container.start();
        applyMigrations(container);
        return container;
    }

    private static void applyMigrations(PostgreSQLContainer container) {
        try (
            Connection con = DriverManager.getConnection(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
            );
            Statement stmt = con.createStatement()
        ) {
            stmt.execute(readMigrationSql("V1__init_dw_schema.sql"));
            stmt.execute(readMigrationSql("V2__add_active_sessions_index.sql"));
        } catch (Exception e) {
            throw new IllegalStateException("DW 테스트 스키마 초기화 실패", e);
        }
    }

    private static String readMigrationSql(String fileName) throws Exception {
        Path directPath = Path.of("airflow", "migrations", fileName);
        if (Files.exists(directPath)) {
            return Files.readString(directPath);
        }
        Path parentPath = Path.of("..", "airflow", "migrations", fileName);
        return Files.readString(parentPath);
    }

    @DynamicPropertySource
    static void dwPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DW_POSTGRES_CONTAINER.getJdbcUrl() + "&reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", DW_POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", DW_POSTGRES_CONTAINER::getPassword);
    }
}
