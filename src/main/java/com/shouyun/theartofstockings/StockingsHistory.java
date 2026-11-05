package com.shouyun.theartofstockings;

import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Permanent item history: real player wear counts, armor-stand display does not. */
@EventBusSubscriber(modid=TheArtofStockings.MODID)
public final class StockingsHistory {
    private StockingsHistory() {}
    public static boolean wasWorn(ItemStack stack) {
        return StockingsActions.isStockings(stack) && (stack.has(TheArtofStockings.WORN_BEFORE.get())
                || stack.has(TheArtofStockings.STINKY.get())); // Older scented pairs already prove prior wear.
    }
    public static boolean mark(ItemStack stack) {
        if(!StockingsActions.isStockings(stack) || stack.has(TheArtofStockings.WORN_BEFORE.get()))return false;
        stack.set(TheArtofStockings.WORN_BEFORE.get(),Unit.INSTANCE);return true;
    }
    public static void record(Player player) {
        if(player.level().isClientSide || !player.isAlive() || player.isSpectator() || StockingsActions.busy(player))return;
        for(var slot:WearSlot.values()) {
            var stack=StockingsWear.get(player,slot);
            if(!StockingsActions.isStockings(stack) || stack.has(TheArtofStockings.WORN_BEFORE.get()))continue;
            var recorded=stack.copy();mark(recorded);
            StockingsWear.set(player,slot,recorded);
        }
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) { record(event.getEntity()); }
}
