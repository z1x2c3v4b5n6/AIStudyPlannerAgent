package com.yhk.aistudyplanner.adaptive;

import java.time.*;
import java.util.*;

final class AdaptivePlanner {
    enum Strategy { MINIMAL, PRIORITY, BALANCED }
    record Task(long id, long subjectId, String title, int priority, int minutes,
                LocalDate plannedDate, LocalDateTime dueAt, LocalDate goalDate, LocalDateTime updatedAt,
                Long prerequisiteTaskId, boolean missingPrerequisite) {}
    record Slot(LocalDateTime start, LocalDateTime end) {}
    record Placement(Task task, LocalDateTime start, LocalDateTime end) {}
    record Omission(long taskId, String title, String reason) {}
    record Scheme(Strategy strategy, String objective, List<Placement> scheduled, List<Omission> omitted,
                  Map<LocalDate,Integer> dailyMinutes, int changedDates, int overdueScheduled) {}
    record Constraints(LocalDate start, int days, int dailyMinutes, Long preferredSubjectId,
                       List<Slot> locked, LocalDateTime planningTime) {}

    static Scheme plan(Strategy strategy, Constraints constraints, List<Task> candidates, List<Slot> occupied) {
        var days = new TreeMap<LocalDate, Day>();
        for (int offset=0; offset<constraints.days(); offset++) {
            LocalDate date = constraints.start().plusDays(offset);
            var blocks = occupied.stream().filter(slot -> slot.start().toLocalDate().equals(date)).toList();
            days.put(date, new Day(date, constraints.dailyMinutes(), blocks, constraints.locked()));
        }
        var ordering = Comparator.comparingInt(Task::priority).reversed()
                .thenComparing(Task::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(task -> !Objects.equals(task.subjectId(),constraints.preferredSubjectId()))
                .thenComparingLong(Task::id);
        var queue = new ArrayList<>(candidates.stream().sorted(ordering).toList());
        var assigned = new HashMap<Long,Placement>();
        var omitted = new ArrayList<Omission>();
        while (!queue.isEmpty()) {
            boolean progressed = false;
            for (var iterator=queue.iterator();iterator.hasNext();) {
                var task = iterator.next();
                if (task.prerequisiteTaskId()!=null && !assigned.containsKey(task.prerequisiteTaskId())) continue;
                iterator.remove();
                progressed = true;
                if (task.missingPrerequisite()) {
                    omitted.add(new Omission(task.id(), task.title(), "学习路径前置节点尚未完成"));
                    continue;
                }
                var options = new ArrayList<>(days.values());
                if (strategy == Strategy.MINIMAL) options.sort(Comparator
                        .comparing((Day day) -> !day.date.equals(task.plannedDate())).thenComparing(day -> day.date));
                if (strategy == Strategy.BALANCED) options.sort(Comparator.comparingInt((Day day) -> day.used)
                        .thenComparing(day -> day.date));
                var previous = assigned.get(task.prerequisiteTaskId());
                var selected = options.stream().map(day -> day.find(task, previous == null ? null : previous.end(),
                        constraints.planningTime())).filter(Objects::nonNull).findFirst().orElse(null);
                if (selected == null) {
                    omitted.add(new Omission(task.id(), task.title(), omissionReason(task,constraints,options,
                            previous==null?null:previous.end())));
                } else {
                    assigned.put(task.id(),selected);
                    var day = days.get(selected.start().toLocalDate());
                    day.blocks.add(new Slot(selected.start(),selected.end()));
                    day.used += Math.max(15, task.minutes());
                }
            }
            if (!progressed) {
                queue.forEach(task -> omitted.add(new Omission(task.id(),task.title(),"前置任务未能安排")));
                break;
            }
        }
        var placements = assigned.values().stream().sorted(Comparator.comparing(Placement::start)).toList();
        var usage = new TreeMap<LocalDate,Integer>();
        days.forEach((date,day) -> usage.put(date,day.used));
        return new Scheme(strategy, switch(strategy) {
            case MINIMAL -> "优先保留任务原计划日期";
            case PRIORITY -> "优先让高优先级及临期任务尽早进入空闲时段";
            case BALANCED -> "优先使用当前负载较低的日期";
        },placements,List.copyOf(omitted),usage,(int)placements.stream()
                .filter(item -> !item.start().toLocalDate().equals(item.task().plannedDate())).count(),
                (int)placements.stream().filter(item -> item.task().dueAt()!=null
                        && item.task().dueAt().isBefore(constraints.planningTime())).count());
    }

    private static String omissionReason(Task task,Constraints constraints,List<Day> days,LocalDateTime prerequisiteEnd) {
        int minutes=Math.max(15,task.minutes());
        if(minutes>constraints.dailyMinutes()) return "完整任务时长超过单日预算";
        boolean deadline=task.dueAt()!=null && !task.dueAt().isBefore(constraints.planningTime())
                || task.goalDate()!=null && !task.goalDate().isBefore(constraints.planningTime().toLocalDate());
        if(deadline && days.stream().noneMatch(day -> new Day(day.date,day.capacity,List.of(),List.of())
                .find(task,prerequisiteEnd,constraints.planningTime())!=null))
            return "有效截止日期前无法保留完整时段";
        if(days.stream().noneMatch(day->day.capacity-day.used>=minutes)) return "各日剩余学习预算不足";
        return "可用时段与已有安排、锁定时段或截止时间不兼容";
    }

    private static final class Day {
        final LocalDate date;
        final int capacity;
        final List<Slot> blocks = new ArrayList<>();
        int used;
        Day(LocalDate date, int capacity, List<Slot> occupied, List<Slot> locks) {
            this.date=date; this.capacity=capacity;
            blocks.addAll(occupied);
            blocks.addAll(locks.stream().filter(slot -> slot.start().toLocalDate().equals(date)).toList());
            LocalDateTime unionEnd = date.atStartOfDay();
            for (var slot : occupied.stream().sorted(Comparator.comparing(Slot::start)).toList()) {
                var start = slot.start().isAfter(unionEnd) ? slot.start() : unionEnd;
                if (slot.end().isAfter(start)) {
                    used += (int) java.time.temporal.ChronoUnit.MINUTES.between(start,slot.end());
                    unionEnd=slot.end();
                }
            }
        }
        Placement find(Task task, LocalDateTime prerequisiteEnd, LocalDateTime now) {
            int minutes = Math.max(15,task.minutes());
            if (minutes > capacity-used) return null;
            var cursor = date.atTime(9,0);
            if (cursor.isBefore(now)) cursor = now;
            if (prerequisiteEnd!=null && cursor.isBefore(prerequisiteEnd)) cursor = prerequisiteEnd;
            var boundary = date.atTime(22,0);
            if (task.goalDate()!=null && !task.goalDate().isBefore(now.toLocalDate()) && date.isAfter(task.goalDate())) return null;
            if (task.dueAt()!=null && !task.dueAt().isBefore(now) && task.dueAt().isBefore(boundary)) boundary=task.dueAt();
            for (var slot : blocks.stream().sorted(Comparator.comparing(Slot::start)).toList()) {
                if (!cursor.plusMinutes(minutes).isAfter(slot.start())) break;
                if (slot.end().isAfter(cursor)) cursor=slot.end();
            }
            var end=cursor.plusMinutes(minutes);
            return !end.isAfter(boundary) ? new Placement(task,cursor,end) : null;
        }
    }
}
