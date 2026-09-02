-- Preserve historical log references while hiding redundant AI catalog rows.
WITH ranked AS (
    SELECT f.id,
           ROW_NUMBER() OVER (
               PARTITION BY f.search_name, f.category, COALESCE(f.preparation, 'UNSPECIFIED'), COALESCE(f.search_brand, '')
               ORDER BY (
                   SELECT COUNT(*) FROM food_log log WHERE log.food_id = f.id
               ) DESC, f.created_at ASC, f.id ASC
           ) AS position
    FROM food f
    WHERE f.source = 'AI_ESTIMATE' AND f.deleted_at IS NULL
)
UPDATE food f
SET deleted_at = CURRENT_TIMESTAMP
FROM ranked duplicate
WHERE f.id = duplicate.id AND duplicate.position > 1;

CREATE UNIQUE INDEX uq_food_active_ai_identity
    ON food(search_name, category, COALESCE(preparation, 'UNSPECIFIED'), COALESCE(search_brand, ''))
    WHERE deleted_at IS NULL AND source = 'AI_ESTIMATE';

CREATE UNIQUE INDEX uq_food_ai_source_id
    ON food(source_id)
    WHERE source = 'AI_ESTIMATE' AND source_id IS NOT NULL;
