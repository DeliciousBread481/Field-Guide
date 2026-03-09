# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.1] - 2026-03-08

### Fixed

- Fixed the 1.20.1 versions being slightly out of date.

## [1.2.0] - 2026-03-08

### Added

- Added Exposure integration.
  - Entities/blocks in photos will automatically be unlocked in the Field Guide (configurable).
  - Photographs can be added to any entry displays in place of their regular renders.
- Added Reliable Remover integration.
  - Blacklisted items will now be removed from entries and loot displays.
- Added default configurations for the following mods' plants:
  - Farmer's Delight
  - Atmospheric
  - Autumnity
  - Supplementaries
  - Windswept
- Added config options for changing silhouette colors.
- Added support for biome tags in biome modifiers.
- Added Simplified and Traditional Chinese localizations (@balitube).

### Changed

- Increased widget render size.
- Removed default fish texture overrides.
- The display for days in the Field Guide now starts from Day 1.
- Moved more text to translation keys.

### Fixed

- Fixed mob rendering with Mixed Litter (No Man's Land).
- Fixed spawn biomes not showing on dedicated servers.
- Fixed crash with Shades, Monster Booklet, and possibly other mods.
- Fixed issue where pressing B would sometimes erroneously open the guide.
- Improved render scaling.

## [1.1.3] - 2026-03-02

### Fixed

- Fixed crash with Lithostitched.

## [1.1.2] - 2026-03-01

### Fixed

- Improve networking performance for 1.21.1.
- Fixed crashes with scanning multipart entities.

## [1.1.1] - 2026-03-01

### Fixed

- Fixed bosses tag not populating properly.
- Fixed possible networking-related NPE.

## [1.1.0] - 2026-03-01

### Added

- Added tag for Spyglass items.
- `tag` strategies now support tags for blocks.

### Fixed

- Fixed issues with category syncing with Lithium.
- Fixed resource pack reloading failing in-game.

## [1.0.3] - 2026-03-01

### Fixed

- Fixed huge mushrooms not showing up in the Field Guide.

## [1.0.2] - 2026-03-01

### Fixed

- Fixed category syncing in heavily modded environments.

## [1.0.1] - 2026-03-01

### Fixed

- Fixed too large payloads in heavily modded environments.

## [1.0.0] - 2026-03-01

- Initial release.