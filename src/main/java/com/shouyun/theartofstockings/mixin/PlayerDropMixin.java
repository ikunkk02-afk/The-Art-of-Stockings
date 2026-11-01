package com.shouyun.theartofstockings.mixin;

import com.shouyun.theartofstockings.StockingsWear;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDropMixin {
    @Inject(method="dropEquipment",at=@At("TAIL"))
    private void stockings$dropClothing(CallbackInfo ci) { StockingsWear.dropOnDeath((Player)(Object)this); }
}
