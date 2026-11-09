package com.substring.docmind.growth.controller;

import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.growth.dto.PersonalizedRecommendationDto;
import com.substring.docmind.growth.dto.TopicGrowthDashboardDto;
import com.substring.docmind.growth.dto.TopicHierarchyDto;
import com.substring.docmind.growth.service.TopicExtractionService;
import com.substring.docmind.growth.service.TopicGrowthService;
import com.substring.docmind.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/growth")
@RequiredArgsConstructor
@Tag(name = "Topic Growth & Progress Analytics", description = "Endpoints for topic-wise practice tracking, knowledge strength scoring, and learning analytics")
public class TopicGrowthController {

    private final TopicGrowthService topicGrowthService;
    private final TopicExtractionService topicExtractionService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get Topic Growth Dashboard", description = "Retrieves complete topic practice progress, strength analysis, activity, and recommendations")
    public ResponseEntity<ApiResponse<TopicGrowthDashboardDto>> getDashboard(
            @RequestParam(required = false, defaultValue = "all") String timeRange,
            @RequestParam(required = false) String documentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to fetch Topic Growth Dashboard for user: {}, timeRange: {}, documentId: {}",
                currentUser.getId(), timeRange, documentId);
        TopicGrowthDashboardDto dashboard = topicGrowthService.getGrowthDashboard(timeRange, documentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    @GetMapping("/topics")
    @Operation(summary = "Get Topic Hierarchy List", description = "Returns all extracted topics and subtopics with practice metrics")
    public ResponseEntity<ApiResponse<List<TopicHierarchyDto>>> getTopics(
            @RequestParam(required = false) String documentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to fetch Topic Hierarchy for user: {}", currentUser.getId());
        List<TopicHierarchyDto> topics = topicGrowthService.getTopicHierarchy(documentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(topics));
    }

    @GetMapping("/topics/{id}/history")
    @Operation(summary = "Get Topic Practice History", description = "Returns detailed attempt history and strength snapshots for a specific topic")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTopicHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to fetch Topic Practice History for topic id: {} and user: {}", id, currentUser.getId());
        Map<String, Object> history = topicGrowthService.getTopicPracticeHistory(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Get Personalized Learning Recommendations", description = "Returns targeted practice and revision suggestions based on real performance")
    public ResponseEntity<ApiResponse<List<PersonalizedRecommendationDto>>> getRecommendations(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to fetch Personalized Recommendations for user: {}", currentUser.getId());
        List<PersonalizedRecommendationDto> recs = topicGrowthService.getRecommendations(currentUser);
        return ResponseEntity.ok(ApiResponse.success(recs));
    }

    @PostMapping("/sync-topics")
    @Operation(summary = "Sync and extract study topics", description = "Scans all website files and discovers topics and subtopics")
    public ResponseEntity<ApiResponse<String>> syncTopics(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to sync study topics across platform by user: {}", currentUser.getId());
        topicExtractionService.scanAndExtractAllTopics();
        return ResponseEntity.ok(ApiResponse.success("Study topics synchronization completed successfully."));
    }
}
