package com.rinkynooble.scalpel.common.content;

import net.minecraft.resources.ResourceLocation;

/** Implemented by every placeholder Scalpel registers in place of cut content. */
public interface Redacted {
    /** The id this placeholder stands in for. */
    ResourceLocation originalId();
}
