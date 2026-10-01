# Changelog

## 0.2.2 (2026-10-01)

- Removing advancements no longer crashes the server when a mod awards them from code. Many mods (most made with MCreator) look an advancement up by id and pass it on without checking that it exists, so the first time a player earned one that a rule had removed, the server crashed. A missing advancement now does nothing.

## 0.2.1 (2026-09-29)

- Fixed a crash with Lost Cities when cutting `minecraft:blackstone`, `minecraft:bricks` or `minecraft:deepslate`. Lost Cities has building variants with those names, written without a mod id. Scalpel took them for the vanilla blocks and replaced them with air, so Lost Cities crashed when the world loaded.
- Fixed servers not starting ("Failed to load datapacks") when a cut block has the same id as a vanilla world generation feature, such as `minecraft:nether_sprouts`, `minecraft:glow_lichen` or `minecraft:bamboo`. Biomes list those features by id, and Scalpel replaced the ids with air.
- In vanilla world generation files, only the blocks a file places now turn into air. Other ids that match a cut block, such as a feature in a biome or a noise in the surface rules, are taken out of their list instead.
- In another mod's own data, such as Lost Cities buildings and palettes, only full ids like `minecraft:stone` now count as blocks. Scalpel leaves names without a mod id as they are, and lists the ones that match something you cut in the report under "worldgen names left alone".

## 0.2.0 (2026-09-28)

- New `hide` verb. Hidden items, blocks and entities stay in the game and keep working, with their recipes, loot, tags, trades and world generation, but they're left out of JEI, EMI and the creative tabs, including the search tab. When rules overlap, the gentler one wins: `keep`, then `hide`, then `redact`, then `remove`.
- Hiding a block or an entity also hides the item that places or spawns it. Protected ids can be hidden.
- The report lists hidden content in its own section, and `/scalpel explain` and `/scalpel test` show it.
- Dry run no longer hides anything in JEI or EMI.
- Redacted vanilla items say "Redacted by Scalpel" in their tooltip, so "hidden" only ever means the new verb.

## 0.1.1 (2026-09-27)

- Cutting an item now hides all of its variants in JEI. Before, `redact item minecraft:enchanted_book` left every enchanted book, potion or other NBT variant visible.
- Cut items no longer come out of loot when a loot function creates them, such as the enchanted books in fishing treasure and chest loot. A new global loot modifier, `scalpel:strip_cut_items`, removes them. It runs after every other global loot modifier, and the report lists each loot table it had to clean.
- When Scalpel removes another mod's loot modifier because it uses cut content, Forge no longer logs a "Could not decode GlobalLootModifier" warning for it.

## 0.1.0 (2026-09-24)

First version, for Minecraft 1.20.1 and Forge 47.

- Rules files in `config/scalpel/` with `keep`, `redact` and `remove`, for items, blocks, entities, recipes, loot tables, advancements and tags. Patterns can be exact ids, wildcards or regular expressions.
- Redacted content becomes a placeholder with a shared model. Removed content never registers.
- Block items, spawn eggs and blocks with the same id as a cut item follow along.
- Recipes, tags, loot tables, loot modifiers, advancements, world generation, structures, creative tabs, trades, JEI and EMI are cleaned up. Recipes can be dropped or rewritten.
- Vanilla content is hidden rather than replaced.
- Dry run mode, a report grouped by mod, warnings for rules that match nothing, and a separate log.
- `/scalpel find`, `test`, `explain`, `reload` and `report`.
- Client and server must have the same version and rules to connect.
- Optional JEI, EMI and Jade support.
