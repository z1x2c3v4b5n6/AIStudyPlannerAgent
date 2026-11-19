package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge-hub")
class KnowledgeHubController {
    private final KnowledgeHub hub;
    private final KnowledgeHubSearch search;
    private final OfficialDocuments collector;
    private final KnowledgeExtraction extraction;

    KnowledgeHubController(KnowledgeHub hub, KnowledgeHubSearch search, OfficialDocuments collector, KnowledgeExtraction extraction) {
        this.hub=hub;
        this.search=search;
        this.collector=collector;
        this.extraction=extraction;
    }

    @GetMapping("/capabilities")
    ApiResponse<?> capabilities() { return ApiResponse.success(new Capabilities(hub.reviewer(),collector.enabled())); }
    @PostMapping("/search")
    ApiResponse<?> search(@Valid @RequestBody KnowledgeHubSearch.Query query) { return ApiResponse.success(search.search(query)); }
    @GetMapping("/personal")
    ApiResponse<?> personal() { return ApiResponse.success(hub.list(false)); }
    @PostMapping("/personal")
    ApiResponse<?> upload(@Valid @RequestBody KnowledgeHub.Import input) { return ApiResponse.success(hub.importPrivate(input)); }
    @DeleteMapping("/personal/{id}")
    ApiResponse<Void> delete(@PathVariable UUID id) { hub.removePrivate(id.toString()); return ApiResponse.success(null); }
    @GetMapping("/versions/{id}")
    ApiResponse<?> view(@PathVariable UUID id) { return ApiResponse.success(hub.detail(id.toString(),false)); }
    @PostMapping("/versions/{id}/feedback")
    ApiResponse<Void> feedback(@PathVariable UUID id,@Valid @RequestBody KnowledgeHub.Feedback input) {
        hub.feedback(id.toString(),input);
        return ApiResponse.success(null);
    }

    @GetMapping("/manage/sources")
    ApiResponse<?> sources() { return ApiResponse.success(hub.sources()); }
    @PostMapping("/manage/sources")
    ApiResponse<?> add(@Valid @RequestBody KnowledgeHub.NewSource input) { return ApiResponse.success(hub.addSource(input)); }
    @PostMapping("/manage/sources/{id}/candidate")
    ApiResponse<?> stage(@PathVariable UUID id,@Valid @RequestBody KnowledgeHub.Import input) {
        return ApiResponse.success(hub.stage(id.toString(),input));
    }
    @PostMapping("/manage/sources/{id}/refresh")
    ApiResponse<?> refresh(@PathVariable UUID id,@Valid @RequestBody Refresh input) {
        hub.requireReviewer();
        var source=hub.source(id.toString(),false);
        try {
            var snapshot=collector.fetch(source);
            var staged=hub.stageCapture(source,snapshot,input.unitId());
            hub.recordCheck(source.id(),null);
            return ApiResponse.success(staged);
        } catch(BusinessException failure) {
            hub.recordCheck(source.id(),failure.getErrorCode().name());
            throw failure;
        }
    }
    @GetMapping("/manage/versions")
    ApiResponse<?> pending() { return ApiResponse.success(hub.list(true)); }
    @GetMapping("/manage/versions/{id}")
    ApiResponse<?> inspect(@PathVariable UUID id) { return ApiResponse.success(hub.detail(id.toString(),true)); }
    @GetMapping("/manage/versions/{id}/difference")
    ApiResponse<?> difference(@PathVariable UUID id) { return ApiResponse.success(hub.difference(id.toString())); }
    @PostMapping("/manage/versions/{id}/decision")
    ApiResponse<?> approve(@PathVariable UUID id,@Valid @RequestBody KnowledgeHub.Review input) {
        return ApiResponse.success(hub.review(id.toString(),input));
    }
    @PostMapping("/manage/versions/{id}/extract")
    ApiResponse<?> extract(@PathVariable UUID id) { return ApiResponse.success(extraction.preview(id.toString())); }
    @GetMapping("/manage/documents/{id}/history")
    ApiResponse<?> history(@PathVariable UUID id) { return ApiResponse.success(hub.history(id.toString())); }
    @GetMapping("/manage/signals")
    ApiResponse<?> signals() { return ApiResponse.success(hub.signals()); }

    record Capabilities(boolean reviewer, boolean onlineEnabled) {}
    record Refresh(@Size(max=100) String unitId) {}
}
