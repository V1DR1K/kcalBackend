ALTER TABLE food
    DROP CONSTRAINT chk_food_cooked_yield_source;

ALTER TABLE food
    ADD CONSTRAINT chk_food_cooked_yield_source
        CHECK (cooked_yield_source IS NULL OR cooked_yield_source IN ('MANUAL', 'IDENTITY', 'GEMINI', 'OPENAI'));
