package com.shouyun.theartofstockings;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/** Players use separate clothing storage; Equipable retains vanilla armor-stand interaction. */
public final class StockingsItem extends Item implements Equipable {
    public static final int DEFAULT_COLOR = 0xFF30303B;
    public enum Style { CLASSIC, GRADIENT, PANTYHOSE, FISHNET, GLOW }
    private final Style style;
    public StockingsItem(Properties properties) { this(properties, Style.CLASSIC); }
    public StockingsItem(Properties properties, Style style) { super(properties); this.style = style; }
    public static Style style(ItemStack stack) { return stack.getItem() instanceof StockingsItem item ? item.style : Style.CLASSIC; }
    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.LEGS; }
    public static int color(ItemStack stack) { return DyedItemColor.getOrDefault(stack, style(stack) == Style.GRADIENT ? 0xFFCC77BB : DEFAULT_COLOR); }
    public static int secondaryColor(ItemStack stack) { return 0xFF000000 | stack.getOrDefault(TheArtofStockings.SECONDARY_COLOR.get(), 0x659FE6); }
    public static int mix(int top, int bottom, float amount) {
        amount = Math.clamp(amount, 0, 1);
        int r = Math.round(((top >> 16) & 255) * (1-amount) + ((bottom >> 16) & 255) * amount);
        int g = Math.round(((top >> 8) & 255) * (1-amount) + ((bottom >> 8) & 255) * amount);
        int b = Math.round((top & 255) * (1-amount) + (bottom & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) StockingsActions.startEquip(player, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.theartofstockings.use").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.stand").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.dye").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.palette").withStyle(ChatFormatting.AQUA));
        if (style == Style.GRADIENT) lines.add(Component.translatable("tooltip.theartofstockings.gradient",
                String.format("#%06X", color(stack)&0xFFFFFF), String.format("#%06X", secondaryColor(stack)&0xFFFFFF)).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (style == Style.GLOW) lines.add(Component.translatable("tooltip.theartofstockings.glow").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.theartofstockings.exclusive").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
