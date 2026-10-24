CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- Users & goals
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE profiles (
    user_id        BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    sex            TEXT CHECK (sex IN ('male', 'female')),
    birth_date     DATE,
    height_cm      NUMERIC(5, 1),
    activity_level TEXT CHECK (activity_level IN ('sedentary', 'light', 'moderate', 'active', 'very_active')),
    goal_type      TEXT CHECK (goal_type IN ('lose', 'maintain', 'gain')),
    units          TEXT NOT NULL DEFAULT 'metric' CHECK (units IN ('metric', 'imperial'))
);

CREATE TABLE goals (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kcal           INTEGER       NOT NULL,
    protein_g      NUMERIC(6, 1) NOT NULL,
    carbs_g        NUMERIC(6, 1) NOT NULL,
    fat_g          NUMERIC(6, 1) NOT NULL,
    effective_from DATE          NOT NULL,
    UNIQUE (user_id, effective_from)
);

CREATE TABLE weights (
    user_id BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date    DATE          NOT NULL,
    kg      NUMERIC(5, 2) NOT NULL,
    PRIMARY KEY (user_id, date)
);

-- ---------------------------------------------------------------------------
-- Foods (all nutrient values are per 100 g of edible portion).
-- These use sequences instead of identity columns so Hibernate can batch the
-- bulk inserts done by importers. Increment must match allocationSize.
-- ---------------------------------------------------------------------------
CREATE SEQUENCE foods_seq INCREMENT BY 50;
CREATE SEQUENCE food_portions_seq INCREMENT BY 50;

CREATE TABLE foods (
    id             BIGINT PRIMARY KEY DEFAULT nextval('foods_seq'),
    source         TEXT    NOT NULL CHECK (source IN ('usda', 'off', 'custom', 'ai')),
    source_ref     TEXT,                -- fdc_id for USDA, barcode for Open Food Facts
    data_type      TEXT,                -- e.g. 'Foundation', 'SR Legacy', 'Survey (FNDDS)'
    name           TEXT    NOT NULL,
    brand          TEXT,
    category       TEXT,
    kcal_100g      NUMERIC(7, 2),
    protein_100g   NUMERIC(7, 2),
    carbs_100g     NUMERIC(7, 2),
    fat_100g       NUMERIC(7, 2),
    fiber_100g     NUMERIC(7, 2),
    sugar_100g     NUMERIC(7, 2),
    sodium_mg_100g NUMERIC(8, 2),
    energy_basis   TEXT CHECK (energy_basis IN ('reported', 'atwater_specific', 'atwater_general', 'computed')),
    owner_user_id  BIGINT REFERENCES users (id) ON DELETE CASCADE,  -- only for custom foods
    name_tsv       TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', name)) STORED,
    name_embedding VECTOR(1024),                                     -- filled in a later milestone
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source, source_ref)
);

CREATE INDEX foods_name_tsv_idx  ON foods USING GIN (name_tsv);
CREATE INDEX foods_name_trgm_idx ON foods USING GIN (lower(name) gin_trgm_ops);
CREATE INDEX foods_embedding_idx ON foods USING HNSW (name_embedding vector_cosine_ops);
CREATE INDEX foods_owner_idx     ON foods (owner_user_id) WHERE owner_user_id IS NOT NULL;

CREATE TABLE food_portions (
    id       BIGINT PRIMARY KEY DEFAULT nextval('food_portions_seq'),
    food_id  BIGINT        NOT NULL REFERENCES foods (id) ON DELETE CASCADE,
    label    TEXT          NOT NULL,  -- "1 cup", "1 medium", "2 tbsp"
    grams    NUMERIC(8, 2) NOT NULL CHECK (grams > 0),
    position INTEGER       NOT NULL DEFAULT 0
);

CREATE INDEX food_portions_food_idx ON food_portions (food_id);

-- ---------------------------------------------------------------------------
-- Scans
-- ---------------------------------------------------------------------------
CREATE TABLE scans (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    image_key   TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'processing' CHECK (status IN ('processing', 'ready', 'failed')),
    model       TEXT,
    latency_ms  INTEGER,
    raw_ai_json JSONB,
    error       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX scans_user_idx ON scans (user_id, created_at DESC);

CREATE TABLE scan_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scan_id         BIGINT        NOT NULL REFERENCES scans (id) ON DELETE CASCADE,
    ai_name         TEXT          NOT NULL,
    ai_query        TEXT,
    est_grams       NUMERIC(7, 1) NOT NULL,
    grams_low       NUMERIC(7, 1),
    grams_high      NUMERIC(7, 1),
    confidence      NUMERIC(3, 2),
    matched_food_id BIGINT REFERENCES foods (id),
    match_score     NUMERIC(5, 4),
    source          TEXT          NOT NULL CHECK (source IN ('db', 'ai')),
    ai_kcal_100g    NUMERIC(7, 2),
    flags           TEXT[]        NOT NULL DEFAULT '{}'
);

CREATE INDEX scan_items_scan_idx ON scan_items (scan_id);

CREATE TABLE scan_corrections (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scan_item_id BIGINT      NOT NULL REFERENCES scan_items (id) ON DELETE CASCADE,
    field        TEXT        NOT NULL,
    old_value    TEXT,
    new_value    TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Diary. Macros are snapshotted at log time so later food edits don't
-- rewrite history.
-- ---------------------------------------------------------------------------
CREATE TABLE diary_entries (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date         DATE          NOT NULL,
    meal_type    TEXT          NOT NULL CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack')),
    food_id      BIGINT        NOT NULL REFERENCES foods (id),
    grams        NUMERIC(7, 1) NOT NULL CHECK (grams > 0),
    kcal         NUMERIC(7, 1) NOT NULL,
    protein_g    NUMERIC(6, 1) NOT NULL,
    carbs_g      NUMERIC(6, 1) NOT NULL,
    fat_g        NUMERIC(6, 1) NOT NULL,
    scan_item_id BIGINT REFERENCES scan_items (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX diary_entries_user_date_idx ON diary_entries (user_id, date);
