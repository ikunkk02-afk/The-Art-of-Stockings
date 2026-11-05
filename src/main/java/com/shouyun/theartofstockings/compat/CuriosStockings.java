package com.shouyun.theartofstockings.compat;

import com.shouyun.theartofstockings.*;
import java.util.Optional;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** Loaded only when Curios is present; Curios owns persistence, syncing and death drops. */
public final class CuriosStockings {
    public static final String SLOT = "stockings";
    private CuriosStockings() {}
    private static Optional<ICurioStacksHandler> handler(Player player) {
        return CuriosApi.getCuriosInventory(player).flatMap(inventory -> inventory.getStacksHandler(SLOT))
                .filter(handler -> handler.getStacks().getSlots() > 0);
    }
    public static boolean available(Player player) { return handler(player).isPresent(); }
    public static ItemStack get(Player player) {
        return handler(player).map(h -> h.getStacks().getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }
    public static boolean set(Player player, ItemStack stack) {
        var handler = handler(player);
        if (handler.isEmpty()) return false;
        handler.get().getStacks().setStackInSlot(0,stack.copy());
        return true;
    }
    public static void register() {
        ICurioItem behavior = new ICurioItem() {
            @Override public boolean canEquip(SlotContext context, ItemStack stack) {
                if (StockingsWear.accessoriesLoaded() || !context.identifier().equals(SLOT) || context.index() != 0 || context.cosmetic()) return false;
                return context.entity() instanceof Player player && !StockingsActions.busy(player)
                        && StockingsWear.stored(player).isEmpty();
            }
            @Override public boolean canUnequip(SlotContext context, ItemStack stack) {
                return !(context.entity() instanceof Player player) || !StockingsActions.busy(player);
            }
            @Override public boolean canEquipFromUse(SlotContext context, ItemStack stack) { return false; }
            @Override public void curioTick(SlotContext context, ItemStack stack) {
                if (!context.entity().level().isClientSide && context.entity().hasEffect(FootOdor.EFFECT)
                        && !stack.has(TheArtofStockings.STINKY.get())) {
                    stack.set(TheArtofStockings.STINKY.get(),Unit.INSTANCE);
                }
            }
            @Override public ICurio.DropRule getDropRule(SlotContext context, DamageSource source, boolean recentlyHit, ItemStack stack) {
                if (net.minecraft.world.item.enchantment.EnchantmentHelper.has(stack,
                        net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))
                    return ICurio.DropRule.DESTROY;
                return ICurio.DropRule.DEFAULT;
            }
        };
        for (var entry : TheArtofStockings.ITEMS.getEntries()) {
            if (entry.get() instanceof StockingsItem item) CuriosApi.registerCurio(item,behavior);
        }
    }
}
