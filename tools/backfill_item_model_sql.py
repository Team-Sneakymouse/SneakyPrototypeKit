#!/usr/bin/env python3
"""Print the one-off SQL that backfills `lom.lom2_prototype_kit_items.item_model` for rows logged with integer model data.

Usage: python3 tools/backfill_item_model_sql.py [carrier ...] > backfill.sql

Run with no arguments: every carrier in src/main/resources/legacy-item-models.json is included. (Passing
lowercase material names restricts the output to those carriers and leaves other rows unfilled; only for
debugging.) The SQL is idempotent; re-run it after cutover/rollback. The plugin never runs this: schema
changes and data transfer belong to the migration owner (see docs/database.md).
"""
import json
import sys
from pathlib import Path

table = json.loads((Path(__file__).resolve().parent.parent / "src/main/resources/legacy-item-models.json").read_text(encoding="utf-8"))
carriers = sys.argv[1:] or sorted(table)
unknown = [c for c in carriers if c not in table]
if unknown:
    sys.exit(f"no legacy mapping for carrier(s): {', '.join(unknown)}")

rows = [
    f"    ('{carrier.upper()}', {cmd}, '{model}')"
    for carrier in carriers
    for cmd, model in table[carrier].items()
]
print("BEGIN;")
print("ALTER TABLE lom.lom2_prototype_kit_items ADD COLUMN IF NOT EXISTS item_model text NOT NULL DEFAULT '';")
print("UPDATE lom.lom2_prototype_kit_items AS t")
print("SET item_model = m.item_model")
print("FROM (VALUES")
print(",\n".join(rows))
print(") AS m(material, model_data, item_model)")
print("WHERE t.item_model = '' AND t.material = m.material AND t.model_data = m.model_data;")
print("COMMIT;")
