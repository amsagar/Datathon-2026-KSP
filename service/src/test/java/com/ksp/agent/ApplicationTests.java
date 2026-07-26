package com.ksp.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full-context smoke test. Boots against a real, disposable Postgres (via Testcontainers, using
 * the same {@code pgvector/pgvector} image the app requires in production for its vector-extension
 * schema) instead of a pre-existing local/CI database — {@code mvn test} works out of the box as
 * long as Docker is available, with no manual setup.
 */
@SpringBootTest
@Testcontainers
class ApplicationTests {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("pgvector/pgvector:pg16")
					.withDatabaseName("kspagent_test")
					.withUsername("test")
					.withPassword("test");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		// Both logical datasources point at the same container — this test only needs the schema
		// and bean graph to wire up successfully, not to exercise cross-database business logic.
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("crime.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("crime.datasource.username", POSTGRES::getUsername);
		registry.add("crime.datasource.password", POSTGRES::getPassword);

		registry.add("agent.auth.jwt-secret", () -> "test-only-jwt-secret-not-for-production-use");
		registry.add("agent.cron.secret", () -> "test-only-cron-secret");

		// No real external LLM/RAG/blob-storage endpoints in a context-load test — these just need
		// to be non-blank so the corresponding beans construct; nothing in this test calls out to them.
		registry.add("agent.llm.base-url", () -> "http://localhost:1");
		registry.add("agent.llm.completions-path", () -> "/x");
		registry.add("agent.llm.catalyst-org", () -> "0");
		registry.add("agent.llm.model", () -> "none");
		registry.add("agent.llm.api-key", () -> "none");
		registry.add("agent.llm.client-id", () -> "none");
		registry.add("agent.llm.client-secret", () -> "none");
		registry.add("agent.llm.refresh-token", () -> "none");
		registry.add("agent.rag.base-url", () -> "http://localhost:1");
		registry.add("agent.rag.completions-path", () -> "/x");
		registry.add("agent.rag.catalyst-org", () -> "0");
		registry.add("agent.rag.api-key", () -> "none");
		registry.add("agent.stratus.bucket-url", () -> "http://localhost:1");
		registry.add("agent.stratus.catalyst-org", () -> "0");
		registry.add("agent.stratus.api-key", () -> "none");
	}

	@Test
	void contextLoads() {
	}

}
