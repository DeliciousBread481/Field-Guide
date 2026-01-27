# Field Guide

**Field Guide** is an exploration-focused mod that acts as an in-game encyclopedia! It encourages players to observe the
world around them using a **Spyglass**. By looking at entities and specific blocks, you can scan them, unlocking entries
in your Field Guide.

## Gameplay & Usage

Using a standard Minecraft **Spyglass**, zoom in and look directly at a mob or specific blocks (like flora). A scanning
reticle will appear over the target, and after a set duration (default 1 second), a sound will play and the entry will
be unlocked.

**To open the guide:**

* Press **'B'** (default).
* Click the book icon in the **Inventory Screen** or **Pause Menu**.

---

## Configuration

The configuration file is located at `.minecraft/config/fieldguide.json`.

| Option                   | Type    | Default     | Description                                              |
|--------------------------|---------|-------------|----------------------------------------------------------|
| `showPauseMenuButton`    | Boolean | `true`      | Adds a Field Guide button to the pause menu.             |
| `pauseButtonXOffset`     | Integer | `0`         | Adjusts X position of the pause menu button.             |
| `pauseButtonYOffset`     | Integer | `0`         | Adjusts Y position of the pause menu button.             |
| `showInventoryButton`    | Boolean | `true`      | Adds a Field Guide button to the player inventory.       |
| `inventoryButtonXOffset` | Integer | `125`       | Adjusts X position of the inventory button.              |
| `inventoryButtonYOffset` | Integer | `62`        | Adjusts Y position of the inventory button.              |
| `showUndiscoveredNames`  | Boolean | `false`     | If true, shows names of locked entries instead of "???". |
| `scanSpeed`              | Double  | `1.0`       | Time in seconds required to scan a target.               |
| `entityBlacklist`        | List    | *See below* | List of IDs that cannot be scanned.                      |

---

## Commands

Admin commands are available for debugging or map-making purposes. Requires OP.

### Granting Entries

Unlock entries for a player without scanning.

* **Unlock Everything:** `/fieldguide grant <player> everything`
* **Unlock Specific Category:** `/fieldguide grant <player> category <namespace:category_id>`
* **Unlock Specific Entry:** `/fieldguide grant <player> only <namespace:entity_or_block_id>`

### Revoking Entries

Lock entries again (reset progress).

* **Revoke Everything:** `/fieldguide revoke <player> everything`
* **Revoke Specific Category:** `/fieldguide revoke <player> category <namespace:category_id>`
* **Revoke Specific Entry:** `/fieldguide revoke <player> only <namespace:entity_or_block_id>`

---

## Datapacks (Customizing Content)

You can add new categories, reorganize existing ones, or add modded entities to the Field Guide using standard Minecraft
**Datapacks**.

### Defining Categories

Category files are JSON files located at:
`data/<namespace>/fieldguide/categories/<filename>.json`

The filename becomes the ID of the category (e.g., `wetlands.json` becomes `<namespace>:wetlands`).

#### JSON Structure

| Field        | Type    | Description                                                                          |
|--------------|---------|--------------------------------------------------------------------------------------|
| `sort_index` | Integer | Determines the tab order (lower numbers are first).                                  |
| `replace`    | Boolean | (Optional) If true, clears existing entries in this category before adding new ones. |
| `contents`   | Array   | A list of entry objects.                                                             |

#### Content Types

1. **Manual Entry:** Adds a specific Entity or Block.

```json
{
  "type": "entry",
  "id": "minecraft:pig"
}

```

2. **Auto-Populate:** Automatically adds entities based on a preset strategy.

```json
{
  "type": "auto_populate",
  "strategy": "hostile"
}

```

*Available Strategies:* `passive`, `hostile`, `flora`.

#### Example: Custom Category

`data/mypack/fieldguide/categories/swamp_life.json`

```json
{
  "sort_index": 5,
  "contents": [
    {
      "type": "entry",
      "id": "minecraft:frog"
    },
    {
      "type": "entry",
      "id": "minecraft:witch"
    },
    {
      "type": "entry",
      "id": "minecraft:lily_pad"
    }
  ]
}

```

---

## Resource Packs (Customizing Visuals)

You can customize the appearance of categories, render settings for entities, and descriptions using **Resource Packs**.

### Category Visuals

To customize the icon and color of a category tab, create a JSON file at:
`assets/fieldguide/visuals/categories/<category_name>.json`

*Note: The filename must match the path of the category ID.*

```json
{
  "color": "#32a852",
  "icon": "minecraft:textures/item/slime_ball.png"
}

```

### Entry Configuration (Scaling & Offsets)

Sometimes entities render too large, too small, or off-center in the book. You can adjust this by creating a JSON file
at:
`assets/fieldguide/visuals/entries/<entity_name>.json`

*Note: The filename must match the path of the entity/block ID.*

#### JSON Structure

| Field        | Type  | Description                                                         |
|--------------|-------|---------------------------------------------------------------------|
| `scale`      | Float | Base scale multiplier for the model.                                |
| `y_offset`   | Float | Moves the model up/down globally.                                   |
| `x_offset`   | Float | Moves the model left/right globally.                                |
| `grid_scale` | Float | (Optional) Overrides scale specifically for the grid view.          |
| `page_scale` | Float | (Optional) Overrides scale specifically for the detailed page view. |

#### Example: Adjusting a Creeper

`assets/fieldguide/visuals/entries/creeper.json`

```json
{
  "scale": 0.8,
  "y_offset": 10.0,
  "page_scale": 1.2
}

```

### Descriptions & Localization

By default, Field Guide uses descriptions from [Item Descriptions](https://modrinth.com/mod/item-descriptions) and its
addon resource pack [Mod Descriptions](https://modrinth.com/resourcepack/mod-descriptions). To add names to custom
categories or descriptions to entries, add lines to your `en_us.json` (or other language file).

| Type              | Key Format                                | Example                                                             |
|-------------------|-------------------------------------------|---------------------------------------------------------------------|
| Category Name     | `category.fieldguide.<category_id>`       | `"category.fieldguide.swamp_life": "Swamp Life"`                    |
| Entry Description | `fieldguide.<namespace>.<id>.description` | `"fieldguide.minecraft.pig.description": "A common farm animal..."` |

### Texture Overrides (2D Sprites)

If an entity renders poorly (or if you prefer 2D illustrations), you can force the Field Guide to use a texture instead
of the 3D model.

Place textures in: `assets/<namespace>/textures/fieldguide/entries/`

The mod looks for textures in this specific order:

1. **View-Specific:** `<id>_page.png` (Used on the details page) or `<id>_grid.png` (Used in the list).
2. **General:** `<id>.png` (Used for both if specifics aren't found).

*Example:* To override the specific rendering of a pig in `minecraft`, place a file at
`assets/minecraft/textures/fieldguide/entries/pig.png`.

---

### License

This project is licensed under the **MIT License**.

### Contributing

Contributions are welcome! If you find a bug or have a feature request, please open an issue or submit a pull request.