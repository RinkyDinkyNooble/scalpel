# Scalpel for Fabric and Quilt, Minecraft 1.21 and 1.21.1

Changelog for `scalpel-fabric-1.21.1-<version>.jar`.

## 0.5.0 (2026-11-16)

First version, for Minecraft 1.21 and 1.21.1 on Fabric and Quilt. Both need Fabric API, since there's no Quilted Fabric API for 1.21.1.

- From 1.21 on, enchantments are data. A redacted enchantment stays, but it does nothing and reads "Redacted". A removed one is left out of the game's data, and the tags, loot tables, advancements and other enchantments that name it are fixed up.
- On Fabric, mods build their own blocks and items before registering them. A mod can keep using one that Scalpel removed, so try `remove` on one mod at a time there; `redact` and `hide` are safer.
