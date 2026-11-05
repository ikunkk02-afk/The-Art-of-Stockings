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
    public static final int HAND_SLOT=-2,HEAD_SLOT=-3,MIN_SLOT=HEAD_SLOT,NONE_SLOT=-4;
    public PaletteItem(Properties properties) { super(properties); }
    public static boolean holdingPalette(Player player) {
        return player.getMainHandItem().is(TheArtofStockings.PALETTE.get()) || player.getOffhandItem().is(TheArtofStockings.PALETTE.get());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && !StockingsActions.busy(player)) {
            StockingsWear.migrateLegacy(player);
            StockingsHistory.record(player);
            int slot = StockingsActions.isStockings(player.getOffhandItem()) ? 40
                    : StockingsActions.isStockings(player.getMainHandItem()) ? player.getInventory().selected
                    : StockingsActions.wearing(player) ? WORN_SLOT
                    : !StockingsWear.get(player,WearSlot.HANDS).isEmpty() ? HAND_SLOT
                    : !StockingsWear.get(player,WearSlot.HEAD).isEmpty() ? HEAD_SLOT : NONE_SLOT;
            if (slot == NONE_SLOT) for (int i=0; i<36; i++) if (StockingsActions.isStockings(player.getInventory().getItem(i))) { slot=i; break; }
            if (slot >= MIN_SLOT) {
                var stack=target(player,slot).copy();
                PacketDistributor.sendToPlayer(server, new StockingsNetwork.OpenPalette(slot,stack,PaletteEdits.open(player,slot,stack)));
            }
            else player.displayClientMessage(Component.translatable("message.theartofstockings.palette_empty"), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    static ItemStack target(Player player,int slot) {
        return switch(slot) { case WORN_SLOT -> StockingsWear.get(player);case HAND_SLOT -> StockingsWear.get(player,WearSlot.HANDS);
            case HEAD_SLOT -> StockingsWear.get(player,WearSlot.HEAD);default -> slot>=0 && slot<player.getInventory().getContainerSize()?player.getInventory().getItem(slot):ItemStack.EMPTY; };
    }
    public static boolean apply(Player player, int slot, ItemStack expected, int top, int bottom) {
        return apply(player,slot,expected,top,bottom,StockingsItem.opacity(expected),expected.getOrDefault(TheArtofStockings.CUSTOM_TEXTURE.get(),""));
    }
    public static boolean apply(Player player, int slot, ItemStack expected, int top, int bottom, int opacity, String texture) {
        return apply(player,slot,expected,top,bottom,opacity,StockingsItem.depth(expected),texture);
    }
    public static boolean apply(Player player, int slot, ItemStack expected, int top, int bottom, int opacity,int depth, String texture) {
        if (!player.isAlive() || player.isSpectator() || StockingsActions.busy(player) || !holdingPalette(player)
                || slot < MIN_SLOT || slot >= player.getInventory().getContainerSize() || (top & 0xFF000000) != 0 || (bottom & 0xFF000000) != 0
                || opacity < 0 || opacity > 100 || depth<0 || depth>100) return false;
        var stack = target(player,slot);
        if (!StockingsActions.isStockings(stack) || !ItemStack.matches(stack, expected)) return false;
        if ((!texture.isEmpty() && StockingsItem.style(stack) != StockingsItem.Style.CUSTOM) || !CustomStockingsTexture.valid(texture)) return false;
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(top, true));
        if (StockingsItem.twoColors(stack)) stack.set(TheArtofStockings.SECONDARY_COLOR.get(), bottom);
        stack.set(TheArtofStockings.OPACITY.get(),opacity);
        stack.set(TheArtofStockings.DEPTH.get(),depth);
        if (texture.isEmpty()) stack.remove(TheArtofStockings.CUSTOM_TEXTURE.get());
        else stack.set(TheArtofStockings.CUSTOM_TEXTURE.get(),texture);
        if(slot<0)StockingsWear.set(player,slot==WORN_SLOT?WearSlot.LEGS:slot==HAND_SLOT?WearSlot.HANDS:WearSlot.HEAD,stack);
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.theartofstockings.palette_applied"), true);
        return true;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.theartofstockings.palette_use").withStyle(ChatFormatting.GRAY));
    }
}
