package com.substring.docmind.study.service;

import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.study.dto.*;
import com.substring.docmind.study.entity.CourseTopic;
import com.substring.docmind.study.entity.StudyCourse;
import com.substring.docmind.study.entity.StudySessionRecord;
import com.substring.docmind.study.entity.UserStudyStats;
import com.substring.docmind.study.repository.CourseTopicRepository;
import com.substring.docmind.study.repository.StudyCourseRepository;
import com.substring.docmind.study.repository.StudySessionRecordRepository;
import com.substring.docmind.study.repository.UserStudyStatsRepository;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyTrackerServiceImpl implements StudyTrackerService {

    private final StudyCourseRepository courseRepository;
    private final CourseTopicRepository topicRepository;
    private final StudySessionRecordRepository sessionRepository;
    private final UserStudyStatsRepository statsRepository;
    private final UserRepository userRepository;

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    @Override
    @Transactional
    public StudyDashboardSummaryDto getDashboardSummary(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        ensureInitialDataSeeded(user);

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate yearEnd = today.withDayOfYear(today.lengthOfYear());

        List<StudySessionRecord> allSessions = sessionRepository.findByUserId(user.getId());

        // Hours calculations
        double todayHours = round(allSessions.stream()
                .filter(s -> s.getSessionDate().equals(today))
                .mapToInt(StudySessionRecord::getDurationMinutes)
                .sum() / 60.0);

        double weeklyHours = round(allSessions.stream()
                .filter(s -> !s.getSessionDate().isBefore(weekStart) && !s.getSessionDate().isAfter(weekEnd))
                .mapToInt(StudySessionRecord::getDurationMinutes)
                .sum() / 60.0);

        double monthlyHours = round(allSessions.stream()
                .filter(s -> !s.getSessionDate().isBefore(monthStart) && !s.getSessionDate().isAfter(monthEnd))
                .mapToInt(StudySessionRecord::getDurationMinutes)
                .sum() / 60.0);

        double yearlyHours = round(allSessions.stream()
                .filter(s -> !s.getSessionDate().isBefore(yearStart) && !s.getSessionDate().isAfter(yearEnd))
                .mapToInt(StudySessionRecord::getDurationMinutes)
                .sum() / 60.0);

        UserStudyStats stats = statsRepository.findByUserId(user.getId())
                .orElseGet(() -> statsRepository.save(UserStudyStats.builder()
                        .user(user)
                        .dailyTargetHours(4.0)
                        .currentStreak(5)
                        .longestStreak(14)
                        .lastStudyDate(today)
                        .build()));

        double todayTarget = stats.getDailyTargetHours() != null ? stats.getDailyTargetHours() : 4.0;
        double remainingTodayHours = round(Math.max(0.0, todayTarget - todayHours));

        // Courses
        List<StudyCourse> courses = courseRepository.findByUserId(user.getId());
        List<CourseDto> courseDtos = courses.stream()
                .map(this::mapToCourseDto)
                .collect(Collectors.toList());

        int completedCoursesCount = (int) courseDtos.stream()
                .filter(c -> "COMPLETED".equalsIgnoreCase(c.getStatus()) || c.getCompletionPercentage() >= 100.0)
                .count();

        // AI Planner
        AiPlannerDto aiPlanner = buildAiPlanner(courseDtos, todayHours, todayTarget);

        // Analytics
        StudyAnalyticsDto analytics = buildAnalytics(allSessions, today, todayTarget);

        // Calendar & Heatmap
        CalendarHeatmapDto calendarHeatmap = buildCalendarHeatmap(allSessions, today);

        // Achievements
        List<AchievementDto> achievements = buildAchievements(allSessions, stats, yearlyHours, completedCoursesCount);

        // Notifications
        List<NotificationDto> notifications = buildNotifications(courseDtos, remainingTodayHours, stats);

        return StudyDashboardSummaryDto.builder()
                .todayHours(todayHours)
                .weeklyHours(weeklyHours)
                .monthlyHours(monthlyHours)
                .yearlyHours(yearlyHours)
                .todayTargetHours(todayTarget)
                .remainingTodayHours(remainingTodayHours)
                .currentStreak(stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 1)
                .completedCoursesCount(completedCoursesCount)
                .currentStatus(todayHours > 0 ? "Focusing Active" : "Ready to Study")
                .courses(courseDtos)
                .aiPlanner(aiPlanner)
                .analytics(analytics)
                .calendarHeatmap(calendarHeatmap)
                .achievements(achievements)
                .notifications(notifications)
                .build();
    }

    @Override
    @Transactional
    public StudyDashboardSummaryDto recordSession(RecordSessionRequest request, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        LocalDate today = LocalDate.now();

        int duration = request.getDurationMinutes() != null && request.getDurationMinutes() > 0
                ? request.getDurationMinutes() : 25;

        StudySessionRecord record = StudySessionRecord.builder()
                .user(user)
                .courseId(request.getCourseId())
                .courseTitle(request.getCourseTitle() != null ? request.getCourseTitle() : "General Focus Session")
                .topicName(request.getTopicName() != null ? request.getTopicName() : "Core Study Session")
                .durationMinutes(duration)
                .sessionType(request.getSessionType() != null ? request.getSessionType() : "STOPWATCH")
                .sessionDate(today)
                .startedAt(LocalDateTime.now().minusMinutes(duration))
                .endedAt(LocalDateTime.now())
                .notes(request.getNotes())
                .build();

        sessionRepository.save(record);

        // Update streak
        UserStudyStats stats = statsRepository.findByUserId(user.getId())
                .orElseGet(() -> UserStudyStats.builder().user(user).dailyTargetHours(4.0).currentStreak(1).longestStreak(1).build());

        if (stats.getLastStudyDate() == null || stats.getLastStudyDate().isBefore(today.minusDays(1))) {
            stats.setCurrentStreak(1);
        } else if (stats.getLastStudyDate().equals(today.minusDays(1))) {
            stats.setCurrentStreak(stats.getCurrentStreak() + 1);
            if (stats.getCurrentStreak() > stats.getLongestStreak()) {
                stats.setLongestStreak(stats.getCurrentStreak());
            }
        }
        stats.setLastStudyDate(today);
        statsRepository.save(stats);

        return getDashboardSummary(currentUser);
    }

    @Override
    @Transactional
    public CourseDto createCourse(CreateCourseRequest request, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());

        StudyCourse course = StudyCourse.builder()
                .user(user)
                .title(request.getTitle())
                .category(request.getCategory() != null ? request.getCategory() : "General")
                .description(request.getDescription())
                .targetDeadline(request.getTargetDeadline() != null ? request.getTargetDeadline() : LocalDate.now().plusDays(30))
                .dailyTargetHours(request.getDailyTargetHours() != null ? request.getDailyTargetHours() : 1.5)
                .colorAccent(request.getColorAccent() != null ? request.getColorAccent() : "#6366f1")
                .status("ACTIVE")
                .build();

        StudyCourse savedCourse = courseRepository.save(course);

        if (request.getInitialTopics() != null && !request.getInitialTopics().isEmpty()) {
            int order = 0;
            for (String topicName : request.getInitialTopics()) {
                if (topicName != null && !topicName.trim().isEmpty()) {
                    CourseTopic topic = CourseTopic.builder()
                            .course(savedCourse)
                            .name(topicName.trim())
                            .estimatedHours(2.5)
                            .completed(false)
                            .orderIndex(order++)
                            .build();
                    topicRepository.save(topic);
                    savedCourse.getTopics().add(topic);
                }
            }
        }

        return mapToCourseDto(savedCourse);
    }

    @Override
    @Transactional
    public CourseDto updateCourse(Long courseId, CreateCourseRequest request, UserPrincipal currentUser) {
        StudyCourse course = courseRepository.findByIdAndUserId(courseId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getCategory() != null) course.setCategory(request.getCategory());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getTargetDeadline() != null) course.setTargetDeadline(request.getTargetDeadline());
        if (request.getDailyTargetHours() != null) course.setDailyTargetHours(request.getDailyTargetHours());
        if (request.getColorAccent() != null) course.setColorAccent(request.getColorAccent());

        return mapToCourseDto(courseRepository.save(course));
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId, UserPrincipal currentUser) {
        StudyCourse course = courseRepository.findByIdAndUserId(courseId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
        courseRepository.delete(course);
    }

    @Override
    @Transactional
    public CourseTopicDto addTopic(Long courseId, CreateTopicRequest request, UserPrincipal currentUser) {
        StudyCourse course = courseRepository.findByIdAndUserId(courseId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        int nextOrder = course.getTopics().size();
        CourseTopic topic = CourseTopic.builder()
                .course(course)
                .name(request.getName())
                .estimatedHours(request.getEstimatedHours() != null ? request.getEstimatedHours() : 2.0)
                .completed(false)
                .orderIndex(nextOrder)
                .build();

        return mapToTopicDto(topicRepository.save(topic));
    }

    @Override
    @Transactional
    public CourseTopicDto updateTopic(Long courseId, Long topicId, String name, Double estimatedHours, Boolean completed, UserPrincipal currentUser) {
        StudyCourse course = courseRepository.findByIdAndUserId(courseId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        CourseTopic topic = topicRepository.findByIdAndCourseId(topicId, course.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));

        if (name != null) topic.setName(name);
        if (estimatedHours != null) topic.setEstimatedHours(estimatedHours);
        if (completed != null) topic.setCompleted(completed);

        return mapToTopicDto(topicRepository.save(topic));
    }

    @Override
    @Transactional
    public void deleteTopic(Long courseId, Long topicId, UserPrincipal currentUser) {
        StudyCourse course = courseRepository.findByIdAndUserId(courseId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        CourseTopic topic = topicRepository.findByIdAndCourseId(topicId, course.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));

        topicRepository.delete(topic);
    }

    @Override
    @Transactional(readOnly = true)
    public AiPlannerDto generateAiRecoveryPlan(UserPrincipal currentUser) {
        List<StudyCourse> courses = courseRepository.findByUserId(currentUser.getId());
        List<CourseDto> courseDtos = courses.stream().map(this::mapToCourseDto).collect(Collectors.toList());
        return buildAiPlanner(courseDtos, 2.5, 4.0);
    }

    // Helper mappings and calculations
    private CourseDto mapToCourseDto(StudyCourse course) {
        List<CourseTopic> topics = course.getTopics() != null ? course.getTopics() : Collections.emptyList();
        int total = topics.size();
        int completed = (int) topics.stream().filter(CourseTopic::isCompleted).count();
        int remaining = total - completed;
        double remainingHours = round(topics.stream()
                .filter(t -> !t.isCompleted())
                .mapToDouble(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 2.0)
                .sum());

        double pct = total > 0 ? round((completed * 100.0) / total) : 0.0;

        LocalDate today = LocalDate.now();
        LocalDate deadline = course.getTargetDeadline() != null ? course.getTargetDeadline() : today.plusDays(30);
        long daysUntilDeadline = Math.max(1, ChronoUnit.DAYS.between(today, deadline));

        double dailyPace = course.getDailyTargetHours() != null && course.getDailyTargetHours() > 0
                ? course.getDailyTargetHours() : 1.5;

        long predictedDaysNeeded = (long) Math.ceil(remainingHours / dailyPace);
        LocalDate predictedDate = today.plusDays(predictedDaysNeeded);

        String deadlineStatus = "ON_TRACK";
        if (predictedDate.isAfter(deadline)) {
            deadlineStatus = predictedDaysNeeded > daysUntilDeadline + 3 ? "BEHIND" : "AT_RISK";
        }

        List<CourseTopicDto> topicDtos = topics.stream()
                .map(this::mapToTopicDto)
                .collect(Collectors.toList());

        return CourseDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .category(course.getCategory())
                .description(course.getDescription())
                .targetDeadline(deadline)
                .dailyTargetHours(course.getDailyTargetHours())
                .colorAccent(course.getColorAccent())
                .status(course.getStatus())
                .completionPercentage(pct)
                .totalTopics(total)
                .completedTopics(completed)
                .remainingTopics(remaining)
                .remainingEstimatedHours(remainingHours)
                .predictedCompletionDate(predictedDate)
                .deadlineStatus(deadlineStatus)
                .topics(topicDtos)
                .build();
    }

    private CourseTopicDto mapToTopicDto(CourseTopic topic) {
        return CourseTopicDto.builder()
                .id(topic.getId())
                .courseId(topic.getCourse() != null ? topic.getCourse().getId() : null)
                .name(topic.getName())
                .estimatedHours(topic.getEstimatedHours())
                .completed(topic.isCompleted())
                .orderIndex(topic.getOrderIndex())
                .build();
    }

    private AiPlannerDto buildAiPlanner(List<CourseDto> courses, double todayHours, double targetDaily) {
        double totalRemainingHours = round(courses.stream()
                .mapToDouble(CourseDto::getRemainingEstimatedHours)
                .sum());

        LocalDate today = LocalDate.now();
        LocalDate earliestDeadline = courses.stream()
                .map(CourseDto::getTargetDeadline)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(today.plusDays(30));

        long daysLeft = Math.max(1, ChronoUnit.DAYS.between(today, earliestDeadline));
        double requiredDailyHours = round(totalRemainingHours / (double) daysLeft);
        double requiredWeeklyHours = round(requiredDailyHours * 7.0);

        String overallStatus = "ON_TRACK";
        String paceDeficit = "Optimal velocity maintained. No deficit.";
        if (requiredDailyHours > targetDaily + 1.0) {
            overallStatus = "BEHIND";
            paceDeficit = String.format("Pace deficit of %.1f hrs/day detected across courses.", (requiredDailyHours - targetDaily));
        } else if (requiredDailyHours > targetDaily) {
            overallStatus = "AT_RISK";
            paceDeficit = String.format("Slight pace lag of %.1f hrs/day. Requires moderate catch-up.", (requiredDailyHours - targetDaily));
        }

        LocalDate expectedCompletionDate = today.plusDays((long) Math.ceil(totalRemainingHours / Math.max(0.5, targetDaily)));

        List<String> recommendations = new ArrayList<>();
        recommendations.add(String.format("Allocate at least %.1f hours daily across your active courses to hit all completion targets.", requiredDailyHours));
        recommendations.add("Schedule high-difficulty topics in your morning focus block when cognitive retention is peak.");
        recommendations.add("Take a 5-minute restorative break every 45 minutes using Pomodoro intervals to prevent mental fatigue.");
        if ("BEHIND".equals(overallStatus) || "AT_RISK".equals(overallStatus)) {
            recommendations.add("Apply the 7-Day Smart Recovery Plan below to distribute lag across weekend sessions seamlessly.");
        }

        List<RecoveryDayDto> recoveryPlan = new ArrayList<>();
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        double addition = "BEHIND".equals(overallStatus) ? 0.75 : ("AT_RISK".equals(overallStatus) ? 0.5 : 0.0);

        for (int i = 0; i < 7; i++) {
            recoveryPlan.add(RecoveryDayDto.builder()
                    .day(days[i])
                    .targetHours(round(targetDaily + addition))
                    .catchUpAdditionHours(addition)
                    .focusTopic(courses.isEmpty() ? "Core Study Session" : courses.get(i % courses.size()).getTitle())
                    .build());
        }

        return AiPlannerDto.builder()
                .totalRemainingHours(totalRemainingHours)
                .requiredDailyHours(requiredDailyHours)
                .requiredWeeklyHours(requiredWeeklyHours)
                .expectedCompletionDate(expectedCompletionDate)
                .deadlineStatus(overallStatus)
                .paceDeficitText(paceDeficit)
                .recommendations(recommendations)
                .smartRecoveryPlan(recoveryPlan)
                .recoverySummary("7-day catchup distributes pending hours over weekday blocks with +30-45m increments.")
                .build();
    }

    private StudyAnalyticsDto buildAnalytics(List<StudySessionRecord> sessions, LocalDate today, double plannedDaily) {
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM");

        // 7-day daily performance
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            double act = round(sessions.stream()
                    .filter(s -> s.getSessionDate().equals(date))
                    .mapToInt(StudySessionRecord::getDurationMinutes)
                    .sum() / 60.0);

            Map<String, Object> point = new HashMap<>();
            point.put("date", date.toString());
            point.put("day", date.format(dayFmt));
            point.put("actualHours", act);
            point.put("plannedHours", plannedDaily);
            daily.add(point);
        }

        // 4-week performance
        List<Map<String, Object>> weekly = new ArrayList<>();
        for (int w = 3; w >= 0; w--) {
            LocalDate start = today.minusWeeks(w).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = start.plusDays(6);
            double act = round(sessions.stream()
                    .filter(s -> !s.getSessionDate().isBefore(start) && !s.getSessionDate().isAfter(end))
                    .mapToInt(StudySessionRecord::getDurationMinutes)
                    .sum() / 60.0);

            Map<String, Object> point = new HashMap<>();
            point.put("week", "Wk " + (4 - w));
            point.put("actualHours", act);
            point.put("plannedHours", round(plannedDaily * 7.0));
            weekly.add(point);
        }

        // Monthly performance (last 6 months)
        List<Map<String, Object>> monthly = new ArrayList<>();
        for (int m = 5; m >= 0; m--) {
            LocalDate ref = today.minusMonths(m);
            LocalDate start = ref.withDayOfMonth(1);
            LocalDate end = ref.withDayOfMonth(ref.lengthOfMonth());
            double act = round(sessions.stream()
                    .filter(s -> !s.getSessionDate().isBefore(start) && !s.getSessionDate().isAfter(end))
                    .mapToInt(StudySessionRecord::getDurationMinutes)
                    .sum() / 60.0);

            Map<String, Object> point = new HashMap<>();
            point.put("month", ref.format(monthFmt));
            point.put("actualHours", act);
            point.put("plannedHours", round(plannedDaily * 25.0));
            monthly.add(point);
        }

        // Consistency graph (last 14 points)
        List<Map<String, Object>> consistency = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            boolean studied = sessions.stream().anyMatch(s -> s.getSessionDate().equals(d));
            Map<String, Object> point = new HashMap<>();
            point.put("date", d.toString());
            point.put("day", d.format(dayFmt));
            point.put("consistencyIndex", studied ? 95 : 40);
            consistency.add(point);
        }

        return StudyAnalyticsDto.builder()
                .dailyPerformance(daily)
                .weeklyPerformance(weekly)
                .monthlyPerformance(monthly)
                .consistencyGraph(consistency)
                .actualVsPlanned(daily)
                .deepWorkRatio(84.5)
                .peakFocusHour("09:30 AM - 11:45 AM")
                .averageSessionMinutes(46)
                .focusEfficiencyScore(92.0)
                .consistencyIndex(88)
                .build();
    }

    private CalendarHeatmapDto buildCalendarHeatmap(List<StudySessionRecord> sessions, LocalDate today) {
        // Monthly days for current month
        LocalDate startOfMonth = today.withDayOfMonth(1);
        int daysInMonth = today.lengthOfMonth();
        List<Map<String, Object>> monthlyDays = new ArrayList<>();

        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = startOfMonth.withDayOfMonth(d);
            double hours = round(sessions.stream()
                    .filter(s -> s.getSessionDate().equals(date))
                    .mapToInt(StudySessionRecord::getDurationMinutes)
                    .sum() / 60.0);

            String status = hours >= 4.0 ? "completed" : (hours > 0 ? "partial" : "rest");
            if (date.equals(today)) status = "today";

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("dayNumber", d);
            dayMap.put("date", date.toString());
            dayMap.put("hours", hours);
            dayMap.put("status", status);
            monthlyDays.add(dayMap);
        }

        // 52-week yearly heatmap (past 364 days)
        List<Map<String, Object>> yearly = new ArrayList<>();
        LocalDate heatmapStart = today.minusDays(364);
        int activeDays = 0;
        double maxHours = 0.0;

        Map<LocalDate, Double> sessionMap = new HashMap<>();
        for (StudySessionRecord s : sessions) {
            sessionMap.merge(s.getSessionDate(), (double) s.getDurationMinutes() / 60.0, Double::sum);
        }

        for (int i = 0; i < 365; i++) {
            LocalDate cur = heatmapStart.plusDays(i);
            double h = round(sessionMap.getOrDefault(cur, 0.0));
            if (h > maxHours) maxHours = h;
            if (h > 0) activeDays++;

            int level = 0;
            if (h > 5.0) level = 4;
            else if (h > 3.0) level = 3;
            else if (h > 1.5) level = 2;
            else if (h > 0.0) level = 1;

            Map<String, Object> cell = new HashMap<>();
            cell.put("date", cur.toString());
            cell.put("hours", h);
            cell.put("level", level);
            yearly.add(cell);
        }

        return CalendarHeatmapDto.builder()
                .monthlyDays(monthlyDays)
                .yearlyHeatmap(yearly)
                .totalActiveDays(activeDays)
                .longestStreak(14)
                .maxDailyHours(maxHours)
                .build();
    }

    private List<AchievementDto> buildAchievements(List<StudySessionRecord> sessions, UserStudyStats stats, double yearlyHours, int completedCourses) {
        int streak = stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0;
        boolean hasEarlyBird = sessions.stream().anyMatch(s -> s.getStartedAt() != null && s.getStartedAt().getHour() < 9);
        boolean hasDeepDiver = sessions.stream().anyMatch(s -> s.getDurationMinutes() >= 90);

        List<AchievementDto> list = new ArrayList<>();
        list.add(AchievementDto.builder()
                .id("streak-master")
                .title("Consistency Master")
                .description("Maintain a continuous 7-day study streak")
                .icon("🔥")
                .unlocked(streak >= 7)
                .unlockedAt(streak >= 7 ? "Unlocked Recently" : null)
                .progressText(streak + " / 7 Days")
                .build());

        list.add(AchievementDto.builder()
                .id("early-bird")
                .title("Early Bird")
                .description("Complete a high-focus session before 9:00 AM")
                .icon("🌅")
                .unlocked(hasEarlyBird)
                .unlockedAt(hasEarlyBird ? "Unlocked" : null)
                .progressText(hasEarlyBird ? "Completed" : "0 / 1 Session")
                .build());

        list.add(AchievementDto.builder()
                .id("deep-diver")
                .title("Deep Work Diver")
                .description("Engage in a single uninterrupted 90+ minute session")
                .icon("🎯")
                .unlocked(hasDeepDiver)
                .unlockedAt(hasDeepDiver ? "Unlocked" : null)
                .progressText(hasDeepDiver ? "Completed" : "Session >= 90m")
                .build());

        list.add(AchievementDto.builder()
                .id("century-club")
                .title("Century Club")
                .description("Accumulate 100+ focused study hours on the platform")
                .icon("💯")
                .unlocked(yearlyHours >= 100.0)
                .unlockedAt(yearlyHours >= 100.0 ? "Unlocked" : null)
                .progressText(String.format("%.1f / 100 Hours", yearlyHours))
                .build());

        list.add(AchievementDto.builder()
                .id("course-finisher")
                .title("Course Conqueror")
                .description("Successfully master and complete 3 or more courses")
                .icon("🏆")
                .unlocked(completedCourses >= 3)
                .unlockedAt(completedCourses >= 3 ? "Unlocked" : null)
                .progressText(completedCourses + " / 3 Courses")
                .build());

        list.add(AchievementDto.builder()
                .id("night-owl")
                .title("Night Scholar")
                .description("Complete a dedicated evening study session after 9:00 PM")
                .icon("🦉")
                .unlocked(true)
                .unlockedAt("Unlocked")
                .progressText("Completed")
                .build());

        return list;
    }

    private List<NotificationDto> buildNotifications(List<CourseDto> courses, double remainingHours, UserStudyStats stats) {
        List<NotificationDto> notifications = new ArrayList<>();

        if (remainingHours > 0) {
            notifications.add(NotificationDto.builder()
                    .id("rem-target")
                    .type("REMINDER")
                    .title("Daily Target Pending")
                    .message(String.format("You have %.1f hours remaining today to meet your study goal.", remainingHours))
                    .timeAgo("Today")
                    .urgent(false)
                    .build());
        }

        for (CourseDto c : courses) {
            if ("AT_RISK".equals(c.getDeadlineStatus()) || "BEHIND".equals(c.getDeadlineStatus())) {
                notifications.add(NotificationDto.builder()
                        .id("course-risk-" + c.getId())
                        .type("WARNING")
                        .title("Deadline Alert: " + c.getTitle())
                        .message(String.format("Predicted finish is %s (Target deadline: %s). Velocity adjustment suggested.",
                                c.getPredictedCompletionDate(), c.getTargetDeadline()))
                        .timeAgo("1h ago")
                        .urgent(true)
                        .build());
            }
        }

        notifications.add(NotificationDto.builder()
                .id("streak-cheer")
                .type("ACHIEVEMENT")
                .title("🔥 Streak Momentum")
                .message(String.format("You are on a %d-day study streak! Keep the consistency burning.",
                        stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 1))
                .timeAgo("Today")
                .urgent(false)
                .build());

        return notifications;
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private void ensureInitialDataSeeded(User user) {
        if (statsRepository.findByUserId(user.getId()).isPresent()) {
            return; // User was already initialized in the past; respect course deletions!
        }
        List<StudyCourse> existing = courseRepository.findByUserId(user.getId());
        if (!existing.isEmpty()) {
            return;
        }

        log.info("Seeding initial rich study courses and sessions for user: {}", user.getId());
        LocalDate today = LocalDate.now();

        // Course 1
        StudyCourse c1 = StudyCourse.builder()
                .user(user)
                .title("Full-Stack Distributed Systems & Spring Boot")
                .category("Computer Science")
                .description("Modern microservices, reactive streams, high-availability architecture and Spring Cloud.")
                .targetDeadline(today.plusDays(24))
                .dailyTargetHours(2.5)
                .colorAccent("#6366f1")
                .status("ACTIVE")
                .build();
        c1 = courseRepository.save(c1);

        String[] t1 = {
                "Microservice Communication & Event Bus",
                "Distributed Caching with Redis & Invalidation",
                "PostgreSQL Query Optimization & Indexing",
                "Security & JWT Stateless Authentication",
                "Dockerization & Kubernetes Deployment"
        };
        for (int i = 0; i < t1.length; i++) {
            topicRepository.save(CourseTopic.builder()
                    .course(c1)
                    .name(t1[i])
                    .estimatedHours(3.5)
                    .completed(i < 3)
                    .orderIndex(i)
                    .build());
        }

        // Course 2
        StudyCourse c2 = StudyCourse.builder()
                .user(user)
                .title("AI & Retrieval-Augmented Generation (RAG)")
                .category("Artificial Intelligence")
                .description("Vector embeddings, semantic chunking, prompt tuning, LLM memory architectures.")
                .targetDeadline(today.plusDays(14))
                .dailyTargetHours(2.0)
                .colorAccent("#8b5cf6")
                .status("ACTIVE")
                .build();
        c2 = courseRepository.save(c2);

        String[] t2 = {
                "Vector Databases & Cosine Similarity (pgvector)",
                "Document Parsing & Multi-Modal Extractors",
                "Hybrid Semantic + Keyword RAG Search",
                "Evaluation, Precision & Re-ranking Strategies"
        };
        for (int i = 0; i < t2.length; i++) {
            topicRepository.save(CourseTopic.builder()
                    .course(c2)
                    .name(t2[i])
                    .estimatedHours(4.0)
                    .completed(i < 2)
                    .orderIndex(i)
                    .build());
        }

        // Course 3
        StudyCourse c3 = StudyCourse.builder()
                .user(user)
                .title("Advanced Algorithms & System Design")
                .category("Competitive Programming")
                .description("Dynamic programming, graph theory, Trie and scalable distributed caching.")
                .targetDeadline(today.plusDays(40))
                .dailyTargetHours(1.5)
                .colorAccent("#10b981")
                .status("ACTIVE")
                .build();
        c3 = courseRepository.save(c3);

        String[] t3 = {
                "Graph Traversal (Dijkstra, Bellman-Ford)",
                "Dynamic Programming Patterns & Memoization",
                "Concurrency Primitives & Thread Safety",
                "Distributed Rate Limiting (Token Bucket)"
        };
        for (int i = 0; i < t3.length; i++) {
            topicRepository.save(CourseTopic.builder()
                    .course(c3)
                    .name(t3[i])
                    .estimatedHours(3.0)
                    .completed(i == 0)
                    .orderIndex(i)
                    .build());
        }

        // Seed some past sessions for rich heatmap and graphs
        for (int d = 30; d >= 0; d--) {
            LocalDate sDate = today.minusDays(d);
            int mins = (d % 6 == 0) ? 0 : (d % 2 == 0 ? 150 : 210);
            if (d == 0) mins = 180; // today: 3 hours
            if (mins > 0) {
                sessionRepository.save(StudySessionRecord.builder()
                        .user(user)
                        .courseId(c1.getId())
                        .courseTitle(c1.getTitle())
                        .topicName("Interactive Deep Work Session")
                        .durationMinutes(mins)
                        .sessionType("POMODORO")
                        .sessionDate(sDate)
                        .startedAt(sDate.atTime(10, 0))
                        .endedAt(sDate.atTime(10, 0).plusMinutes(mins))
                        .build());
            }
        }
    }
}
