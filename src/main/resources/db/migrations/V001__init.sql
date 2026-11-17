-- SneakyPrototypeKit schema v1: Live baseline for lom.lom2_prototype_kit_items (2026-11-17).
-- Compared with team_sneakymouse (Live) via pg_dump / information_schema on that date.
-- Operator provisions schema `lom`. Scripts never CREATE SCHEMA.
-- Shared lom.touch_updated() already exists on Live; CREATE OR REPLACE keeps empty-DB tests working.

CREATE OR REPLACE FUNCTION lom.touch_updated() RETURNS trigger
LANGUAGE plpgsql
SET search_path TO pg_catalog
AS $$
BEGIN
 NEW.updated = clock_timestamp();
 RETURN NEW;
END
$$;

CREATE TABLE lom.lom2_prototype_kit_items (
 id text PRIMARY KEY,
 type text,
 ability text,
 creator text,
 name text,
 material text,
 model_data bigint DEFAULT 0,
 lore text,
 created timestamptz NOT NULL DEFAULT now(),
 updated timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX lom2_prototype_kit_items_created_idx ON lom.lom2_prototype_kit_items (created DESC);
CREATE INDEX lom2_prototype_kit_items_creator_idx ON lom.lom2_prototype_kit_items (creator);

CREATE TRIGGER lom2_prototype_kit_items_touch_updated
 BEFORE UPDATE ON lom.lom2_prototype_kit_items
 FOR EACH ROW EXECUTE FUNCTION lom.touch_updated();
