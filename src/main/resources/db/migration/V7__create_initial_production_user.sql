-- Initial private account. The password is stored only as a BCrypt hash.
INSERT INTO users (
    full_name, email, password_hash, role, gender, activity_level, goal,
    daily_calorie_goal, protein_goal_grams, carbs_goal_grams, fat_goal_grams,
    water_goal_liters, plan_name, nutrition_style, created_at
)
VALUES (
    'Tomás Colombo',
    'tomicolombo20051@gmail.com',
    '$2a$12$/2VUroNjmmqgTkBB4XpN.uo.fMDHPehlPq4uwttJZqNZHV4iRZ4Yy',
    'USER', NULL, 'MODERATELY_ACTIVE', 'MAINTAIN',
    2200, 165, 220, 75, 3.0, 'Personal', 'Balanceado', CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;
