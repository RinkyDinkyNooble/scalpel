package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trades are data since 26.1 and their files are filtered as they load ({@code DataFilter}). This is the last check on
 * what a trade made while rolling: an offer that holds cut content after the trade's item modifiers ran comes back as
 * null, which the game skips (the 1.21 ports wrapped trade listings for the same check).
 */
@Mixin(VillagerTrade.class)
public abstract class VillagerTradeMixin {
    @Inject(method = "getOffer", at = @At("RETURN"), cancellable = true)
    private void scalpel$noCutOffer(LootContext context, CallbackInfoReturnable<MerchantOffer> cir) {
        MerchantOffer offer = cir.getReturnValue();
        if (offer == null) {
            return;
        }
        MerchantOffer kept = TabsAndTrades.filterOffer(offer, () -> context.getLevel().registryAccess()
                .lookup(Registries.VILLAGER_TRADE)
                .flatMap(registry -> registry.getResourceKey((VillagerTrade) (Object) this))
                .map(key -> key.identifier().toString())
                .orElse(null));
        if (kept != offer) {
            cir.setReturnValue(kept);
        }
    }
}
