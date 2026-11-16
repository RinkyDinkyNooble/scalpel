package com.rinkynooble.scalpel.fabric;

/** The id a loot table was loaded under, for the report (vanilla 1.20.1 tables don't know their own). */
public interface LootTableId {
    String scalpel$id();

    void scalpel$setId(String id);
}
