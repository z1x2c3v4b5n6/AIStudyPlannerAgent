package com.yhk.aistudyplanner.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class WorkflowStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    WorkflowStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    void insert(long owner, WorkflowRun run) {
        jdbc.update("""
                INSERT INTO agent_workflow(id,owner_id,session_id,updated_ms,expires_ms,document)
                VALUES(?,?,?,?,?,?)
                """, run.id().toString(), owner, run.sessionId().toString(), run.updatedAt().toEpochMilli(),
                run.expiresAt().toEpochMilli(), encode(run));
    }

    WorkflowRun load(long owner, UUID id, boolean lock) {
        return jdbc.query("SELECT document FROM agent_workflow WHERE owner_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> decode(rs.getString(1)), owner, id.toString()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    WorkflowRun save(long owner, WorkflowRun run) {
        var next = run.nextVersion();
        if (jdbc.update("""
                UPDATE agent_workflow SET revision=?,updated_ms=?,document=? WHERE id=? AND owner_id=? AND revision=?
                """, next.revision(), next.updatedAt().toEpochMilli(), encode(next), next.id().toString(), owner, run.revision()) != 1) {
            throw new BusinessException(ErrorCode.AGENT_ACTION_CONFLICT);
        }
        return next;
    }

    List<WorkflowRun> list(long owner, UUID session, long offset) {
        return jdbc.query("""
                SELECT document FROM agent_workflow WHERE owner_id=? AND session_id=?
                ORDER BY updated_ms DESC,id LIMIT 10 OFFSET ?
                """, (rs, n) -> decode(rs.getString(1)), owner, session.toString(), offset);
    }

    private String encode(WorkflowRun run) {
        try { return mapper.writeValueAsString(run); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot encode workflow", ex); }
    }
    private WorkflowRun decode(String data) {
        try { return mapper.readValue(data, WorkflowRun.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot decode workflow", ex); }
    }
}
