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

    private static final String V1 = "db/migration/V1__create_mvp_tables.sql";
    private static final String V2 = "db/migration/V2__add_plan_execution_tracking.sql";
    private static final String V3 = "db/migration/V3__complete_plan_execution_tracking.sql";
    private static final String V4 = "db/migration/V4__add_learning_paths.sql";

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

    @Test
    void allPublishedMigrationChecksumsMustRemainImmutable() {
        assertEquals(-1761162165, checksum(V1));
        assertEquals(-810465326, checksum(V2));
        assertEquals(1354719819, checksum(V3));
        assertEquals(876787759, checksum(V4));
    }

    @Test
    void v4AddsLearningPathTablesWithoutChangingPublishedMigrations() throws IOException {
        String sql = read(V4);
        assertTrue(sql.contains("CREATE TABLE study_path"));
        assertTrue(sql.contains("CREATE TABLE study_path_item"));
        assertTrue(sql.contains("UNIQUE KEY uk_path_item_sequence"));
        assertTrue(sql.contains("UNIQUE KEY uk_path_item_task"));
        assertTrue(sql.contains("FOREIGN KEY (task_id) REFERENCES study_task"));
        assertFalse(sql.contains("ALTER TABLE study_plan"));
        assertFalse(sql.contains("ALTER TABLE study_record"));
    }

    private String read(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing migration: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int checksum(String path) {
        return ChecksumCalculator.calculate(new ClassPathResource(
                null, path, Thread.currentThread().getContextClassLoader(), StandardCharsets.UTF_8));
    }
}
