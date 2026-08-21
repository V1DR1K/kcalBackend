ALTER TABLE users ADD COLUMN auth_user_id UUID;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD CONSTRAINT uq_users_auth_user_id UNIQUE (auth_user_id);
