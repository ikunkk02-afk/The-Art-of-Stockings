package com.shouyun.theartofstockings.mixin;

import com.google.common.collect.ImmutableList;
import com.shouyun.theartofstockings.FootOdorAi;
import com.shouyun.theartofstockings.CharmAi;
import com.shouyun.theartofstockings.Charm;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
abstract class VillagerBrainMixin {
    @Inject(method = "registerBrainGoals", at = @At("RETURN"))
    private void theartofstockings$addOdorAvoidance(Brain<Villager> brain, CallbackInfo ci) {
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(new FootOdorAi.AvoidScent()));
        brain.addActivity(Activity.CORE, 2, ImmutableList.of(new CharmAi.AttractVillager()));
    }
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void theartofstockings$charmDiscount(net.minecraft.world.entity.player.Player player,CallbackInfo ci) {
        Charm.discount((Villager)(Object)this,player);
    }
}
