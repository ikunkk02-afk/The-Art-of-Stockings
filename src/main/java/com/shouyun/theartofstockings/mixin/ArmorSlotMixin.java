package com.shouyun.theartofstockings.mixin;

import com.shouyun.theartofstockings.StockingsActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class ArmorSlotMixin {
    @Shadow @Final private LivingEntity owner;
    @Shadow @Final private EquipmentSlot slot;
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void stockings$blockArmor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == EquipmentSlot.LEGS && owner instanceof Player && StockingsActions.isStockings(stack)) cir.setReturnValue(false);
    }
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void stockings$animatedRemoval(Player player, CallbackInfoReturnable<Boolean> cir) {
        var slot = (net.minecraft.world.inventory.Slot)(Object)this;
        if (StockingsActions.isStockings(slot.getItem())) cir.setReturnValue(false);
    }
}
