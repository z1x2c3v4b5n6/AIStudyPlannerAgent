package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import java.time.*;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class KnowledgeRefresh {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeRefresh.class);
    private final KnowledgeHub hub;
    private final OfficialDocuments collector;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final long checkHours;
    private final boolean enabled;

    KnowledgeRefresh(KnowledgeHub hub, OfficialDocuments collector, StringRedisTemplate redis, Clock clock,
                     @Value("${app.knowledge.refresh-enabled:false}") boolean enabled,
                     @Value("${app.knowledge.recheck-hours:168}") long checkHours) {
        this.hub=hub;
        this.collector=collector;
        this.redis=redis;
        this.clock=clock;
        this.enabled=enabled;
        this.checkHours=Math.max(1,Math.min(checkHours,720));
    }

    @Scheduled(fixedDelayString="${app.knowledge.refresh-delay-ms:3600000}", initialDelayString="${app.knowledge.refresh-delay-ms:3600000}")
    void check() {
        if(!enabled || !collector.enabled()) return;
        for(var source:hub.dueSources(LocalDateTime.now(clock).minusHours(checkHours))) {
            // The lease outlives a bounded request and expires without deleting another worker's lease.
            if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("knowledge:refresh:"+source.id(),
                    UUID.randomUUID().toString(),Duration.ofMinutes(1)))) continue;
            try {
                hub.stageCapture(source,collector.fetch(source),null);
                hub.recordCheck(source.id(),null);
            } catch(BusinessException failure) {
                hub.recordCheck(source.id(),failure.getErrorCode().name());
                LOG.warn("knowledge_refresh_failure source={} code={}",source.id(),failure.getErrorCode().name());
            }
        }
    }
}
