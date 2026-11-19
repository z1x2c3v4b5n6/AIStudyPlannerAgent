package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TodayTasksView;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentToolsTest {
    @Test
    void registryUsesOwnedServiceAndRejectsUnexpectedArguments() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var tasks = mock(TaskService.class);
            when(tasks.today(null)).thenReturn(new TodayTasksView(List.of(), 0));
            var tools = new AgentTools(null, null, tasks, null, null, null, null, factory.getValidator());
            assertEquals(8, tools.callbacks().size());
            tools.run("today_tasks", tools.parse("{}"));
            verify(tasks).today(null);
            assertThrows(BusinessException.class, () -> tools.run("today_tasks", tools.parse("{\"userId\":42}")));
            assertThrows(BusinessException.class, () -> tools.run("delete_task", tools.parse("{}")));
            assertThrows(BusinessException.class, () -> tools.parse("{\"id\":1,\"id\":2}"));
            assertThrows(IllegalStateException.class, () -> tools.callbacks().get(0).call("{}"));
            verifyNoMoreInteractions(tasks);
        }
    }
}
