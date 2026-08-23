package com.yhk.aistudyplanner.learningpath.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yhk.aistudyplanner.ai.mapper.AiPlanningContextMapper;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class LearningPathDailyPlanContractTest {
    @Test
    void ordinaryPathTaskCanEnterAiAndRuleCandidateQueries() throws Exception {
        String aiSql = selectSql(AiPlanningContextMapper.class, "tasks");
        String ruleSql = selectSql(StudyPlanMapper.class, "selectCandidates");

        assertCandidateContract(aiSql);
        assertCandidateContract(ruleSql);
        assertFalse(aiSql.contains("path_id"));
        assertFalse(ruleSql.contains("path_id"));
    }

    private void assertCandidateContract(String sql) {
        String compact = sql.replaceAll("\\s+", " ");
        assertTrue(compact.contains("t.user_id=#{userId}"));
        assertTrue(compact.contains("t.status IN ('TODO','IN_PROGRESS')"));
        assertTrue(compact.contains("t.planned_date IS NULL OR t.planned_date <= #{planDate}"));
    }

    private String selectSql(Class<?> mapper, String methodName) throws Exception {
        Method method = Arrays.stream(mapper.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join(" ", method.getAnnotation(Select.class).value());
    }
}
