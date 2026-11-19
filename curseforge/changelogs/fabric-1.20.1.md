# Scalpel for Fabric and Quilt, Minecraft 1.20 and 1.20.1

Changelog for `scalpel-fabric-1.20.1-<version>.jar`.

## 0.5.0 (2026-11-16)

- Fixed: removing an item that an armor trim material or trim pattern is made from no longer stops the world from loading ("Failed to load datapacks"). That trim is left out instead.
- Fixed: a server with Tech Reborn stopped at startup while Scalpel filtered villager trades.

## 0.4.0 (2026-11-16)

First version for Fabric and Quilt, for Minecraft 1.20 and 1.20.1. Needs Fabric API (on Quilt, Quilted Fabric API). The rules, the report and the commands are the same as on Forge.

- On Fabric, mods build their own blocks and items before registering them. A mod can keep using one that Scalpel removed, so try `remove` on one mod at a time there; `redact` and `hide` are safer.
