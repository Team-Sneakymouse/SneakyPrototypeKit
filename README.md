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
- Integration with PocketBase for logging created items

## Dependencies

- **Required**:
  - Java 21
  - Paper 1.20.4 or newer
  - SneakyPocketbase (for item logging)

## Usage

1. `/prototypekit` - Start the item creation process
2. `/prototypekitreload` - Reload the plugin configuration
3. Follow the interactive UI to:
   - Choose between item, food, or drink
   - Select an ability from the config
   - Pick an icon (supports custom model data)
   - Set name and lore
   - Get your custom item!

## Configuration

The plugin uses a configuration file to define:

### Abilities
```yaml
abilities:
    heal:
        name: "Healing Touch"
        description: "Heals the user"
        icon-material: "jigsaw"  # Material for the ability's UI icon
        icon-custom-model-data: 1
        command-console: "effect give [playerName] instant_health 1 1"
        charges: 10  # How many uses per item
        stack-size: 5  # How many items can stack together
        allowed-types: ["FOOD", "DRINK"]  # What item types can use this ability
```

### Icons
Define available icons for each item type:
```yaml
item-icons:
    diamond_sword:
        - "1-5"  # Range of model data
        - "7"    # Single model data value

food-icons:
    apple:
        - "1-3"
    golden_apple:
        - "1"

drink-icons:
    potion:
        - "1-3"
    honey_bottle:
        - "1-2"
```

## Permissions

- `sneakyprototypekit.command.prototypekit` - Access to create items
- `sneakyprototypekit.command.reload` - Access to reload the plugin configuration
- `sneakyprototypekit.admin` - Access to use color codes in names/lore

## Installation

1. Install Java 21 on your server
2. Install Paper 1.20.4 or newer
3. Install SneakyPocketbase plugin
4. Place SneakyPrototypeKit.jar in your plugins folder
5. Start the server
6. Configure the plugin in `plugins/SneakyPrototypeKit/config.yml`

## Development

This plugin is built using Gradle. To build the plugin:

```bash
./gradlew build
```

To run a test server:

```bash
./gradlew runServer
```

## License

This project is licensed under the MIT License - see the LICENSE file for details. 