CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE food_embedding (
    food_id BIGINT PRIMARY KEY REFERENCES food(id) ON DELETE CASCADE,
    embedding VECTOR(768) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    source_text TEXT NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_food_embedding_hnsw
    ON food_embedding USING hnsw (embedding vector_cosine_ops);

CREATE OR REPLACE FUNCTION invalidate_food_embedding_on_food_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.name IS DISTINCT FROM NEW.name
       OR OLD.brand IS DISTINCT FROM NEW.brand
       OR OLD.category IS DISTINCT FROM NEW.category
       OR OLD.preparation IS DISTINCT FROM NEW.preparation
       OR OLD.search_name IS DISTINCT FROM NEW.search_name
       OR OLD.search_brand IS DISTINCT FROM NEW.search_brand
       OR OLD.search_tags IS DISTINCT FROM NEW.search_tags
       OR OLD.moderation_status IS DISTINCT FROM NEW.moderation_status
       OR OLD.deleted_at IS DISTINCT FROM NEW.deleted_at THEN
        DELETE FROM food_embedding WHERE food_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_food_embedding_invalidation
AFTER UPDATE ON food
FOR EACH ROW
EXECUTE FUNCTION invalidate_food_embedding_on_food_change();
