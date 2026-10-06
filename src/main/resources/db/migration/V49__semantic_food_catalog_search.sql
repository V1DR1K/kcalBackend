CREATE INDEX idx_food_semantic_search
    ON food USING gin (
        to_tsvector('simple', search_name || ' ' || search_brand || ' ' || search_tags)
    );
