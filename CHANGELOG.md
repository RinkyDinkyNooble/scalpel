# Changelog

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
