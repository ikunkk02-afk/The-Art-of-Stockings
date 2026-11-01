package com.shouyun.theartofstockings;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(TheArtofStockings.MODID)
public final class TheArtofStockings {
    public static final String MODID = "theartofstockings";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    public static final Supplier<AttachmentType<ItemStack>> WORN_STOCKINGS = ATTACHMENTS.register("worn_stockings",
            () -> AttachmentType.builder(() -> ItemStack.EMPTY).serialize(ItemStack.OPTIONAL_CODEC, stack -> !stack.isEmpty())
                    .copyOnDeath().sync(ItemStack.OPTIONAL_STREAM_CODEC).build());
    public static final DeferredItem<StockingsItem> STOCKINGS = ITEMS.register("stockings",
            () -> new StockingsItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<StockingsItem> GRADIENT = clothing("gradient_stockings", StockingsItem.Style.GRADIENT);
    public static final DeferredItem<StockingsItem> PANTYHOSE = clothing("pantyhose", StockingsItem.Style.PANTYHOSE);
    public static final DeferredItem<StockingsItem> FISHNET = clothing("fishnet_stockings", StockingsItem.Style.FISHNET);
    public static final DeferredItem<StockingsItem> GLOW = clothing("glow_stockings", StockingsItem.Style.GLOW);
    public static final DeferredItem<PaletteItem> PALETTE = ITEMS.register("palette", () -> new PaletteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final Supplier<DataComponentType<Integer>> SECONDARY_COLOR = COMPONENTS.register("secondary_color",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 0xFFFFFF)).networkSynchronized(ByteBufCodecs.INT).build());
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final Supplier<SimpleCraftingRecipeSerializer<StockingsStyleRecipe>> STYLE_RECIPE = RECIPES.register("stockings_style",
            () -> new SimpleCraftingRecipeSerializer<>(StockingsStyleRecipe::new));
    private static DeferredItem<StockingsItem> clothing(String id, StockingsItem.Style style) {
        return ITEMS.register(id, () -> new StockingsItem(new Item.Properties().stacksTo(1), style));
    }
    public TheArtofStockings(IEventBus bus) {
        ITEMS.register(bus);
        ATTACHMENTS.register(bus);
        COMPONENTS.register(bus);
        RECIPES.register(bus);
        bus.addListener(StockingsNetwork::register);
        bus.addListener(this::creativeItems);
    }
    private void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(STOCKINGS);
            event.accept(PALETTE);
            event.accept(GRADIENT); event.accept(PANTYHOSE); event.accept(FISHNET); event.accept(GLOW);
            for (DyeColor color : DyeColor.values()) {
                ItemStack stack = STOCKINGS.toStack();
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor(), true));
                event.accept(stack);
            }
        }
    }
}
