-- Richer goals: five goal types, target weight and pace, and a calculated/custom
-- mode so custom targets aren't overwritten when the profile changes.

ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_goal_type_check;
UPDATE profiles SET goal_type = 'lose_fat' WHERE goal_type = 'lose';
UPDATE profiles SET goal_type = 'lean_gain' WHERE goal_type = 'gain';
ALTER TABLE profiles
    ADD CONSTRAINT profiles_goal_type_check
        CHECK (goal_type IN ('lose_fat', 'maintain', 'lean_gain', 'bulk', 'recomp')),
    ADD COLUMN target_weight_kg NUMERIC(5, 2),
    ADD COLUMN weekly_rate_kg   NUMERIC(3, 2),
    ADD COLUMN goal_mode        TEXT NOT NULL DEFAULT 'calculated'
        CHECK (goal_mode IN ('calculated', 'custom'));

ALTER TABLE goals
    ADD COLUMN source TEXT NOT NULL DEFAULT 'calculated' CHECK (source IN ('calculated', 'custom'));
