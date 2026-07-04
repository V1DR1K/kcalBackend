CREATE INDEX IF NOT EXISTS idx_food_lower_name_id ON food(LOWER(name), id);
CREATE INDEX IF NOT EXISTS idx_food_category_lower_name_id ON food(category, LOWER(name), id);
CREATE INDEX IF NOT EXISTS idx_recipe_lower_name_id ON recipe(LOWER(name), id);
