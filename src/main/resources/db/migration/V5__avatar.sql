-- Profile photo: a storage key like avatars/<userId>/<uuid>.jpg; the timestamp
-- lets clients bust their image cache when the photo changes.
ALTER TABLE profiles
    ADD COLUMN avatar_key TEXT,
    ADD COLUMN avatar_updated_at TIMESTAMPTZ;
