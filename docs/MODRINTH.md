# Noclip

**The Backrooms as a skyblock inspired survival challenge.**

Noclip replaces overworld generation with Level 0 of the Backrooms: an infinite, sealed office interior of yellowed wallpaper, damp carpet, and fluorescent light. Nothing here is natural, and your survival depends on your ability to adapt.

## Currently Implemented

- **Level 0 world type.** Select "The Backrooms" world type when creating a world. Seeded generation with walls, doorways, carpet floors, and a ceiling grid of fluorescent panels, sealed above and below.
- **Unbreakable structure, breakable contents.** Walls, floors, and ceilings cannot be mined.
- **Darkness with rules.** Most of Level 0 is lit. Mobs can only spawn on player placed blocks.
- **Supply rooms.** Rare rooms with loot chests, generated from a data-driven room registry.
- **The Noclip Key.** Craft one ender pearl and four paper into a key that lets you slip into the Backrooms from a normal survival world, and back out again. This exists so you can visit Level 0 from an ordinary world while the full progression is still being built.

## Guide

Either start a world with the "The Backrooms" world type, or play a normal world and craft the Noclip Key to phase in and out.

Fair warning for the world-type route: the full path from spawn to ender dragon is not finished yet. Wood, food, and several progression steps currently depend on loot rooms that are still being made.

## Future Content

- More rooms: multi-cell templates, rotations, danger rooms, and themed loot pools
- Progression routes for wood, food, other required materials for beating the game and earing most achievements.
- Nether and End rethemed as deeper levels, keeping their vanilla dimension IDs so portals, the dragon fight, and dimension-keyed mod behavior keep working
- Room packs as plain datapacks, so modmakers can add rooms.

## Compatibility

NoClip is not compatible with most generation mods.

Rooms, palettes, and loot are JSON and NBT loaded from any datapack namespace. Planned mod-compat patches use `neoforge:conditions`, so they activate only when the target mod is present.

Found a bug or got softlocked? Report it on the [issue tracker](https://github.com/KodaNotABear/noclip/issues).
