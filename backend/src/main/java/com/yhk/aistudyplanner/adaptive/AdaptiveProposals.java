package com.yhk.aistudyplanner.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdaptiveProposals {
    record Preview(UUID id, String digest, Instant expiresAt, JsonNode data) {}
    record Stored(String kind, String digest, Instant expiresAt, JsonNode payload, String decision, JsonNode result) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuthSessionService auth;
    private final Clock clock;

    AdaptiveProposals(JdbcTemplate jdbc,ObjectMapper mapper,AuthSessionService auth,Clock clock) {
        this.jdbc=jdbc; this.mapper=mapper; this.auth=auth; this.clock=clock;
    }
    Preview open(String kind, Object payload, Object preview) {
        UUID id=UUID.randomUUID();
        Instant expires=clock.instant().plus(Duration.ofMinutes(15));
        JsonNode data=mapper.valueToTree(payload);
        String digest=fingerprint(data);
        jdbc.update("INSERT INTO adaptive_proposal(id,owner_id,kind,digest,expires_ms,payload) VALUES(?,?,?,?,?,?)",
                id.toString(),auth.currentUserId(),kind,digest,expires.toEpochMilli(),data.toString());
        return new Preview(id,digest,expires,mapper.valueToTree(preview));
    }
    JsonNode decide(UUID id, String kind, String digest, String decision, Function<JsonNode,Object> apply) {
        long owner=auth.currentUserId();
        // Share the per-owner serialization point with ordinary plan confirmation.
        if (jdbc.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE",Long.class,owner).isEmpty())
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var stored=jdbc.query("SELECT kind,digest,expires_ms,payload,decision,result FROM adaptive_proposal WHERE id=? AND owner_id=? FOR UPDATE",
                (r,n)->new Stored(r.getString(1),r.getString(2),Instant.ofEpochMilli(r.getLong(3)),
                        read(r.getString(4)),r.getString(5),read(r.getString(6))),id.toString(),owner)
                .stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!stored.kind().equals(kind) || !stored.digest().equals(digest)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (stored.decision()!=null) {
            if (!stored.decision().equals(decision)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
            return stored.result();
        }
        if (!stored.expiresAt().isAfter(clock.instant())) throw new BusinessException(ErrorCode.AGENT_ACTION_NOT_FOUND);
        JsonNode result=mapper.valueToTree(decision.equals("REJECT") ? Map.of("status","CANCELLED") : apply.apply(stored.payload()));
        jdbc.update("UPDATE adaptive_proposal SET decision=?,result=? WHERE id=? AND owner_id=?",
                decision,result.toString(),id.toString(),owner);
        return result;
    }
    String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot compute preview fingerprint",e);
        }
    }
    private JsonNode read(String json) {
        try { return json == null ? null : mapper.readTree(json); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid saved proposal",e); }
    }
}
