package com.shouyun.theartofstockings;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Conversion and two-endpoint dyeing; normal solid-color redyeing uses vanilla armor dye. */
public final class StockingsStyleRecipe extends CustomRecipe {
    public StockingsStyleRecipe(CraftingBookCategory category) { super(category); }
    private record Input(ItemStack source, ItemStack material, List<DyeItem> dyes) {}
    private static Input parse(CraftingInput input) {
        ItemStack source = ItemStack.EMPTY, material = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();
        for (var stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (StockingsActions.isStockings(stack)) {
                if (!source.isEmpty()) return null;
                source = stack;
            } else if (stack.getItem() instanceof DyeItem dye) dyes.add(dye);
            else if (stack.is(Items.STRING) || stack.is(Items.SHEARS) || stack.is(Items.GLOW_INK_SAC) || stack.is(Items.AMETHYST_SHARD)) {
                if (!material.isEmpty()) return null;
                material = stack;
            } else return null;
        }
        if (source.isEmpty()) return null;
        if (!material.isEmpty()) return source.is(TheArtofStockings.STOCKINGS.get())
                && (!material.is(Items.AMETHYST_SHARD) || dyes.size() <= 2) ? new Input(source, material, dyes) : null;
        if (source.is(TheArtofStockings.GRADIENT.get()) && (dyes.size() == 1 || dyes.size() == 2))
            return new Input(source, material, dyes);
        return null;
    }
    @Override public boolean matches(CraftingInput input, Level level) { return parse(input) != null; }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Input p = parse(input);
        if (p == null) return ItemStack.EMPTY;
        if (p.material.isEmpty() || p.material.is(Items.AMETHYST_SHARD)) {
            var result = p.source.transmuteCopy(TheArtofStockings.GRADIENT.get(), 1);
            int top = p.dyes.size() == 2 ? p.dyes.get(0).getDyeColor().getTextureDiffuseColor() : StockingsItem.color(p.source);
            int bottom = p.dyes.isEmpty() ? 0x659FE6 : p.dyes.getLast().getDyeColor().getTextureDiffuseColor();
            result.set(DataComponents.DYED_COLOR, new DyedItemColor(top & 0xFFFFFF, true));
            result.set(TheArtofStockings.SECONDARY_COLOR.get(), bottom & 0xFFFFFF);
            return result;
        }
        var item = p.material.is(Items.STRING) ? TheArtofStockings.PANTYHOSE.get()
                : p.material.is(Items.SHEARS) ? TheArtofStockings.FISHNET.get() : TheArtofStockings.GLOW.get();
        var result = p.source.transmuteCopy(item, 1);
        result.remove(TheArtofStockings.SECONDARY_COLOR.get());
        return p.dyes.isEmpty() ? result : DyedItemColor.applyDyes(result, p.dyes);
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i=0; i<input.size(); i++) {
            var stack = input.getItem(i);
            // Shears are a reusable tailoring tool. Preserve damage, name and other components.
            if (stack.is(Items.SHEARS)) remaining.set(i, stack.copyWithCount(1));
            else if (stack.hasCraftingRemainingItem()) remaining.set(i, stack.getCraftingRemainingItem());
        }
        return remaining;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width*height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return TheArtofStockings.STYLE_RECIPE.get(); }
}
