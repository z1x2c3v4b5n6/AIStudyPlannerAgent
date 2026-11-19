package com.yhk.aistudyplanner.agent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class AgentTestDatabase {
    final JdbcTemplate jdbc;
    final DataSourceTransactionManager transactions;
    AgentTestDatabase(String... migrations) {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        for (String file : migrations) {
            try (var input = new ClassPathResource("db/migration/" + file).getInputStream()) {
                var sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace(" DEFAULT CHARSET=utf8mb4", "");
                for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
    }
}
