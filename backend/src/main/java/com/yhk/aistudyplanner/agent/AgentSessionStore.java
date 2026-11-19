package com.yhk.aistudyplanner.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AgentSessionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    AgentSessionStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    void insert(AgentSessions.Session row) {
        jdbc.update("INSERT INTO agent_session(id,owner_id,title,updated_ms,expires_ms,document) VALUES(?,?,?,?,?,?)",
                row.id().toString(), row.owner(), row.title(), row.updated().toEpochMilli(),
                row.expires().toEpochMilli(), encode(row));
    }

    AgentSessions.Session find(UUID id, long owner, boolean lock) {
        return jdbc.query("SELECT document,revision FROM agent_session WHERE id=? AND owner_id=?"
                        + (lock ? " FOR UPDATE" : ""), (rs, n) -> decode(rs.getString(1), AgentSessions.Session.class),
                id.toString(), owner).stream().findFirst().orElse(null);
    }

    boolean save(AgentSessions.Session row) {
        var next = row.withRevision(row.revision() + 1);
        return jdbc.update("""
                UPDATE agent_session SET revision=?,title=?,updated_ms=?,expires_ms=?,document=?
                WHERE id=? AND owner_id=? AND revision=?
                """, next.revision(), next.title(), next.updated().toEpochMilli(), next.expires().toEpochMilli(),
                encode(next), row.id().toString(), row.owner(), row.revision()) == 1;
    }

    List<AgentSessions.SessionSummary> list(long owner, Instant now, int limit, long offset) {
        return jdbc.query("""
                SELECT id,title,updated_ms FROM agent_session
                WHERE owner_id=? AND expires_ms>? ORDER BY updated_ms DESC,id LIMIT ? OFFSET ?
                """, (rs,n) -> new AgentSessions.SessionSummary(UUID.fromString(rs.getString(1)),rs.getString(2),
                Instant.ofEpochMilli(rs.getLong(3))), owner, now.toEpochMilli(), limit, offset);
    }

    boolean delete(UUID id, long owner) {
        return jdbc.update("DELETE FROM agent_session WHERE id=? AND owner_id=?", id.toString(), owner) == 1;
    }

    void receipt(UUID session, long owner, AgentSessions.Action action, Instant now) {
        jdbc.update("""
                INSERT INTO agent_action_receipt(action_id,owner_id,session_id,action_type,digest,committed_ms,result)
                VALUES(?,?,?,?,?,?,?)
                """, action.id().toString(), owner, session.toString(), action.proposal().type(), action.digest(),
                now.toEpochMilli(), encode(action.result()));
    }

    JsonNode receipt(UUID id, long owner, UUID session, String digest) {
        return jdbc.query("""
                SELECT result FROM agent_action_receipt WHERE action_id=? AND owner_id=? AND session_id=? AND digest=?
                """, (rs,n) -> decode(rs.getString(1), JsonNode.class), id.toString(), owner, session.toString(), digest)
                .stream().findFirst().orElse(null);
    }


    private String encode(Object data) {
        try { return mapper.writeValueAsString(data); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot encode agent state", ex); }
    }

    private <T> T decode(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot decode agent state", ex); }
    }
}
