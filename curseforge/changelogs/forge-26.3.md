# Scalpel for Forge, Minecraft 26.3

Changelog for `scalpel-forge-26.3-<version>.jar`.

## Unreleased

First version, for Minecraft 26.3 on Forge 66.

- From 26.3 on, recipes, advancements and loot tables name each other. When Scalpel drops a recipe or a loot table, it also takes it out of the advancements and loot tables that name it.
- From 26.1 on, villager trades are data too. A trade that uses a cut item or enchantment is left out, along with its place in the villagers' trade lists.
- From 1.21 on, enchantments are data. A redacted enchantment stays, but it does nothing and reads "Redacted". A removed one is left out of the game's data, and the tags, loot tables, advancements and other enchantments that name it are fixed up.
- No recipe viewer support, because JEI, EMI and Jade have no Forge builds for 26.x.
