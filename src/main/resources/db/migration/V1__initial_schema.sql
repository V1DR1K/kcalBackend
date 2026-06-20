CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    gender VARCHAR(255),
    activity_level VARCHAR(255),
    goal VARCHAR(255),
    weight_kg NUMERIC(38, 2),
    height_cm NUMERIC(38, 2),
    target_weight_kg NUMERIC(38, 2),
    birth_date DATE,
    daily_calorie_goal INTEGER,
    protein_goal_grams INTEGER,
    carbs_goal_grams INTEGER,
    fat_goal_grams INTEGER,
    water_goal_liters NUMERIC(38, 2),
    plan_name VARCHAR(255),
    nutrition_style VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE food (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    barcode VARCHAR(255) UNIQUE,
    category VARCHAR(255) NOT NULL,
    base_unit VARCHAR(255) NOT NULL,
    base_quantity NUMERIC(38, 2),
    calories INTEGER,
    protein_grams NUMERIC(38, 2),
    carbs_grams NUMERIC(38, 2),
    fat_grams NUMERIC(38, 2),
    image_url VARCHAR(500),
    image_object_key VARCHAR(500),
    source VARCHAR(255),
    source_id VARCHAR(255),
    last_synced_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE food_tags (
    food_id BIGINT NOT NULL REFERENCES food(id) ON DELETE CASCADE,
    tag VARCHAR(255)
);

CREATE TABLE nutrition_plan (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    daily_calories INTEGER NOT NULL,
    protein_percent NUMERIC(38, 2) NOT NULL,
    carbs_percent NUMERIC(38, 2) NOT NULL,
    fat_percent NUMERIC(38, 2) NOT NULL,
    protein_goal_grams INTEGER NOT NULL,
    carbs_goal_grams INTEGER NOT NULL,
    fat_goal_grams INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_nutrition_plan_user_start ON nutrition_plan(user_id, start_date);
CREATE INDEX idx_nutrition_plan_user_end ON nutrition_plan(user_id, end_date);

CREATE TABLE recipe (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    total_weight_grams NUMERIC(38, 2) NOT NULL,
    calories INTEGER,
    protein_grams NUMERIC(38, 2),
    carbs_grams NUMERIC(38, 2),
    fat_grams NUMERIC(38, 2),
    created_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE recipe_ingredient (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    food_id BIGINT NOT NULL REFERENCES food(id),
    unit VARCHAR(255) NOT NULL,
    quantity NUMERIC(38, 2) NOT NULL
);

CREATE TABLE food_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    food_id BIGINT REFERENCES food(id),
    recipe_id BIGINT REFERENCES recipe(id),
    item_type VARCHAR(255),
    meal_type VARCHAR(255),
    unit VARCHAR(255),
    log_date DATE,
    quantity NUMERIC(38, 2),
    calories INTEGER,
    protein_grams NUMERIC(38, 2),
    carbs_grams NUMERIC(38, 2),
    fat_grams NUMERIC(38, 2),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE water_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    log_date DATE,
    liters NUMERIC(38, 2),
    created_at TIMESTAMP WITH TIME ZONE
);
