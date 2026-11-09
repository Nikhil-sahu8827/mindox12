-- Seed Growth and Study data for both user 2 and user 7
BEGIN;

-- 1. Insert Frontend Recent Activities into study_session_records for users 2 and 7
INSERT INTO study_session_records (
    created_at, updated_at, course_id, course_title, duration_minutes,
    ended_at, notes, session_date, session_type, started_at, topic_name, user_id
)
SELECT 
    NOW(), NOW(), 8, 'JAVA', 68,
    NOW(), 'Completed Exception Handling deep focus session', CURRENT_DATE, 'POMODORO', NOW() - INTERVAL '68 minutes', 'Java Exception Handling', u.id
FROM users u WHERE u.id IN (2, 7)
AND NOT EXISTS (
    SELECT 1 FROM study_session_records s 
    WHERE s.user_id = u.id AND s.topic_name = 'Java Exception Handling' AND s.session_date = CURRENT_DATE
);

INSERT INTO study_session_records (
    created_at, updated_at, course_id, course_title, duration_minutes,
    ended_at, notes, session_date, session_type, started_at, topic_name, user_id
)
SELECT 
    NOW(), NOW(), 8, 'JAVA', 44,
    (CURRENT_DATE - INTERVAL '1 day') + TIME '15:44', 'Generics, Wildcards, and Type Erasure', CURRENT_DATE - 1, 'STOPWATCH', (CURRENT_DATE - INTERVAL '1 day') + TIME '15:00', 'Java Generics Deep Dive', u.id
FROM users u WHERE u.id IN (2, 7)
AND NOT EXISTS (
    SELECT 1 FROM study_session_records s 
    WHERE s.user_id = u.id AND s.topic_name = 'Java Generics Deep Dive' AND s.session_date = CURRENT_DATE - 1
);

INSERT INTO study_session_records (
    created_at, updated_at, course_id, course_title, duration_minutes,
    ended_at, notes, session_date, session_type, started_at, topic_name, user_id
)
SELECT 
    NOW(), NOW(), 8, 'JAVA', 58,
    (CURRENT_DATE - INTERVAL '2 days') + TIME '11:58', 'Primitive & Reference Types Review', CURRENT_DATE - 2, 'POMODORO', (CURRENT_DATE - INTERVAL '2 days') + TIME '11:00', 'Data Types in Java', u.id
FROM users u WHERE u.id IN (2, 7)
AND NOT EXISTS (
    SELECT 1 FROM study_session_records s 
    WHERE s.user_id = u.id AND s.topic_name = 'Data Types in Java' AND s.session_date = CURRENT_DATE - 2
);

INSERT INTO study_session_records (
    created_at, updated_at, course_id, course_title, duration_minutes,
    ended_at, notes, session_date, session_type, started_at, topic_name, user_id
)
SELECT 
    NOW(), NOW(), 1, 'Cloud & Distributed Systems', 85,
    (CURRENT_DATE - INTERVAL '3 days') + TIME '17:25', 'Architecture, Storage, & Deployment Models', CURRENT_DATE - 3, 'STOPWATCH', (CURRENT_DATE - INTERVAL '3 days') + TIME '16:00', 'Cloud Computing Basics', u.id
FROM users u WHERE u.id IN (2, 7)
AND NOT EXISTS (
    SELECT 1 FROM study_session_records s 
    WHERE s.user_id = u.id AND s.topic_name = 'Cloud Computing Basics' AND s.session_date = CURRENT_DATE - 3
);

-- 2. Update user_study_stats for user 2 and 7 to match frontend metrics
UPDATE user_study_stats
SET 
    daily_target_hours = 2.0,
    current_streak = 5,
    longest_streak = 14,
    last_study_date = CURRENT_DATE,
    updated_at = NOW()
WHERE user_id IN (2, 7);

-- 3. Copy user 7's courses to user 2 if user 2 is missing them
DO $$
DECLARE
    r RECORD;
    new_course_id BIGINT;
    top RECORD;
BEGIN
    FOR r IN SELECT * FROM study_courses WHERE user_id = 7 LOOP
        IF NOT EXISTS (SELECT 1 FROM study_courses WHERE user_id = 2 AND title = r.title) THEN
            INSERT INTO study_courses (user_id, title, category, description, target_deadline, daily_target_hours, color_accent, status, created_at, updated_at)
            VALUES (2, r.title, r.category, r.description, r.target_deadline, r.daily_target_hours, r.color_accent, r.status, NOW(), NOW())
            RETURNING id INTO new_course_id;

            FOR top IN SELECT * FROM course_topics WHERE course_id = r.id LOOP
                INSERT INTO course_topics (course_id, name, estimated_hours, completed, order_index, created_at, updated_at)
                VALUES (new_course_id, top.name, top.estimated_hours, top.completed, top.order_index, NOW(), NOW());
            END LOOP;
        END IF;
    END LOOP;

    -- Also copy user 2's courses to user 7 if user 7 is missing them
    FOR r IN SELECT * FROM study_courses WHERE user_id = 2 LOOP
        IF NOT EXISTS (SELECT 1 FROM study_courses WHERE user_id = 7 AND title = r.title) THEN
            INSERT INTO study_courses (user_id, title, category, description, target_deadline, daily_target_hours, color_accent, status, created_at, updated_at)
            VALUES (7, r.title, r.category, r.description, r.target_deadline, r.daily_target_hours, r.color_accent, r.status, NOW(), NOW())
            RETURNING id INTO new_course_id;

            FOR top IN SELECT * FROM course_topics WHERE course_id = r.id LOOP
                INSERT INTO course_topics (course_id, name, estimated_hours, completed, order_index, created_at, updated_at)
                VALUES (new_course_id, top.name, top.estimated_hours, top.completed, top.order_index, NOW(), NOW());
            END LOOP;
        END IF;
    END LOOP;
END $$;

-- 4. Insert Question Attempts from existing quiz questions for users 2 and 7
DO $$
DECLARE
    q RECORD;
    u_id BIGINT;
BEGIN
    FOR u_id IN SELECT 2 UNION SELECT 7 LOOP
        FOR q IN SELECT qq.id, qq.quiz_id, qq.topic, qq.correct_answer, qz.document_id, dm.filename 
                 FROM quiz_questions qq 
                 JOIN quizzes qz ON qq.quiz_id = qz.id 
                 LEFT JOIN document_metadata dm ON qz.document_id = dm.id
                 LIMIT 25 LOOP
            IF NOT EXISTS (SELECT 1 FROM question_attempts WHERE user_id = u_id AND question_id = q.id) THEN
                INSERT INTO question_attempts (
                    created_at, document_id, is_correct, question_id, quiz_id, selected_option, source_file_name, topic_name, user_id
                ) VALUES (
                    NOW() - (random() * interval '3 days'),
                    q.document_id,
                    (random() > 0.15), -- 85% accuracy
                    q.id,
                    q.quiz_id,
                    q.correct_answer,
                    COALESCE(q.filename, 'Java Core Reference'),
                    COALESCE(NULLIF(q.topic, ''), 'Java Fundamentals'),
                    u_id
                );
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- 5. Insert Topic Progress Snapshots
INSERT INTO topic_progress_snapshots (
    user_id, topic_name, document_id, source_file_name, strength_score, accuracy, questions_attempted, questions_correct, snapshot_date, created_at
)
SELECT 
    qa.user_id,
    qa.topic_name,
    qa.document_id,
    qa.source_file_name,
    ROUND((COUNT(CASE WHEN qa.is_correct THEN 1 END)::numeric / COUNT(*)::numeric) * 100, 1) AS strength_score,
    ROUND((COUNT(CASE WHEN qa.is_correct THEN 1 END)::numeric / COUNT(*)::numeric) * 100, 1) AS accuracy,
    COUNT(*)::integer AS questions_attempted,
    COUNT(CASE WHEN qa.is_correct THEN 1 END)::integer AS questions_correct,
    CURRENT_DATE,
    NOW()
FROM question_attempts qa
WHERE NOT EXISTS (
    SELECT 1 FROM topic_progress_snapshots tps 
    WHERE tps.user_id = qa.user_id AND tps.topic_name = qa.topic_name AND tps.snapshot_date = CURRENT_DATE
)
GROUP BY qa.user_id, qa.topic_name, qa.document_id, qa.source_file_name;

COMMIT;

