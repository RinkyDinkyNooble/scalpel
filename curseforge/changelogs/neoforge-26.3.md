# Scalpel for NeoForge, Minecraft 26.3

Changelog for `scalpel-neoforge-26.3-<version>.jar`.

## Unreleased

First version, for Minecraft 26.3 on NeoForge.

- From 26.3 on, recipes, advancements and loot tables name each other. When Scalpel drops a recipe or a loot table, it also takes it out of the advancements and loot tables that name it.
- From 26.1 on, villager trades are data too. A trade that uses a cut item or enchantment is left out, along with its place in the villagers' trade lists.
- From 1.21 on, enchantments are data. A redacted enchantment stays, but it does nothing and reads "Redacted". A removed one is left out of the game's data, and the tags, loot tables, advancements and other enchantments that name it are fixed up.
- Recipe viewers: JEI and Jade, because EMI has no 26.x release.
