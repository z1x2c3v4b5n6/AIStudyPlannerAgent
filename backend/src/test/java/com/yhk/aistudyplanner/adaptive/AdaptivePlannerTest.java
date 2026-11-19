package com.yhk.aistudyplanner.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptivePlannerTest {
    private final LocalDate date=LocalDate.of(2026,11,20);
    private final LocalDateTime now=date.minusDays(1).atTime(10,0);

    @Test void threeStrategiesDifferAndNeverExceedCapacityOrLocks() {
        var tasks=List.of(task(1,4,date.plusDays(1),null,null),task(2,3,date,null,null),task(3,2,date,null,null));
        var lock=new AdaptivePlanner.Slot(date.atTime(9,0),date.atTime(9,30));
        var rules=new AdaptivePlanner.Constraints(date,2,60,null,List.of(lock),now);
        var minimal=AdaptivePlanner.plan(AdaptivePlanner.Strategy.MINIMAL,rules,tasks,List.of());
        var priority=AdaptivePlanner.plan(AdaptivePlanner.Strategy.PRIORITY,rules,tasks,List.of());
        var balanced=AdaptivePlanner.plan(AdaptivePlanner.Strategy.BALANCED,rules,tasks,List.of());
        assertEquals(date.plusDays(1),minimal.scheduled().stream().filter(p->p.task().id()==1).findFirst().orElseThrow().start().toLocalDate());
        assertEquals(date.atTime(9,30),priority.scheduled().get(0).start());
        assertNotEquals(priority.scheduled(),balanced.scheduled());
        for(var scheme:List.of(minimal,priority,balanced)) {
            assertTrue(scheme.dailyMinutes().values().stream().allMatch(value->value<=60));
            assertTrue(scheme.scheduled().stream().noneMatch(p->p.start().isBefore(lock.end())&&p.end().isAfter(lock.start())));
        }
    }

    @Test void overdueAndPrerequisiteAreHandledWithoutExtendingFutureDueTimes() {
        var rules=new AdaptivePlanner.Constraints(date,2,30,null,List.of(),now);
        var tasks=List.of(task(1,4,date,date.atTime(9,15),null),
                task(2,2,date,now.minusDays(1),null),task(3,4,date,null,2L));
        var output=AdaptivePlanner.plan(AdaptivePlanner.Strategy.PRIORITY,rules,tasks,List.of());
        assertEquals(List.of(2L,3L),output.scheduled().stream().map(p->p.task().id()).toList());
        assertEquals(1,output.overdueScheduled());
        assertEquals(1,output.omitted().get(0).taskId());
        assertTrue(output.scheduled().get(1).start().isAfter(output.scheduled().get(0).end()));
        assertEquals(1,AdaptivePlanner.plan(AdaptivePlanner.Strategy.PRIORITY,
                new AdaptivePlanner.Constraints(date,1,30,null,List.of(),now),tasks,List.of()).scheduled().size());
    }

    private AdaptivePlanner.Task task(long id,int priority,LocalDate planned,LocalDateTime due,Long prerequisite) {
        return new AdaptivePlanner.Task(id,1,"任务"+id,priority,30,planned,due,null,now,prerequisite,false);
    }
}
