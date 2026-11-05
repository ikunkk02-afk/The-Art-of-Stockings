package com.shouyun.theartofstockings.compat;

import com.shouyun.theartofstockings.*;
import io.wispforest.accessories.api.*;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Native Accessories integration; no Curios compatibility layer is needed. */
public final class AccessoriesStockings {
    public static final String SLOT="stockings";
    private AccessoriesStockings() {}
    private static AccessoriesContainer container(Player player) {
        var capability=AccessoriesCapability.get(player);
        if (capability==null) return null;
        var container=capability.getContainers().get(SLOT);
        return container!=null && container.getSize()>0 ? container : null;
    }
    public static boolean available(Player player) { return container(player)!=null; }
    public static ItemStack get(Player player) {
        var container=container(player);
        return container==null ? ItemStack.EMPTY : container.getAccessories().getItem(0);
    }
    public static ItemStack visual(Player player) {
        var container=container(player);
        if(container==null)return StockingsWear.stored(player);
        if(!container.shouldRender(0))return ItemStack.EMPTY;
        var cosmetic=container.getCosmeticAccessories().getItem(0);
        return StockingsActions.isStockings(cosmetic) ? cosmetic : container.getAccessories().getItem(0);
    }
    public static boolean set(Player player,ItemStack stack) {
        var container=container(player);
        if(container==null)return false;
        container.getAccessories().setItem(0,stack.copy());
        return true;
    }
    public static void register() {
        Accessory behavior=new Accessory() {
            @Override public boolean canEquip(ItemStack stack,SlotReference reference) {
                return reference.slotName().equals(SLOT) && reference.slot()==0
                        && reference.entity() instanceof Player player && !StockingsActions.busy(player)
                        && StockingsWear.stored(player).isEmpty();
            }
            @Override public boolean canUnequip(ItemStack stack,SlotReference reference) {
                return (!(reference.entity() instanceof Player player) || !StockingsActions.busy(player))
                        && Accessory.super.canUnequip(stack,reference);
            }
            @Override public boolean canEquipFromUse(ItemStack stack) { return false; }
            @Override public void tick(ItemStack stack,SlotReference reference) {
                if (!reference.entity().level().isClientSide && reference.entity().hasEffect(FootOdor.EFFECT)
                        && !stack.has(TheArtofStockings.STINKY.get())) stack.set(TheArtofStockings.STINKY.get(),Unit.INSTANCE);
            }
        };
        for(var entry:TheArtofStockings.ITEMS.getEntries())
            if(entry.get() instanceof StockingsItem item) AccessoriesAPI.registerAccessory(item,behavior);
    }
}
