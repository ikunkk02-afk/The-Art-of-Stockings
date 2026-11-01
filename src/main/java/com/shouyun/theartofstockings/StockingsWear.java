package com.shouyun.theartofstockings;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;

/** Player clothing storage, persisted and synchronized by NeoForge attachments. */
public final class StockingsWear {
    private StockingsWear() {}
    public static ItemStack get(Player player) {
        var stack=player.getExistingDataOrNull(TheArtofStockings.WORN_STOCKINGS);
        return stack==null?ItemStack.EMPTY:stack;
    }
    public static void set(Player player,ItemStack stack) {
        player.setData(TheArtofStockings.WORN_STOCKINGS,stack.copy());
    }
    public static void migrateLegacy(Player player) {
        if(player.level().isClientSide)return;
        var legacy=player.getItemBySlot(EquipmentSlot.LEGS);
        if(!StockingsActions.isStockings(legacy))return;
        // Transfer first, then clear the former slot; never overwrite a separate worn pair.
        if(get(player).isEmpty())set(player,legacy);
        else player.getInventory().placeItemBackInInventory(legacy);
        player.setItemSlot(EquipmentSlot.LEGS,ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }
    public static void dropOnDeath(Player player) {
        if(player.level().isClientSide || player.isSpectator()
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))return;
        var pair=get(player);
        if(pair.isEmpty())return;
        // Called during vanilla's captured equipment-drop phase, so normal drop events see it.
        set(player,ItemStack.EMPTY);
        if(!EnchantmentHelper.has(pair,EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))player.spawnAtLocation(pair);
    }
}
