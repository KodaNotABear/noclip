# Noclip

**The Backrooms as a survival challenge. Complete Minecraft from inside the endless yellow rooms.**

Noclip replaces overworld generation with Level 0 of the Backrooms: an infinite, sealed office interior of yellowed wallpaper, damp carpet, and humming fluorescent light. No sky, no trees, no ore veins. The architecture is unbreakable. Everything you need has to come from what the rooms contain, what spawns in the dark, and what you build in between.

## What's in the alpha

- **Level 0 world type.** Select "The Backrooms" when creating a world. Seeded room-and-corridor generation with walls, doorways, carpet floors, and a ceiling grid of fluorescent panels, sealed above and below.
- **Unbreakable structure, breakable contents.** Walls, floors, and ceilings cannot be mined. Fluorescent lights can be broken and picked up, so light itself is a resource you harvest and place.
- **Darkness with rules.** Most of Level 0 is lit. Dead zones where the grid has failed spawn hostiles under vanilla light rules, but generated carpet is not spawnable, so danger stays in the dark patches instead of covering every floor.
- **Supply rooms.** Rare rooms with loot chests, generated from a data-driven room registry. This is the start of the room pool, not the end of it.
- **The Noclip Key.** Craft one ender pearl and four paper into a key that lets you slip into the Backrooms from a normal survival world, and back out again. This exists so you can visit Level 0 from an ordinary world while the full progression is still being built.

## How to play

Either start a world with the "The Backrooms" world type, or play a normal world and craft the Noclip Key to phase in and out.

Fair warning for the world-type route: the full path from empty-handed to credits is not finished yet. Wood, food, and several progression steps currently depend on loot rooms that are still being authored. Alpha means alpha.

## Where it's going

- More rooms: multi-cell templates, rotations, danger rooms, and themed loot pools
- Progression routes for wood, food, and ores that fit the setting
- Nether and End rethemed as deeper levels, keeping their vanilla dimension IDs so portals, the dragon fight, and dimension-keyed mod behavior keep working
- Room packs as plain datapacks, so mapmakers can add rooms without touching Java

## Compatibility

Rooms, palettes, and loot are JSON and NBT loaded from any datapack namespace. Planned mod-compat patches use `neoforge:conditions`, so they activate only when the target mod is present.

Found a bug or got softlocked? Report it on the [issue tracker](https://github.com/KodaNotABear/noclip/issues).
