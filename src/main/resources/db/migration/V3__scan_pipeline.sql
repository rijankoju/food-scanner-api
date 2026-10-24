ALTER TABLE scans
    ADD COLUMN meal_name     TEXT,
    ADD COLUMN notes         TEXT,
    ADD COLUMN input_tokens  INTEGER,
    ADD COLUMN output_tokens INTEGER,
    ADD COLUMN completed_at  TIMESTAMPTZ;

-- est_grams / ai_* keep what the model said; grams and matched_food_id hold
-- the current (possibly user-corrected) values. Corrections are logged in
-- scan_corrections for prompt tuning and future model training.
ALTER TABLE scan_items
    ADD COLUMN position        INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN cooking_method  TEXT,
    ADD COLUMN hidden_addition BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN grams           NUMERIC(7, 1) NOT NULL,
    ADD COLUMN removed         BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN ai_protein_100g NUMERIC(7, 2),
    ADD COLUMN ai_carbs_100g   NUMERIC(7, 2),
    ADD COLUMN ai_fat_100g     NUMERIC(7, 2);
