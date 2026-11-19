# Scalpel for Fabric and Quilt, Minecraft 26.1 to 26.2

Changelog for `scalpel-fabric-26.1-<version>.jar`.

## Unreleased

First version, for Minecraft 26.1, 26.1.1, 26.1.2 and 26.2 on Fabric and Quilt. Both need Fabric API.

- From 26.1 on, villager trades are data too. A trade that uses a cut item or enchantment is left out, along with its place in the villagers' trade lists.
- From 1.21 on, enchantments are data. A redacted enchantment stays, but it does nothing and reads "Redacted". A removed one is left out of the game's data, and the tags, loot tables, advancements and other enchantments that name it are fixed up.
- Recipe viewers: JEI and Jade, because EMI has no 26.x release.
- On Fabric, mods build their own blocks and items before registering them. A mod can keep using one that Scalpel removed, so try `remove` on one mod at a time there; `redact` and `hide` are safer.
