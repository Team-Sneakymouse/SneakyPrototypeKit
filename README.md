# SneakyPrototypeKit

A Paper plugin that allows server administrators to create custom items and consumables with configurable abilities. Perfect for prototyping and testing new item concepts without needing to code new plugins.

## Features

- Create custom items with:
  - Configurable left-click abilities
  - Custom charge system
  - Custom models and textures
  - Custom names and lore
- Create custom food and drinks with:
  - Configurable consumption effects
  - Custom models and textures
  - Custom names and lore
  - Stackable charges (e.g. food that can be eaten multiple times)
- Integration with PostgreSQL through SneakyDB for logging created items

## Dependencies

- **Required**:
  - Java 25
  - Paper 26.2
  - SneakyDB `2026.11.17-452c61d0f480` or compatible newer (for item logging and migrations)
  - SneakyUtils

## Usage

1. `/prototypekit` - Start the item creation process
2. `/prototypekitreload` - Reload the plugin configuration
3. Follow the interactive UI to:
   - Choose between item, food, or drink
   - Select an ability from the config
   - Pick an icon (supports custom model data)
   - Set name and lore
   - Get your custom item!

`/sneakyprototypekit cast <player> <ability>` runs an ability's native `actions` as the player (no charges or
cooldown; `command-console`-only abilities are refused). Java plugins can call
`net.sneakyprototypekit.api.PrototypeAbilities.has(id)` / `cast(player, id)` (main thread) instead.

Name and lore are edited in prefilled dialogs with Save and Cancel. Names allow 30 characters,
and lore allows 100 total characters, including formatting codes and line breaks.
Press Enter in the lore field to add a line break. Player lore preserves these breaks and blank
lines, and wraps long lines at 30 visible characters, splitting long words if needed.
Player lore is limited to eight rendered lines, including blank lines and automatic wrapping.
Ability descriptions and the charges line are separate from this limit.
The field is approximately 30 characters wide in the default
font; wider glyphs and resource-pack fonts can change how much text fits.

## Configuration

The plugin uses a configuration file to define:

### Abilities
```yaml
abilities:
    heal:
        name: "Healing Touch"
        description: "Heals the user"
        icon-material: "jigsaw"  # Material for the ability's UI icon
        icon-item-model: "lom:icons/grayscale/1111"  # item-model id (no integer custom model data)
        command-console: "effect give [playerName] instant_health 1 1"
        charges: 10  # How many uses per item
        stack-size: 5  # How many items can stack together
        allowed-types: ["FOOD", "DRINK"]  # What item types can use this ability
```

### Icons
Define available icons for each item type:
```yaml
item-icons:
    echo_shard:                     # carrier material (kept: it matters for food and drink behaviour)
        - "lom:items/prototype-items/cloak"
        - "lom:tools/powerdrill"

food-icons:
    potato:
        - "lom:items/prototype-food/07_bread"

drink-icons:
    melon_slice:
        - "lom:items/prototype-drinks/wine"
```

The prototype kit looks like `prototype-kit.item-model`. Every look is an `item_model` id; the plugin sets no
integer custom model data. The old integer keys (`icon-custom-model-data`, `model-data`, and `"1-5"` style icon
entries) still load: they are translated through the bundled `legacy-item-models.json` (regenerate it with
`tools/generate_legacy_item_models.py`), and integers the pack never had a model for are dropped.

Kits and finalized items made before the change keep working. A half-built kit's stored icon
(`MATERIAL,<integer>`) is converted to `MATERIAL,<item model>` the next time it is read, and old finalized
copies are still recognised through SneakyItems legacy patterns built from the same table.

## Permissions

- `sneakyprototypekit.command.prototypekit` - Access to create items
- `sneakyprototypekit.command.reload` - Access to reload the plugin configuration
- `sneakyprototypekit.command.cast` - `/sneakyprototypekit cast` (default: op)
- `sneakyprototypekit.admin` - Access to use color codes in names/lore

## Installation

1. Install Java 25 on your server
2. Install Paper 26.2
3. Install and configure SneakyDB and SneakyUtils; ensure schema `lom` exists (see [database contract](docs/database.md) and [migration spec](docs/specs/sneakydb-migrations.md))
4. Place SneakyPrototypeKit.jar in your plugins folder
5. Start the server
6. Configure the plugin in `plugins/SneakyPrototypeKit/config.yml`

The plugin applies its own table DDL through SneakyDB at startup; it does not import historical
PocketBase rows ([migrateSQL](https://github.com/Team-Sneakymouse/migrateSQL) remains the operator path for that).
Item creation remains available if best-effort database logging fails; failures are logged
to the server console, with no automatic retries or PocketBase fallback.

## Diagnostics

Measured by [SneakyDiagnostics](https://github.com/Team-Sneakymouse/SneakyDiagnostics) when it is installed (optional): `/diagnostics plugins start 60 SneakyPrototypeKit`.
Setup and rules: [plugin-providers.md](https://github.com/Team-Sneakymouse/SneakyDiagnostics/blob/main/docs/plugin-providers.md).
Sections, per menu (`main-creation`, `type-selection`, `ability-selection`, `icon-selection`): `menu.<menu>.open` (the whole open), with `menu.<menu>.build` (creating and filling the inventory) and `menu.<menu>.open-inventory` (`Player.openInventory`, which includes every plugin's inventory listeners, such as SneakyItems' inventory update). No counts or levels.

## Development

This plugin is built using Gradle. To build the plugin:

```bash
./gradlew build
```

To run a test server:

```bash
./gradlew runServer
```

Optional PostgreSQL migration test (throwaway database; creates schema `lom` and applies jar migrations):

```bash
export SNEAKYPROTOTYPEKIT_TEST_PG_URL='jdbc:postgresql://host:5432/db'
./gradlew postgresTest
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
