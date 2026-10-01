-- Preserve every historical value and add explicit coverage for new snapshots.
ALTER TABLE food_log_nutrient ADD COLUMN known_value NUMERIC(38, 2);
ALTER TABLE food_log_nutrient ADD COLUMN complete BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE food_log_nutrient SET known_value = nutrient_value, complete = nutrient_value IS NOT NULL;
