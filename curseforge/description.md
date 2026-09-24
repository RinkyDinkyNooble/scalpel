# Scalpel

Scalpel is for modpack makers. List the items, blocks and entities you don't want, and Scalpel cuts them out while the game loads, along with their recipes, tags, loot and world generation.

Hiding content still leaves it loaded. Scalpel stops it from loading, so you can add a big decoration or palette mod and only pay for the parts you keep.

<span style="color:#e03e2d">**Existing worlds can break.** Blocks that are cut from a world that already has them turn into air or placeholders. Set up Scalpel and your rules before a world is created, and treat rule changes like adding or removing a mod.</span>

<span style="color:#e6a700">**Needed on both client and server, with the same rules files.** Players with different rules can't join, and the log says why.</span>

## How it works

You write rules in plain text files in `config/scalpel/`:

```
redact any  examplemod:*
remove any  palettemod:*
keep   block palettemod:red_bricks
```

- **keep** protects content from every other rule
- **redact** swaps the content for a blank "Redacted" placeholder, the safe choice
- **remove** stops the content from existing at all

Patterns can be exact ids, wildcards like `examplemod:*_slab`, or regular expressions.

## What it cleans up

Recipes, tags, loot tables, advancements, world generation, structures, creative tabs, villager trades and JEI / EMI all stop showing or using what you cut. Recipes that used a cut item can be removed or rewritten without it.

## Tools

- Dry run mode, so you can see what would be cut first
- A report that lists everything cut, grouped by mod, plus rules that matched nothing
- `/scalpel test`, `/scalpel find` and `/scalpel explain` to check patterns and see what uses an id

## Good to know

- Minecraft 1.20.1, Forge 47 or newer.
- JEI, EMI and Jade support is built in and optional.
- <span style="color:#e6a700">Removing content that another mod still looks up can crash that mod. Use `redact` first, and run `/scalpel find <id>` before switching a rule to `remove`.</span>

Full documentation and source: https://github.com/RinkyDinkyNooble/scalpel
