
package com.substring.docmind.study.controller;

import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.study.dto.*;
import com.substring.docmind.study.service.StudyTrackerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
@Tag(name = "Smart Study Tracker & Dashboard", description = "Endpoints for live study timer, courses, AI course planner, analytics, calendar and achievements")
public class StudyTrackerController {

    private final StudyTrackerService studyTrackerService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get Single-Page Study Dashboard Summary", description = "Returns live study hours, courses, AI completion plan, analytics, calendar heatmap, and achievements")
    public ResponseEntity<ApiResponse<StudyDashboardSummaryDto>> getDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to fetch Study Dashboard for user: {}", currentUser.getId());
        StudyDashboardSummaryDto summary = studyTrackerService.getDashboardSummary(currentUser);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @PostMapping("/session")
    @Operation(summary = "Record Study Session", description = "Saves completed focus session duration and updates streak, hours, and achievements")
    public ResponseEntity<ApiResponse<StudyDashboardSummaryDto>> recordSession(
            @RequestBody RecordSessionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to record study session for user {}: {} mins", currentUser.getId(),
                request.getDurationMinutes());
        StudyDashboardSummaryDto updatedSummary = studyTrackerService.recordSession(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Session recorded successfully", updatedSummary));
    }

    @PostMapping("/courses")
    @Operation(summary = "Create Course", description = "Adds a new course with initial topics and target deadline")
    public ResponseEntity<ApiResponse<CourseDto>> createCourse(
            @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to create course: {}", request.getTitle());
        CourseDto course = studyTrackerService.createCourse(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Course created successfully", course));
    }

    @PutMapping("/courses/{id}")
    @Operation(summary = "Update Course", description = "Updates course details or target deadline")
    public ResponseEntity<ApiResponse<CourseDto>> updateCourse(
            @PathVariable Long id,
            @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to update course: {}", id);
        CourseDto course = studyTrackerService.updateCourse(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", course));
    }

    @DeleteMapping("/courses/{id}")
    @Operation(summary = "Delete Course", description = "Removes course and its topics")
    public ResponseEntity<ApiResponse<String>> deleteCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to delete course: {}", id);
        studyTrackerService.deleteCourse(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully"));
    }

    @PostMapping("/courses/{id}/topics")
    @Operation(summary = "Add Topic to Course", description = "Appends a new topic with estimated hours")
    public ResponseEntity<ApiResponse<CourseTopicDto>> addTopic(
            @PathVariable Long id,
            @RequestBody CreateTopicRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to add topic to course {}: {}", id, request.getName());
        CourseTopicDto topic = studyTrackerService.addTopic(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Topic added successfully", topic));
    }

    @PutMapping("/courses/{courseId}/topics/{topicId}")
    @Operation(summary = "Update / Toggle Topic", description = "Updates topic completion status, name, or estimated hours")
    public ResponseEntity<ApiResponse<CourseTopicDto>> updateTopic(
            @PathVariable Long courseId,
            @PathVariable Long topicId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        String name = (String) body.get("name");
        Double hours = body.get("estimatedHours") != null ? Double.valueOf(body.get("estimatedHours").toString())
                : null;
        Boolean completed = (Boolean) body.get("completed");

        CourseTopicDto topic = studyTrackerService.updateTopic(courseId, topicId, name, hours, completed, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Topic updated successfully", topic));
    }

    @DeleteMapping("/courses/{courseId}/topics/{topicId}")
    @Operation(summary = "Delete Topic", description = "Removes a topic from course")
    public ResponseEntity<ApiResponse<String>> deleteTopic(
            @PathVariable Long courseId,
            @PathVariable Long topicId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        studyTrackerService.deleteTopic(courseId, topicId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Topic deleted successfully"));
    }

    @GetMapping("/ai-planner")
    @Operation(summary = "Get AI Course Completion & Recovery Plan", description = "Generates personalized course completion recommendations and dynamic recovery plans")
    public ResponseEntity<ApiResponse<AiPlannerDto>> getAiPlanner(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        AiPlannerDto planner = studyTrackerService.generateAiRecoveryPlan(currentUser);
        return ResponseEntity.ok(ApiResponse.success(planner));
    }
}
