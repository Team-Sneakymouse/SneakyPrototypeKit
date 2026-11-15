#!/usr/bin/env python3
"""Regenerate src/main/resources/legacy-item-models.json from craftengine-resources.

Usage: python3 tools/generate_legacy_item_models.py <path to craftengine-resources/resources/base/legacy-item-models.json>

Output is {"<carrier>": {"<integer custom model data>": "<namespace:item-model id>"}} with the `minecraft:`
prefix dropped from carriers. The plugin only reads it to migrate old integer icons (PDC `icon_data`, old
configs, old finalized items); it never writes integers.
"""
import json
import sys
from pathlib import Path

source = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
out = {}
for route in source["routes"]:
    if route["residual_custom_model_data"]:
        continue  # needs extra components, not a plain integer -> model mapping
    carrier = route["carrier"].removeprefix("minecraft:")
    out.setdefault(carrier, {})[str(route["custom_model_data"])] = route["item_model"]
target = Path(__file__).resolve().parent.parent / "src/main/resources/legacy-item-models.json"
ordered = {c: dict(sorted(v.items(), key=lambda kv: int(kv[0]))) for c, v in sorted(out.items())}
target.write_text(json.dumps(ordered, separators=(",", ":"), sort_keys=False) + "\n", encoding="utf-8")
print(f"{sum(len(v) for v in ordered.values())} mappings, {len(ordered)} carriers -> {target}")
