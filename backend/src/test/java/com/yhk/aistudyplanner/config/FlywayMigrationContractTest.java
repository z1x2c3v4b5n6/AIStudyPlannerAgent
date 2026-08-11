package com.yhk.aistudyplanner.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {

    private static final String V2 = "db/migration/V2__add_plan_execution_tracking.sql";
    private static final String V3 = "db/migration/V3__complete_plan_execution_tracking.sql";

    @Test
    void publishedV2ChecksumMustRemainImmutable() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ClassPathResource resource =
                new ClassPathResource(null, V2, classLoader, StandardCharsets.UTF_8);

        assertEquals(-810465326, ChecksumCalculator.calculate(resource));
    }

    @Test
    void v3OnlyRemovesDuplicatedPlanItemExecutionFields() throws IOException {
        String sql = read(V3);

        assertTrue(sql.contains("DROP CHECK chk_plan_item_actual_minutes"));
        assertTrue(sql.contains("DROP COLUMN actual_minutes"));
        assertTrue(sql.contains("DROP COLUMN feedback"));
        assertFalse(sql.contains("ADD COLUMN plan_id"));
        assertFalse(sql.contains("ADD COLUMN plan_item_id"));
        assertFalse(sql.contains("ADD UNIQUE KEY uk_record_plan_item"));
    }

    private String read(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing migration: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
