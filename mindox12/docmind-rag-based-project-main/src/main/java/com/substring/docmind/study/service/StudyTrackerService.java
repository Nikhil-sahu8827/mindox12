package com.substring.docmind.study.service;

import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.study.dto.*;

public interface StudyTrackerService {
    StudyDashboardSummaryDto getDashboardSummary(UserPrincipal currentUser);
    StudyDashboardSummaryDto recordSession(RecordSessionRequest request, UserPrincipal currentUser);
    CourseDto createCourse(CreateCourseRequest request, UserPrincipal currentUser);
    CourseDto updateCourse(Long courseId, CreateCourseRequest request, UserPrincipal currentUser);
    void deleteCourse(Long courseId, UserPrincipal currentUser);
    CourseTopicDto addTopic(Long courseId, CreateTopicRequest request, UserPrincipal currentUser);
    CourseTopicDto updateTopic(Long courseId, Long topicId, String name, Double estimatedHours, Boolean completed, UserPrincipal currentUser);
    void deleteTopic(Long courseId, Long topicId, UserPrincipal currentUser);
    AiPlannerDto generateAiRecoveryPlan(UserPrincipal currentUser);
}
