# Scalpel

Scalpel is a Forge mod for modpack makers. You write rules listing the items, blocks and entities you don't want, and Scalpel cuts them out while the game loads. Their recipes, tags, loot, advancements, world generation and structure pieces go with them.

Hiding content (removing recipes, hiding it in JEI) still leaves it loaded. Scalpel stops it from loading at all, so a pack can include a big decoration or palette mod and only pay for the parts it keeps.

> **Existing worlds can break.** Blocks that are cut from a world that already has them turn into air or placeholders. Add Scalpel and your rules before a world is created, and treat rule changes like adding or removing a mod.

- Minecraft 1.20.1, Forge 47 or newer
- Needed on both the client and the server, with the same rules
- License: 0BSD

## Contents

- [Quick start](#quick-start)
- [Rules files](#rules-files)
- [Redact or remove](#redact-or-remove)
- [What else gets cleaned up](#what-else-gets-cleaned-up)
- [Recipes that use cut content](#recipes-that-use-cut-content)
- [Vanilla content](#vanilla-content)
- [Settings](#settings)
- [Commands](#commands)
- [Report and log](#report-and-log)
- [Multiplayer](#multiplayer)
- [A safe way to work](#a-safe-way-to-work)
- [Compatibility](#compatibility)
- [Limits](#limits)
- [Building](#building)

## Quick start

1. Add Scalpel to the pack and start the game once. It creates `config/scalpel/` with a commented-out `example.rules`.
2. Create a file such as `config/scalpel/decor.rules`:
   ```
   redact any examplemod:*
   keep   block examplemod:marble_bricks
   ```
3. Set `dryRun = true` in `config/scalpel/scalpel.toml` and start the game. Read `logs/scalpel/report.txt` to see what would be cut.
4. Turn dry run off and restart.

## Rules files

Every `*.rules` file in `config/scalpel/` is loaded, in alphabetical order. Use as many files as you like. One rule per line, and `#` starts a comment.

```
<verb> <type> <pattern> [option]
```

### Verbs

| Verb | What it does |
|---|---|
| `keep` | Protects matching ids from every other rule. |
| `redact` | Replaces the content with a blank "Redacted" placeholder. This is the safe choice. |
| `remove` | The content never loads. Saves the most, but some mods expect their content to exist. |

When several rules match the same id, `keep` wins over `redact`, and `redact` wins over `remove`. Order in the files doesn't matter. That makes this pattern work:

```
remove any  palettemod:*
keep   block palettemod:red_bricks
keep   block palettemod:blue_bricks
```

### Types

| Type | Matches |
|---|---|
| `item` | items |
| `block` | blocks |
| `entity` | entity types |
| `any` | items, blocks and entities |
| `recipe` | recipe ids |
| `loot` | loot table ids |
| `advancement` | advancement ids |
| `tag` | tag ids (items, blocks and entities) |

Recipes, loot tables, advancements and tags can only be kept or removed. Removing a tag empties it.

### Patterns

| Pattern | Matches |
|---|---|
| `examplemod:ruby` | exactly that id |
| `examplemod:*` | everything from one mod |
| `examplemod:*_eggs` | `*` matches any run of characters, `?` matches one |
| `*:*_slab` | every slab from every mod |
| `/^examplemod:[a-z_]*_eggs$/` | a Java regular expression between slashes, matched against the whole id |

`*` never crosses the `:` between the mod id and the name. Ids are lowercase. Tag rules accept `#forge:ores/tin` as well as `forge:ores/tin`.

### Options

`rewrite` or `drop` at the end of a `redact` or `remove` rule for items or blocks decides what happens to recipes that use them. See [Recipes that use cut content](#recipes-that-use-cut-content).

```
remove item examplemod:ruby rewrite
```

Lines with mistakes are skipped, and each one is listed at the top of the report with its file and line number.

## Redact or remove

**Redact** keeps the id but registers a placeholder under it: a plain block or item with a "redacted document" texture, the name "Redacted" and a tooltip that says which id it replaced. It has no behaviour, no creative tab entry, and it's hidden in JEI and EMI. The only way to get one is `/give`. The original mod's block, item or entity is never created and its models are never loaded. Mods that look up their own content by id still find something, so redact rarely crashes anything.

A redacted entity shows up as a floating "Redacted" label. It can't be summoned and isn't saved with the world.

**Remove** means the id doesn't exist at all: `/give` fails and nothing refers to it. When a mod's own setup code still asks for a removed block or entity, it gets an unregistered stand-in instead of crashing. Removed items all point to one hidden item, `scalpel:removed`, because items have to be registered to exist in an inventory.

Linked content follows along:
- Cutting a block also cuts the item that places it.
- Cutting an item also cuts the block with the same id.
- Cutting an entity also cuts its spawn egg.

## What else gets cleaned up

For everything that is cut, whether redacted or removed:

- **Recipes** that make it are removed. Recipes that use it are removed or rewritten.
- **Tags** lose it. A tag left empty by a cut is tracked, so recipes that use that tag are handled too.
- **Loot tables** lose the entries that drop it. A cut block's or entity's own loot table goes.
- **Loot modifiers** that mention it are removed.
- **Advancements** that mention it are removed, along with their children.
- **World generation**: ore targets and spawn entries that mention it are taken out. A feature that can't work without it is switched off. In other mods' world generation data, such as Lost Cities palettes and building parts, a cut block becomes air.
- **Structures** place air where a cut block was, and leave out cut entities.
- **Creative tabs** and **villager and wandering trader trades** leave it out.
- **JEI, EMI and Jade**: hidden in JEI and EMI. Jade shows which id a placeholder block replaced.

Each of these changes is listed in the report with the file or structure it came from. That way you can fix the source yourself, and Scalpel has less to clean up.

## Recipes that use cut content

The `ingredientMode` setting and the per-rule option decide this.

- `drop` (default): the recipe is removed.
- `rewrite`: the cut item is taken out and the recipe keeps working without it, where that makes sense. In a shaped recipe the slot becomes empty. In a shapeless recipe the ingredient is taken out. If an ingredient offered several items, only the cut one goes. A recipe that ends up with nothing left, or makes a cut item, is still removed.

Recipes that scripts add (KubeJS, CraftTweaker) are also checked. Scalpel can rewrite shaped and shapeless crafting recipes from scripts. Other script recipes are removed.

## Vanilla content

Vanilla code keeps direct references to its own blocks, items and entities, so Scalpel treats `minecraft:` content differently:

- `redact` hides vanilla content instead of replacing it. It stays registered as itself, but it's removed from recipes, tags, loot, world generation, structures, creative tabs, trades and recipe viewers. The tooltip says "Hidden by Scalpel".
- `remove` on vanilla content acts like `redact`, unless you set `allowRemovingVanilla = true`. Removing vanilla content can crash the game.
- A few ids are protected, such as stone, water, fire, portals and item entities. Set `protectCriticalIds = false` to allow cutting them. Air and the player can never be cut.

## Settings

`config/scalpel/scalpel.toml`. Mods that edit configs in game (Configured and similar) can change it too. Every setting needs a restart.

| Setting | Default | What it does |
|---|---|---|
| `general.dryRun` | `false` | Log and report what would be cut, without cutting anything. |
| `general.logFile` | `true` | Write Scalpel's own log to `logs/scalpel.log`. |
| `registration.allowRemovingVanilla` | `false` | Let `remove` rules really remove `minecraft:` content. |
| `registration.protectCriticalIds` | `true` | Refuse to cut ids the game relies on internally. |
| `recipes.ingredientMode` | `drop` | `drop` or `rewrite` recipes that use cut content. |
| `reports.cascadeDepth` | `3` | How far the "lost every recipe" part of the report follows. `0` turns it off. |

## Commands

For operators (permission level 2). Suggestions work for every id argument.

| Command | What it does |
|---|---|
| `/scalpel test <type> <pattern>` | Lists what a pattern matches right now, so you can check a glob or regex before restarting. |
| `/scalpel find <id>` | Lists the loaded recipes and tags that use the id, every data file that mentions it, and every file in `config/`, `defaultconfigs/`, `kubejs/` and `scripts/` that mentions it (with line numbers). Use it to decide whether something can be removed rather than redacted. |
| `/scalpel explain <id>` | Shows which rules match an id and what happened to it. |
| `/scalpel reload` | Reads the rules again and reloads data. Recipe, loot, advancement and tag rules take effect straight away. Item, block and entity rules need a restart. |
| `/scalpel report` | Writes the report again. |

`test` and `find` show the first few results in chat and write the full list to `logs/scalpel/`.

## Report and log

`logs/scalpel/report.txt` is written after startup, after each data reload, and when the server stops. It lists:

- the rules hash, the settings, and any rule errors
- each rule with how many ids it matched, and rules that matched nothing (usually a typo, or an id a mod update renamed)
- everything cut, grouped by mod
- every data change, grouped by mod
- items that lost every recipe because of what was cut, a few levels deep (report only, nothing there is cut)

`logs/scalpel.log` has one line per decision, naming the mod and the rule. If the game crashes after you add a rule, the last lines of this log tell you what was cut just before.

## Multiplayer

Scalpel must be on the server and on every client, with the same version and the same rules files. It checks this when a player joins. If anything differs, Forge refuses the connection before any registry error and names the `scalpel:rules` channel with both versions. The report shows the rules hash for each file, so you can compare client and server.

Rules files live in `config/`, so they ship with the pack like any other config.

## A safe way to work

1. Write the rules and start with `dryRun = true`. Read the report.
2. Start with `redact`. Play a bit and check the log for errors.
3. Use `/scalpel find <id>` on things you'd like to remove. If only recipes, tags and loot mention them, switch those rules to `remove`. If a config file or another mod's data names them (a Lost Cities palette, for example), keep using `redact` or change that file first.
4. Keep an eye on "Unmatched rules" in the report after mod updates.

## Compatibility

- JEI, EMI and Jade support is built in and optional.
- Tested with ModernFix, FerriteCore, Smooth Boot (Reloaded), Saturn, KubeJS, Farmer's Delight, and several decoration and mob mods.
- If ModernFix's dynamic resources option is on, the original mod's blockstate files may log warnings for placeholder blocks. They look right regardless.

## Limits

- Existing worlds with cut content in them can break.
- A mod that casts its own registry entry to its own class will crash with `redact`. This is common in tech mods and rare in decoration mods. Remove the whole mod's content instead, or keep that entry.
- Textures of cut blocks and items are still stitched into the texture atlas. Models are skipped.
- Loot changes made by scripts at runtime (LootJS, global loot modifiers written in code) are not filtered.

## Building

```
./gradlew build
```

The mod jar is `forge-1.20.1/build/libs/scalpel-forge-1.20.1-<version>.jar`. `core/` holds the rule engine with no Minecraft code in it, so another loader or Minecraft version can reuse it as its own folder next to `forge-1.20.1/`.
