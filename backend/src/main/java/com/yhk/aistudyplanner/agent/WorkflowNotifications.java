package com.yhk.aistudyplanner.agent;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class WorkflowNotifications {
    private final ExecutorService workers =
            new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<>());

    SseEmitter subscribe(Function<UUID, WorkflowRun> snapshots, UUID id, long after) {
        var initial = snapshots.apply(id);
        var emitter = new SseEmitter(15_000L);
        try {
            workers.execute(() -> stream(snapshots, initial, Math.min(after, initial.revision()), emitter));
        } catch (RejectedExecutionException error) {
            throw new BusinessException(ErrorCode.AGENT_LIMIT);
        }
        return emitter;
    }

    private void stream(Function<UUID, WorkflowRun> snapshots, WorkflowRun run, long after, SseEmitter emitter) {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        try {
            emitter.send(SseEmitter.event().comment("connected"));
            while (System.nanoTime() < deadline) {
                if (run.revision() > after) {
                    emitter.send(SseEmitter.event().name("workflow").id(Long.toString(run.revision()))
                            .data(run, MediaType.APPLICATION_JSON));
                    after = run.revision();
                }
                switch (run.status()) {
                    case SUCCEEDED, FAILED, CANCELLED, TIMED_OUT -> { return; }
                    default -> { }
                }
                Thread.sleep(400);
                run = snapshots.apply(run.id());
            }
        } catch (IOException | IllegalStateException | BusinessException ignored) {
            // The browser can recover with a fresh snapshot.
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            emitter.complete();
        }
    }

    @PreDestroy
    void close() { workers.shutdownNow(); }
}
