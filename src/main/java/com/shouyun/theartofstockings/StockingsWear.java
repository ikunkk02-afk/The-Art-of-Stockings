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
    public static boolean curiosLoaded() { return net.neoforged.fml.ModList.get().isLoaded("curios"); }
    public static boolean accessoriesLoaded() { return net.neoforged.fml.ModList.get().isLoaded("accessories"); }
    public static ItemStack accessory(Player player) {
        if (accessoriesLoaded()) return com.shouyun.theartofstockings.compat.AccessoriesStockings.get(player);
        if (curiosLoaded()) return com.shouyun.theartofstockings.compat.CuriosStockings.get(player);
        return ItemStack.EMPTY;
    }
    public static boolean accessoryAvailable(Player player) {
        if (accessoriesLoaded()) return com.shouyun.theartofstockings.compat.AccessoriesStockings.available(player);
        return curiosLoaded() && com.shouyun.theartofstockings.compat.CuriosStockings.available(player);
    }
    private static boolean setAccessory(Player player,ItemStack stack) {
        if (accessoriesLoaded()) return com.shouyun.theartofstockings.compat.AccessoriesStockings.set(player,stack);
        return curiosLoaded() && com.shouyun.theartofstockings.compat.CuriosStockings.set(player,stack);
    }
    public static ItemStack stored(Player player) {
        var stack=player.getExistingDataOrNull(TheArtofStockings.WORN_STOCKINGS);
        return stack==null?ItemStack.EMPTY:stack;
    }
    public static ItemStack get(Player player) {
        var stack=accessory(player);
        if (!stack.isEmpty()) return stack;
        return stored(player);
    }
    public static ItemStack visual(Player player) {
        return accessoriesLoaded() ? com.shouyun.theartofstockings.compat.AccessoriesStockings.visual(player) : get(player);
    }
    public static void set(Player player,ItemStack stack) {
        if (setAccessory(player,stack)) {
            player.removeData(TheArtofStockings.WORN_STOCKINGS);
            return;
        }
        player.setData(TheArtofStockings.WORN_STOCKINGS,stack.copy());
    }
    public static ItemStack get(Player player, WearSlot slot) {
        if (slot == WearSlot.LEGS) return get(player);
        var stack=player.getExistingDataOrNull(slot == WearSlot.HANDS ? TheArtofStockings.HAND_STOCKINGS : TheArtofStockings.HEAD_STOCKINGS);
        return stack == null ? ItemStack.EMPTY : stack;
    }
    public static void set(Player player, WearSlot slot, ItemStack stack) {
        if (slot == WearSlot.LEGS) set(player,stack);
        else player.setData(slot == WearSlot.HANDS ? TheArtofStockings.HAND_STOCKINGS : TheArtofStockings.HEAD_STOCKINGS,stack.copy());
    }
    public static boolean hasExtra(Player player) {
        return !get(player,WearSlot.HANDS).isEmpty() || !get(player,WearSlot.HEAD).isEmpty();
    }
    public static boolean any(Player player) { return !get(player).isEmpty() || hasExtra(player); }
    public static void migrateLegacy(Player player) {
        if(player.level().isClientSide)return;
        if (!stored(player).isEmpty() && accessoryAvailable(player)) {
            var previous=stored(player).copy();
            if (accessory(player).isEmpty()) setAccessory(player,previous);
            else player.getInventory().placeItemBackInInventory(previous);
            player.removeData(TheArtofStockings.WORN_STOCKINGS);
        }
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
        for (var slot : WearSlot.values()) {
            if (slot == WearSlot.LEGS && !accessory(player).isEmpty()) continue;
            var pair=get(player,slot);
            if(pair.isEmpty())continue;
            // Called during vanilla's captured equipment-drop phase, so normal drop events see it.
            set(player,slot,ItemStack.EMPTY);
            if(!EnchantmentHelper.has(pair,EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))player.spawnAtLocation(pair);
        }
    }
}
