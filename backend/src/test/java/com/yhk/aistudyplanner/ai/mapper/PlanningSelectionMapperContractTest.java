package com.yhk.aistudyplanner.ai.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class PlanningSelectionMapperContractTest {

  @Test
  void candidateTasksAreFilteredAgainstRequestedPlanDate() throws Exception {
    Method method =
        PlanningSelectionMapper.class.getDeclaredMethod(
            "tasks", long.class, List.class, LocalDate.class, LocalDate.class);
    String sql = sql(method.getAnnotation(Select.class));

    assertTrue(sql.contains("(t.planned_date IS NULL OR t.planned_date &lt;= #{planDate})"));
    assertFalse(sql.contains("CURRENT_DATE"));
    assertFalse(sql.contains("CURDATE()"));
  }

  private String sql(Select select) {
    return String.join(" ", Arrays.stream(select.value()).map(String::trim).toList());
  }
}
