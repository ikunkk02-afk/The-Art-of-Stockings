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
    public enum Style { CLASSIC, GRADIENT, PANTYHOSE, FISHNET, GLOW, STRIPED, CAT_PAW, MATTE, FOX, CUSTOM, BODYSTOCKING }
    private final Style style;
    public StockingsItem(Properties properties) { this(properties, Style.CLASSIC); }
    public StockingsItem(Properties properties, Style style) { super(properties); this.style = style; }
    public static Style style(ItemStack stack) { return stack.getItem() instanceof StockingsItem item ? item.style : Style.CLASSIC; }
    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.LEGS; }
    public static int color(ItemStack stack) {
        return DyedItemColor.getOrDefault(stack, switch (style(stack)) {
            case GRADIENT -> 0xFFCC77BB;
            case FOX -> 0xFFB84B16;
            case STRIPED -> 0xFFB8B0C2;
            case CAT_PAW -> 0xFFB9AFC0;
            case CUSTOM -> 0xFFFFFFFF;
            default -> DEFAULT_COLOR;
        });
    }
    public static int secondaryColor(ItemStack stack) {
        return 0xFF000000 | stack.getOrDefault(TheArtofStockings.SECONDARY_COLOR.get(), switch (style(stack)) {
            case FOX -> 0x180E0B;
            case STRIPED -> 0x17131D;
            case CAT_PAW -> 0xB94F78;
            default -> 0x659FE6;
        });
    }
    public static boolean twoColors(ItemStack stack) {
        return switch (style(stack)) { case GRADIENT, STRIPED, CAT_PAW, FOX -> true; default -> false; };
    }
    public static int opacity(ItemStack stack) { return Math.clamp(stack.getOrDefault(TheArtofStockings.OPACITY.get(),100),0,100); }
    public static int depth(ItemStack stack) { return Math.clamp(stack.getOrDefault(TheArtofStockings.DEPTH.get(),0),0,100); }
    public static int mix(int top, int bottom, float amount) {
        amount = Math.clamp(amount, 0, 1);
        int r = Math.round(((top >> 16) & 255) * (1-amount) + ((bottom >> 16) & 255) * amount);
        int g = Math.round(((top >> 8) & 255) * (1-amount) + ((bottom >> 8) & 255) * amount);
        int b = Math.round((top & 255) * (1-amount) + (bottom & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) StockingsActions.openSelector(player, hand, true);
            else StockingsActions.startEquip(player, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return stack.has(TheArtofStockings.STINKY.get())
                ? Component.translatable("item.theartofstockings.stinky_stockings", name) : name;
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if(StockingsHistory.wasWorn(stack))lines.add(Component.translatable("tooltip.theartofstockings.worn_before").withStyle(ChatFormatting.DARK_AQUA));
        if (stack.has(TheArtofStockings.STINKY.get())) {
            lines.add(Component.translatable("tooltip.theartofstockings.stinky_stockings").withStyle(ChatFormatting.DARK_GREEN));
        }
        lines.add(Component.translatable("tooltip.theartofstockings.use").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.stand").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.dye").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.palette").withStyle(ChatFormatting.AQUA));
        if (style == Style.GRADIENT) lines.add(Component.translatable("tooltip.theartofstockings.gradient",
                String.format("#%06X", color(stack)&0xFFFFFF), String.format("#%06X", secondaryColor(stack)&0xFFFFFF)).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (style == Style.GLOW) lines.add(Component.translatable("tooltip.theartofstockings.glow").withStyle(ChatFormatting.AQUA));
        if (twoColors(stack) && style != Style.GRADIENT) lines.add(Component.translatable("tooltip.theartofstockings.two_colors").withStyle(ChatFormatting.AQUA));
        if (style == Style.MATTE) lines.add(Component.translatable("tooltip.theartofstockings.matte").withStyle(ChatFormatting.GRAY));
        if (style == Style.CUSTOM) lines.add(Component.translatable("tooltip.theartofstockings.custom").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.theartofstockings.opacity",opacity(stack)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.depth",depth(stack)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.wear_parts").withStyle(ChatFormatting.AQUA));
        if (style == Style.BODYSTOCKING) lines.add(Component.translatable("tooltip.theartofstockings.bodystocking").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.theartofstockings.charm").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("tooltip.theartofstockings.exclusive").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
