ALTER TABLE food
    ADD COLUMN nutrition_fingerprint varchar(32) NOT NULL DEFAULT '';

DROP INDEX uq_food_active_ai_identity;

CREATE UNIQUE INDEX uq_food_active_ai_identity
    ON food(search_name, category, COALESCE(preparation, 'UNSPECIFIED'), COALESCE(search_brand, ''),
            COALESCE(protein_grams, 0), COALESCE(carbs_grams, 0), COALESCE(fat_grams, 0), nutrition_fingerprint)
    WHERE deleted_at IS NULL AND source = 'AI_ESTIMATE';
