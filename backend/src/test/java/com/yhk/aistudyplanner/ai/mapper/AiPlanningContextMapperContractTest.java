package com.yhk.aistudyplanner.ai.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanningContextMapperContractTest {

    @Test
    void everyContextQueryIsScopedToCurrentUser() {
        for (Method method : AiPlanningContextMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            assertTrue(select != null && sql(select).contains("user_id=#{userId}"), method.getName());
        }
    }

    @Test
    void candidateTasksExcludeCompletedAndCancelledAndAreBounded() throws Exception {
        Select select = AiPlanningContextMapper.class
                .getDeclaredMethod("tasks", long.class, java.time.LocalDate.class, java.time.LocalDateTime.class)
                .getAnnotation(Select.class);
        String sql = sql(select);
        assertTrue(sql.contains("t.status IN ('TODO','IN_PROGRESS')"));
        assertTrue(sql.contains("LIMIT 100"));
    }

    @Test
    void recentStudyRecordsAreBounded() throws Exception {
        Select select = AiPlanningContextMapper.class.getDeclaredMethod("records", long.class)
                .getAnnotation(Select.class);
        assertTrue(sql(select).contains("LIMIT 30"));
    }

    private String sql(Select select) {
        return String.join(" ", Arrays.stream(select.value()).map(String::trim).toList());
    }
}
