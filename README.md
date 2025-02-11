# SneakyPrototypeKit

A Paper plugin that allows server administrators to create custom items and food with configurable abilities. Perfect for prototyping and testing new item concepts without needing to code new plugins.

## Features

- Create custom items with:
  - Configurable right-click abilities
  - Custom charge system
  - Custom models and textures
  - Custom names and lore
- Create custom food items with:
  - Configurable consumption effects
  - Custom models and textures
  - Custom names and lore

## Usage

1. `/prototype create` - Start the item creation process
2. Follow the interactive UI to:
   - Choose between item or food
   - Select an ability from the config
   - Pick an icon (supports custom model data)
   - Set name and lore
   - Get your custom item!

## Configuration

The plugin uses a configuration file to define:
- Available abilities and their effects
- Available icons for items and food
- Default charge amounts
- Custom model data ranges

## Requirements

- Java 21
- Paper 1.20.4 or newer

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