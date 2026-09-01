CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE food ADD COLUMN search_name TEXT;
ALTER TABLE food ADD COLUMN search_brand TEXT;
ALTER TABLE food ADD COLUMN search_tags TEXT;
ALTER TABLE recipe ADD COLUMN search_name TEXT;

UPDATE food
SET search_name = regexp_replace(trim(unaccent(lower(coalesce(name, '')))), '\s+', ' ', 'g'),
    search_brand = regexp_replace(trim(unaccent(lower(coalesce(brand, '')))), '\s+', ' ', 'g'),
    search_tags = coalesce((
        SELECT string_agg(regexp_replace(trim(unaccent(lower(tag))), '\s+', ' ', 'g'), ' ')
        FROM food_tags
        WHERE food_id = food.id
    ), '');

UPDATE recipe
SET search_name = regexp_replace(trim(unaccent(lower(coalesce(name, '')))), '\s+', ' ', 'g');

ALTER TABLE food ALTER COLUMN search_name SET DEFAULT '';
ALTER TABLE food ALTER COLUMN search_brand SET DEFAULT '';
ALTER TABLE food ALTER COLUMN search_tags SET DEFAULT '';
ALTER TABLE recipe ALTER COLUMN search_name SET DEFAULT '';

ALTER TABLE food ALTER COLUMN search_name SET NOT NULL;
ALTER TABLE food ALTER COLUMN search_brand SET NOT NULL;
ALTER TABLE food ALTER COLUMN search_tags SET NOT NULL;
ALTER TABLE recipe ALTER COLUMN search_name SET NOT NULL;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_food_search_name_trgm
    ON food USING gin (search_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_food_search_brand_trgm
    ON food USING gin (search_brand gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_food_search_tags_trgm
    ON food USING gin (search_tags gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_recipe_search_name_trgm
    ON recipe USING gin (search_name gin_trgm_ops);
