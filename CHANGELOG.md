# Changelog

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
