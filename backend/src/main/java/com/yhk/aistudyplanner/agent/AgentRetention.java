package com.yhk.aistudyplanner.agent;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AgentRetention {
    private static final Logger LOG = LoggerFactory.getLogger(AgentRetention.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration retention;

    AgentRetention(JdbcTemplate jdbc, Clock clock, @Value("${app.agent.audit-retention-days:30}") long days) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = Duration.ofDays(Math.max(days, 7));
    }

    @Scheduled(fixedDelayString="${app.agent.cleanup-delay-ms:3600000}", initialDelayString="${app.agent.cleanup-delay-ms:3600000}")
    void cleanup() {
        long cutoff = clock.instant().minus(retention).toEpochMilli();
        int workflows = jdbc.update("DELETE FROM agent_workflow WHERE expires_ms < ? ORDER BY expires_ms LIMIT 100", cutoff);
        int proposals = jdbc.update("DELETE FROM adaptive_proposal WHERE expires_ms < ? ORDER BY expires_ms LIMIT 100", cutoff);
        int receipts = jdbc.update("DELETE FROM agent_action_receipt WHERE committed_ms < ? ORDER BY committed_ms LIMIT 100", cutoff);
        int sessions = jdbc.update("""
                DELETE FROM agent_session WHERE expires_ms < ?
                AND NOT EXISTS(SELECT 1 FROM agent_workflow WHERE session_id=agent_session.id)
                ORDER BY expires_ms LIMIT 100
                """, cutoff);
        LOG.info("agent_retention workflows={} proposals={} receipts={} sessions={}", workflows, proposals, receipts, sessions);
    }
}
