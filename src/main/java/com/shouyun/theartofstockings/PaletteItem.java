package com.shouyun.theartofstockings;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PaletteItem extends Item {
    public static final int WORN_SLOT=-1;
    public PaletteItem(Properties properties) { super(properties); }
    public static boolean holdingPalette(Player player) {
        return player.getMainHandItem().is(TheArtofStockings.PALETTE.get()) || player.getOffhandItem().is(TheArtofStockings.PALETTE.get());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && !StockingsActions.busy(player)) {
            StockingsWear.migrateLegacy(player);
            int slot = StockingsActions.isStockings(player.getOffhandItem()) ? 40
                    : StockingsActions.isStockings(player.getMainHandItem()) ? player.getInventory().selected
                    : StockingsActions.wearing(player) ? WORN_SLOT : -2;
            if (slot == -2) for (int i=0; i<36; i++) if (StockingsActions.isStockings(player.getInventory().getItem(i))) { slot=i; break; }
            if (slot >= WORN_SLOT) PacketDistributor.sendToPlayer(server, new StockingsNetwork.OpenPalette(slot, target(player,slot).copy()));
            else player.displayClientMessage(Component.translatable("message.theartofstockings.palette_empty"), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    private static ItemStack target(Player player,int slot) {
        return slot==WORN_SLOT?StockingsWear.get(player):player.getInventory().getItem(slot);
    }
    public static boolean apply(Player player, int slot, ItemStack expected, int top, int bottom) {
        if (!player.isAlive() || player.isSpectator() || StockingsActions.busy(player) || !holdingPalette(player)
                || slot < WORN_SLOT || slot >= player.getInventory().getContainerSize() || (top & 0xFF000000) != 0 || (bottom & 0xFF000000) != 0) return false;
        var stack = target(player,slot);
        if (!StockingsActions.isStockings(stack) || !ItemStack.matches(stack, expected)) return false;
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(top, true));
        if (StockingsItem.style(stack) == StockingsItem.Style.GRADIENT) stack.set(TheArtofStockings.SECONDARY_COLOR.get(), bottom);
        if(slot==WORN_SLOT)StockingsWear.set(player,stack);
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.theartofstockings.palette_applied"), true);
        return true;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.theartofstockings.palette_use").withStyle(ChatFormatting.GRAY));
    }
}
