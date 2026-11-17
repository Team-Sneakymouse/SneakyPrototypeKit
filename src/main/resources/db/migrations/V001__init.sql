-- SneakyPrototypeKit finalized-item logs in schema lom (operator provisions schema lom; no CREATE SCHEMA here).
-- Matches docs/database.md insert contract.

CREATE TABLE lom.lom2_prototype_kit_items (
    id TEXT PRIMARY KEY,
    type TEXT,
    ability TEXT,
    creator TEXT,
    name TEXT,
    material TEXT,
    model_data NUMERIC,
    item_model TEXT NOT NULL DEFAULT '',
    lore TEXT,
    created TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated TIMESTAMPTZ NOT NULL DEFAULT now()
);