package com.yhk.aistudyplanner.learningpath.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import javax.sql.DataSource;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

class LearningPathItemMapperTest {

    @Test
    void selectRowsMapsJsonMinutesAndJoinedTaskFieldsByExplicitColumnOrder() throws Exception {
        var cachedRows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        String[] columns = {
                "id", "sequenceNo", "stageNo", "stageTitle", "stageDescription",
                "topic", "learningObjective", "learningMethod", "completionCriteria",
                "estimatedMinutes", "suggestedDay", "prerequisiteText", "reason",
                "status", "taskId", "taskTitle", "taskStatus"
        };
        int[] types = {
                Types.BIGINT, Types.INTEGER, Types.INTEGER, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.INTEGER, Types.INTEGER, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR
        };
        metadata.setColumnCount(columns.length);
        for (int index = 0; index < columns.length; index++) {
            metadata.setColumnName(index + 1, columns[index]);
            metadata.setColumnLabel(index + 1, columns[index]);
            metadata.setColumnType(index + 1, types[index]);
        }
        cachedRows.setMetaData(metadata);
        cachedRows.moveToInsertRow();
        cachedRows.updateLong(1, 31L);
        cachedRows.updateInt(2, 2);
        cachedRows.updateInt(3, 1);
        cachedRows.updateString(4, "集合基础");
        cachedRows.updateString(5, "掌握集合核心结构");
        cachedRows.updateString(6, "HashMap");
        cachedRows.updateString(7, "理解put流程与扩容机制");
        cachedRows.updateString(8, "[\"阅读源码\",\"运行示例\"]");
        cachedRows.updateString(9, "[\"能解释put流程\",\"能说明扩容条件\"]");
        cachedRows.updateInt(10, 90);
        cachedRows.updateInt(11, 3);
        cachedRows.updateString(12, "面向对象");
        cachedRows.updateString(13, "面试高频知识点");
        cachedRows.updateString(14, "PENDING");
        cachedRows.updateLong(15, 88L);
        cachedRows.updateString(16, "复习HashMap");
        cachedRows.updateString(17, "TODO");
        cachedRows.insertRow();
        cachedRows.moveToCurrentRow();
        cachedRows.beforeFirst();
        var resultSet = spy(cachedRows);
        doReturn(false).when(resultSet).isClosed();

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(statement.getUpdateCount()).thenReturn(-1);

        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.addMapper(LearningPathItemMapper.class);

        List<LearningPathItemMapper.ItemRow> rows;
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            rows = session.getMapper(LearningPathItemMapper.class).selectRows(7L, 1L);
        }

        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals("[\"阅读源码\",\"运行示例\"]", row.learningMethod());
        assertEquals("[\"能解释put流程\",\"能说明扩容条件\"]", row.completionCriteria());
        assertEquals(90, row.estimatedMinutes());
        assertEquals("复习HashMap", row.taskTitle());
        assertEquals("TODO", row.taskStatus());

        String sql = configuration.getMappedStatement(
                LearningPathItemMapper.class.getName() + ".selectRows").getBoundSql(null).getSql();
        assertFalse(sql.contains("i.*"));
        assertTrue(sql.contains("i.path_id=#{pathId}") || sql.contains("i.path_id=?"));
        assertTrue(sql.contains("t.user_id=#{userId}") || sql.contains("t.user_id=?"));
        assertTrue(sql.contains("ORDER BY i.sequence_no ASC"));
    }
}
