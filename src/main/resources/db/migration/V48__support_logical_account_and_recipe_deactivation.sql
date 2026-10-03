ALTER TABLE users
    ADD COLUMN deleted_at timestamptz;

ALTER TABLE recipe
    ADD COLUMN deleted_at timestamptz;

CREATE INDEX idx_recipe_active_search_name
    ON recipe (search_name, id)
    WHERE deleted_at IS NULL;
