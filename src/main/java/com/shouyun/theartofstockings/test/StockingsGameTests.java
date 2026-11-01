package com.shouyun.theartofstockings.test;

import com.shouyun.theartofstockings.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dev-only tests; excluded from the distributable jar. */
@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class StockingsGameTests {
    private static Player player(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setPos(h.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        return p;
    }
    private static void tick(Player p) { StockingsActions.tick(new PlayerTickEvent.Pre(p)); }
    private static void remove(Player p) {
        // Start with an empty main hand, then allow pickups to refill it during the animation.
        ItemStack held = p.getMainHandItem();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        StockingsActions.requestRemoval(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
    }
    private static ItemStack stockings() { return TheArtofStockings.STOCKINGS.toStack(); }

    private static ItemStack craft(GameTestHelper h, ItemStack... stacks) {
        var items=new java.util.ArrayList<>(List.of(stacks));
        while(items.size()<9) items.add(ItemStack.EMPTY);
        var input=CraftingInput.of(3,3,items);
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
        return recipe.value().assemble(input,h.getLevel().registryAccess());
    }
    @GameTest(template="empty") public static void gradientEndpointsAndRedye(GameTestHelper h) {
        var original=stockings();original.set(DataComponents.CUSTOM_NAME,Component.literal("Two colors"));
        var gradient=craft(h,original,new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.RED_DYE),new ItemStack(Items.BLUE_DYE));
        h.assertTrue(gradient.is(TheArtofStockings.GRADIENT.get()),"Amethyst and dyes create gradient");
        h.assertTrue(StockingsItem.color(gradient)==(0xFF000000|net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor()),"First dye is top");
        h.assertTrue(StockingsItem.secondaryColor(gradient)==(0xFF000000|net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor()),"Second dye is toe");
        var two=craft(h,gradient,new ItemStack(Items.LIME_DYE),new ItemStack(Items.PINK_DYE));
        h.assertTrue(StockingsItem.color(two)==net.minecraft.world.item.DyeColor.LIME.getTextureDiffuseColor(),"Redye replaces top");
        var one=craft(h,two,new ItemStack(Items.YELLOW_DYE));
        h.assertTrue(StockingsItem.color(one)==StockingsItem.color(two),"One dye preserves top");
        h.assertTrue(StockingsItem.secondaryColor(one)==net.minecraft.world.item.DyeColor.YELLOW.getTextureDiffuseColor(),"One dye sets toe");
        h.assertTrue(one.getHoverName().getString().equals("Two colors") && !original.has(DataComponents.DYED_COLOR),"Name retained; source unchanged");
        var ordinary=craft(h,stockings(),new ItemStack(Items.RED_DYE),new ItemStack(Items.BLUE_DYE));
        h.assertTrue(ordinary.is(TheArtofStockings.STOCKINGS.get()),"Existing solid mixing recipe is unambiguous");
        h.assertTrue(StockingsItem.mix(0xFF000000,0xFFFFFFFF,.5F)==0xFF808080,"Gradient interpolates RGB channels");
        h.succeed();
    }
    @GameTest(template="empty") public static void allStylesCraftDyeAndToolRemainder(GameTestHelper h) {
        var materials=List.of(Items.STRING,Items.SHEARS,Items.GLOW_INK_SAC);
        var targets=List.of(TheArtofStockings.PANTYHOSE.get(),TheArtofStockings.FISHNET.get(),TheArtofStockings.GLOW.get());
        for(int i=0;i<materials.size();i++) {
            var source=stockings();source.set(DataComponents.CUSTOM_NAME,Component.literal("Tailored"));
            var pair=craft(h,source,new ItemStack(materials.get(i)),new ItemStack(Items.CYAN_DYE));
            h.assertTrue(pair.is(targets.get(i)) && pair.has(DataComponents.DYED_COLOR),"Each upgrade supports dye in same recipe");
            var redyed=craft(h,pair,new ItemStack(Items.RED_DYE),new ItemStack(Items.WHITE_DYE));
            h.assertTrue(redyed.is(targets.get(i)) && StockingsItem.color(redyed)!=StockingsItem.color(pair),"Every solid variant mixes and redyes");
            h.assertTrue(redyed.getHoverName().getString().equals("Tailored"),"Conversion and redye keep name");
        }
        var shears=new ItemStack(Items.SHEARS);shears.setDamageValue(7);
        var input=CraftingInput.of(2,1,List.of(stockings(),shears));
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
        h.assertTrue(ItemStack.matches(recipe.value().getRemainingItems(input).get(1),shears),"Tailoring returns exact reusable shears");
        var invalid=CraftingInput.of(3,1,List.of(stockings(),stockings(),new ItemStack(Items.GLOW_INK_SAC)));
        h.assertTrue(!new StockingsStyleRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.MISC).matches(invalid,h.getLevel()),"Never consume two pairs");
        h.succeed();
    }
    @GameTest(template="empty") public static void paletteValidationAndSave(GameTestHelper h) {
        var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        var pair=TheArtofStockings.GRADIENT.toStack();p.setItemInHand(InteractionHand.OFF_HAND,pair);
        var before=pair.copy();
        h.assertTrue(PaletteItem.apply(p,40,before,0x123456,0xEEDDCC),"Palette sets arbitrary RGB endpoints");
        h.assertTrue(StockingsItem.color(pair)==0xFF123456 && StockingsItem.secondaryColor(pair)==0xFFEEDDCC,"Both arbitrary colors saved exactly");
        h.assertTrue(!PaletteItem.apply(p,40,before,0,0),"Stale request cannot overwrite changed pair");
        h.assertTrue(!PaletteItem.apply(p,-2,pair.copy(),0,0) && !PaletteItem.apply(p,90,pair.copy(),0,0),"Reject invalid target slots");
        h.assertTrue(!PaletteItem.apply(p,40,pair.copy(),-1,0),"Reject colors outside RGB range");
        var loaded=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)pair.save(h.getLevel().registryAccess()));
        h.assertTrue(ItemStack.matches(pair,loaded),"Both endpoints survive save/reload");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(!PaletteItem.apply(p,40,pair.copy(),0,0),"Cannot recolor without holding palette");
        p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        StockingsActions.startEquip(p,InteractionHand.OFF_HAND);
        h.assertTrue(!PaletteItem.apply(p,40,pair.copy(),0,0),"Cannot change colors during dressing transaction");
        StockingsActions.cancel(p);h.succeed();
    }
    @GameTest(template="empty") public static void variantStandPersistenceAndNetwork(GameTestHelper h) {
        var variants=List.of(TheArtofStockings.GRADIENT,TheArtofStockings.PANTYHOSE,TheArtofStockings.FISHNET,TheArtofStockings.GLOW);
        for(var item:variants) {
            var pair=item.toStack();pair.set(DataComponents.DYED_COLOR,new DyedItemColor(0x3399AA,true));
            pair.set(TheArtofStockings.SECONDARY_COLOR.get(),0xFF7700);
            var stand=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),0,0,0);
            var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,pair.copy());
            stand.interactAt(p,new net.minecraft.world.phys.Vec3(0,.7,0),InteractionHand.MAIN_HAND);
            h.assertTrue(ItemStack.matches(stand.getItemBySlot(EquipmentSlot.LEGS),pair),"All variants place on armor stands");
            var loaded=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),0,0,0);
            loaded.load(stand.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(ItemStack.matches(loaded.getItemBySlot(EquipmentSlot.LEGS),pair),"Stand retains variant and endpoints");
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                var packet=new StockingsNetwork.Animation(12,true,pair,17,true);
                StockingsNetwork.Animation.CODEC.encode(buf,packet);
                var decoded=StockingsNetwork.Animation.CODEC.decode(buf);
                h.assertTrue(ItemStack.matches(decoded.stack(),pair) && decoded.elapsed()==17,"Animation sync retains style and both colors");
            } finally { buf.release(); }
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void variantBlockbenchAssets(GameTestHelper h) throws Exception {
        for(String name:List.of("gradient_stockings","pantyhose","fishnet_stockings","glow_stockings")) {
            try(var stream=StockingsGameTests.class.getResourceAsStream("/assets/theartofstockings/geo/"+name+".geo.json")) {
                var geo=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject()
                        .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                boolean hips=false;int cubes=0;
                for(var entry:geo.getAsJsonArray("bones")) {
                    var bone=entry.getAsJsonObject();if(bone.get("name").getAsString().equals("tights_hips"))hips=true;
                    if(bone.has("cubes"))cubes+=bone.getAsJsonArray("cubes").size();
                }
                h.assertTrue(cubes==(name.equals("pantyhose")?10:12),"Runtime geometry matches authored Blockbench model");
                h.assertTrue(hips==name.equals("pantyhose"),"Only pantyhose has joined waist geometry");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingAndDye(GameTestHelper h) {
        var s = new ItemStack(Items.STRING);
        var input = CraftingInput.of(3, 3, List.of(s, ItemStack.EMPTY, s, s, ItemStack.EMPTY, s, s, ItemStack.EMPTY, s));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        ItemStack result = recipe.value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(StockingsActions.isStockings(result), "Six strings must craft stockings");
        result.set(DataComponents.CUSTOM_NAME, Component.literal("Keep my name"));
        var dyeInput = CraftingInput.of(2, 2, List.of(result, new ItemStack(Items.RED_DYE), new ItemStack(Items.BLUE_DYE), ItemStack.EMPTY));
        var dyeRecipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, dyeInput, h.getLevel()).orElseThrow();
        var dyed = dyeRecipe.value().assemble(dyeInput, h.getLevel().registryAccess());
        h.assertTrue(dyed.has(DataComponents.DYED_COLOR), "Dye component must be set by vanilla recipe");
        h.assertTrue(dyed.getHoverName().getString().equals("Keep my name"), "Dye preserves custom name");
        h.assertTrue(!result.has(DataComponents.DYED_COLOR), "Dye must not mutate input");
        var loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag)dyed.save(h.getLevel().registryAccess()));
        h.assertTrue(ItemStack.matches(loaded, dyed), "Save/reload must preserve dye and name");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void animatedRoundTrip(GameTestHelper h) {
        Player p = player(h);
        ItemStack original = stockings();
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0xD579B2, true));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Named pair"));
        ItemStack expected = original.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, original);
        TheArtofStockings.STOCKINGS.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(!StockingsActions.wearing(p), "Equip waits for animation");
        h.runAfterDelay(71, () -> {
            tick(p);
            h.assertTrue(!StockingsActions.wearing(p), "Cannot equip before 72 ticks");
        });
        h.runAfterDelay(73, () -> {
            tick(p);
            h.assertTrue(ItemStack.matches(StockingsActions.worn(p), expected), "Equip preserves metadata");
            h.assertTrue(p.getMainHandItem().isEmpty(), "Exactly one pair transferred");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && p.inventoryMenu.getSlot(7).mayPickup(p), "Stockings leave armor slot unlocked");
            remove(p);
            h.assertTrue(StockingsActions.wearing(p), "Removal waits for animation");
        });
        h.runAfterDelay(146, () -> {
            tick(p);
            h.assertTrue(!StockingsActions.wearing(p), "Removed after animation");
            h.assertTrue(p.getInventory().items.stream().filter(s -> ItemStack.matches(s, expected)).count() == 1, "Return one exact pair");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void cancellationDoesNotDuplicate(GameTestHelper h) {
        Player p = player(h);
        ItemStack source = stockings();
        p.setItemInHand(InteractionHand.MAIN_HAND, source);
        TheArtofStockings.STOCKINGS.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        p.getInventory().selected = 1;
        tick(p);
        h.assertTrue(!StockingsActions.busy(p) && !StockingsActions.wearing(p), "Switching slots cancels equip");
        h.assertTrue(p.getInventory().getItem(0) == source && source.getCount() == 1, "Original stack untouched");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leggingsRemainIndependent(GameTestHelper h) {
        Player p=player(h);
        var pair=TheArtofStockings.GRADIENT.toStack();
        pair.set(TheArtofStockings.SECONDARY_COLOR.get(),0x123456);
        StockingsWear.set(p,pair);
        for(int i=0;i<4;i++) {
            var armor=List.of(Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS).get(i);
            h.assertTrue(p.inventoryMenu.getSlot(5+i).mayPlace(new ItemStack(armor)),"All armor slots accept armor while wearing stockings");
        }
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_LEGGINGS));
        Items.DIAMOND_LEGGINGS.use(p.level(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS),"Right-click equips leggings over stockings");
        h.assertTrue(ItemStack.matches(StockingsActions.worn(p),pair),"Leggings preserve exact worn pair");
        h.assertTrue(p.inventoryMenu.getSlot(7).mayPickup(p),"Leggings may be taken off normally");
        p.inventoryMenu.quickMoveStack(p,7);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && StockingsActions.wearing(p),"Shift-removing leggings retains stockings");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        p.getInventory().setItem(9,new ItemStack(Items.IRON_LEGGINGS));
        p.inventoryMenu.quickMoveStack(p,9);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS),"Shift-click equips leggings over stockings");
        h.assertTrue(!p.inventoryMenu.getSlot(7).mayPlace(stockings()),"Stockings still require the animated clothing action");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=180)
    public static void dressingAndUndressingWithLeggings(GameTestHelper h) {
        var p=player(h);
        var pair=TheArtofStockings.GRADIENT.toStack();
        pair.set(DataComponents.DYED_COLOR,new DyedItemColor(0x123456,true));
        pair.set(TheArtofStockings.SECONDARY_COLOR.get(),0xABCDEF);
        var expected=pair.copy();
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemInHand(InteractionHand.MAIN_HAND,pair);
        StockingsActions.startEquip(p,InteractionHand.MAIN_HAND);
        h.assertTrue(StockingsActions.busy(p),"Stockings may be put on while wearing leggings");
        h.assertTrue(p.inventoryMenu.getSlot(7).mayPlace(new ItemStack(Items.IRON_LEGGINGS)),"Armor slot stays unlocked during dressing");
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));
        tick(p);
        h.assertTrue(StockingsActions.busy(p),"Changing leggings does not cancel dressing");
        h.runAfterDelay(73,()->{
            tick(p);
            h.assertTrue(ItemStack.matches(StockingsActions.worn(p),expected),"Dressing keeps the full gradient stack separately");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS),"Dressing never replaces leggings");
            remove(p);
            p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.NETHERITE_LEGGINGS));
        });
        h.runAfterDelay(146,()->{
            tick(p);
            h.assertTrue(!StockingsActions.wearing(p),"Removal succeeds through leggings");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS),"Removal never touches leggings");
            h.assertTrue(p.getInventory().items.stream().filter(s->ItemStack.matches(s,expected)).count()==1,"Return one exact gradient pair");
            h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void clothingSaveReloadAndClone(GameTestHelper h) {
        var p=player(h);var pair=TheArtofStockings.GRADIENT.toStack();
        pair.set(TheArtofStockings.SECONDARY_COLOR.get(),0x112233);
        pair.set(DataComponents.CUSTOM_NAME,Component.literal("Persistent clothing"));
        StockingsWear.set(p,pair);
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
        var loaded=player(h);loaded.load(p.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(ItemStack.matches(StockingsWear.get(loaded),pair),"Separate pair and all components survive player save/reload");
        h.assertTrue(loaded.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS),"Leggings are saved independently");
        var clone=player(h);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(clone,p,false));
        h.assertTrue(ItemStack.matches(StockingsWear.get(clone),pair),"End-return clone retains clothing");
        h.assertTrue(StockingsWear.get(clone)!=StockingsWear.get(p),"Clone owns an independent stack");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void legacySlotMigration(GameTestHelper h) {
        var p=player(h);var pair=TheArtofStockings.FISHNET.toStack();
        pair.set(DataComponents.CUSTOM_NAME,Component.literal("Legacy pair"));
        p.setItemSlot(EquipmentSlot.LEGS,pair.copy());
        StockingsWear.migrateLegacy(p);StockingsWear.migrateLegacy(p);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),"Legacy leg slot becomes available");
        h.assertTrue(ItemStack.matches(StockingsWear.get(p),pair),"Migration preserves full pair");
        h.assertTrue(p.getInventory().countItem(TheArtofStockings.FISHNET.get())==0,"Migration does not duplicate the pair into inventory");
        var extra=stockings();p.setItemSlot(EquipmentSlot.LEGS,extra.copy());
        StockingsWear.migrateLegacy(p);StockingsWear.migrateLegacy(p);
        h.assertTrue(ItemStack.matches(StockingsWear.get(p),pair),"Existing independent pair is never overwritten");
        h.assertTrue(p.getInventory().countItem(TheArtofStockings.STOCKINGS.get())==1,"Conflicting legacy pair returns once to inventory");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void wornPaletteWithLeggings(GameTestHelper h) {
        var p=player(h);StockingsWear.set(p,TheArtofStockings.GRADIENT.toStack());
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        var old=StockingsWear.get(p).copy();
        h.assertTrue(PaletteItem.apply(p,PaletteItem.WORN_SLOT,old,0x112233,0xAABBCC),"Palette edits independent worn pair");
        h.assertTrue(StockingsItem.color(StockingsWear.get(p))==0xFF112233 && StockingsItem.secondaryColor(StockingsWear.get(p))==0xFFAABBCC,"Both endpoints applied to worn pair");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS) && !p.getItemBySlot(EquipmentSlot.LEGS).has(DataComponents.DYED_COLOR),"Worn palette never dyes leggings");
        h.assertTrue(!PaletteItem.apply(p,PaletteItem.WORN_SLOT,old,0,0),"Reject stale worn palette request");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void clothingDeathDropAndKeepInventory(GameTestHelper h) {
        var rule=h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        boolean previous=rule.get();
        try {
            rule.set(false,h.getLevel().getServer());
            var p=player(h);var pair=TheArtofStockings.GLOW.toStack();
            pair.set(DataComponents.CUSTOM_NAME,Component.literal("Death drop test"));StockingsWear.set(p,pair);
            p.die(p.damageSources().generic());
            h.assertTrue(StockingsWear.get(p).isEmpty(),"Death clears the dropped clothing attachment");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(2),e->ItemStack.matches(e.getItem(),pair));
            h.assertTrue(drops.size()==1,"Vanilla death flow drops one exact pair");
            var deadClone=player(h);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(deadClone,p,true));
            h.assertTrue(StockingsWear.get(deadClone).isEmpty(),"Respawn cannot duplicate a dropped pair");
            rule.set(true,h.getLevel().getServer());
            var kept=player(h);StockingsWear.set(kept,pair);kept.die(kept.damageSources().generic());
            var keptClone=player(h);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(keptClone,kept,true));
            h.assertTrue(ItemStack.matches(StockingsWear.get(keptClone),pair),"Keep-inventory respawn retains clothing");
            var after=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(2),e->ItemStack.matches(e.getItem(),pair));
            h.assertTrue(after.size()==1,"Keep-inventory death creates no additional drop");
        } finally { rule.set(previous,h.getLevel().getServer()); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void otherArmorSurvivesFullRoundTrip(GameTestHelper h) {
        Player p = player(h);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        p.setItemInHand(InteractionHand.MAIN_HAND, stockings());
        TheArtofStockings.STOCKINGS.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(StockingsActions.busy(p), "Helmet, elytra and boots allow dressing");
        h.assertTrue(p.inventoryMenu.getSlot(6).mayPlace(new ItemStack(Items.DIAMOND_CHESTPLATE)), "Chest slot stays unlocked during animation");
        h.runAfterDelay(73, () -> {
            tick(p);
            h.assertTrue(StockingsActions.wearing(p), "Equipped with other armor");
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            tick(p);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "Chestplate is not ejected");
            remove(p);
        });
        h.runAfterDelay(146, () -> {
            tick(p);
            h.assertTrue(!StockingsActions.wearing(p), "Removed stockings");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "Helmet remains equipped");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "Chestplate remains equipped");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS), "Boots remain equipped");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockbenchAnimationContract(GameTestHelper h) throws Exception {
        try (var stream = StockingsGameTests.class.getResourceAsStream("/assets/theartofstockings/animations/stockings.animation.json")) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            var on = new AnimationClip(json.getAsJsonObject("animation.stockings.put_on"));
            var off = new AnimationClip(json.getAsJsonObject("animation.stockings.take_off"));
            h.assertTrue(Math.abs(on.length*20-StockingsActions.DURATION) < .01F, "Server duration matches Blockbench clip");
            h.assertTrue(on.sample("right_pull", "scale", 1.7F).y() > .99F && on.sample("left_pull", "scale", 1.7F).y() < .01F,
                    "Right stocking precedes left stocking");
            for (String side : List.of("right_pull", "left_pull")) {
                float prevOn = -1, prevOff = 2;
                for (int i=0; i<=360; i++) {
                    float a=on.sample(side,"scale",i/100F).y(), b=off.sample(side,"scale",i/100F).y();
                    h.assertTrue(a>=prevOn && a>=0 && a<=1 && b<=prevOff && b>=0 && b<=1, "Cloth never overshoots or reverses");
                    prevOn=a;prevOff=b;
                }
                h.assertTrue(prevOn==1 && prevOff==0,"Clips complete the cloth transfer");
            }
            for (var clip : List.of(on,off)) for (var bone : List.of("body","head","right_arm","left_arm","right_leg","left_leg")) {
                h.assertTrue(clip.sample(bone,"rotation",0).equals(AnimationClip.Vector.ZERO),"Neutral start pose");
                h.assertTrue(clip.sample(bone,"rotation",clip.length).equals(AnimationClip.Vector.ZERO),"Neutral end pose");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullInventoryDropsRemovedPair(GameTestHelper h) {
        Player p = player(h);
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        StockingsWear.set(p, stockings());
        remove(p);
        h.runAfterDelay(73, () -> {
            tick(p);
            h.assertTrue(!StockingsActions.wearing(p), "Remove succeeds with full inventory");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2), e -> StockingsActions.isStockings(e.getItem()));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "One pair safely dropped");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void offhandAndCreativeTransfer(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.CREATIVE);
        p.setItemInHand(InteractionHand.OFF_HAND, stockings());
        TheArtofStockings.STOCKINGS.get().use(p.level(), p, InteractionHand.OFF_HAND);
        h.runAfterDelay(73, () -> {
            tick(p);
            h.assertTrue(StockingsActions.wearing(p) && p.getOffhandItem().isEmpty(), "Creative offhand equips without duplicating");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void deathCancelsAction(GameTestHelper h) {
        Player p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, stockings());
        TheArtofStockings.STOCKINGS.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        p.setHealth(0);
        tick(p);
        h.assertTrue(!StockingsActions.busy(p) && !StockingsActions.wearing(p), "Dead players cannot finish equipping");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "Cancellation preserves original stack");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void creativeOverflowDoesNotDelete(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.CREATIVE);
        p.getAbilities().instabuild = true;
        p.setPos(h.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        ItemStack pair = stockings();
        pair.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456, true));
        StockingsWear.set(p, pair);
        remove(p);
        h.runAfterDelay(73, () -> {
            tick(p);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2),
                    e -> StockingsActions.isStockings(e.getItem()) && StockingsItem.color(e.getItem()) == 0xFF123456);
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "Creative overflow preserves dyed pair");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void removalRequiresSneakingAndEmptyHand(GameTestHelper h) {
        Player p = player(h);
        StockingsWear.set(p, stockings());
        StockingsActions.requestRemoval(p);
        h.assertTrue(!StockingsActions.busy(p), "Ordinary empty right-click does not undress");
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        StockingsActions.requestRemoval(p);
        h.assertTrue(!StockingsActions.busy(p), "Holding an item rejects removal packet");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var pos = p.blockPosition();
        var event = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.UP, pos, false));
        StockingsActions.removeWhileTargetingBlock(event);
        h.assertTrue(event.isCanceled() && StockingsActions.busy(p), "Sneak right-click on a block starts removal, without using block");
        StockingsActions.cancel(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorStandPlacementRemovalAndSave(GameTestHelper h) {
        Player p = player(h);
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(), 0, 0, 0);
        ItemStack pair = stockings();
        pair.set(DataComponents.DYED_COLOR, new DyedItemColor(0x982F68, true));
        pair.set(DataComponents.CUSTOM_NAME, Component.literal("Display pair"));
        ItemStack expected = pair.copy();
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        p.setItemInHand(InteractionHand.MAIN_HAND, pair);
        stand.interactAt(p, new net.minecraft.world.phys.Vec3(0, .7, 0), InteractionHand.MAIN_HAND);
        h.assertTrue(ItemStack.matches(stand.getItemBySlot(EquipmentSlot.LEGS), expected), "Vanilla right-click equips dyed stockings on stand");
        h.assertTrue(p.getMainHandItem().isEmpty(), "Survival placement transfers exactly one pair");
        h.assertTrue(!StockingsActions.busy(p), "Stand interaction does not dress player");
        var saved = stand.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var loaded = new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(), 0, 0, 0);
        loaded.load(saved);
        h.assertTrue(ItemStack.matches(loaded.getItemBySlot(EquipmentSlot.LEGS), expected), "Stand save retains color and name");
        loaded.interactAt(p, new net.minecraft.world.phys.Vec3(0, .7, 0), InteractionHand.MAIN_HAND);
        h.assertTrue(loaded.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && ItemStack.matches(p.getMainHandItem(), expected), "Empty right-click retrieves exact pair");
        h.assertTrue(loaded.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                && loaded.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
                && loaded.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS), "Other stand equipment remains intact");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorStandLocksAndCreativeSemantics(GameTestHelper h) {
        Player p = player(h);
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(), 0, 0, 0);
        var nbt = new net.minecraft.nbt.CompoundTag();
        nbt.putInt("DisabledSlots", 1 << EquipmentSlot.LEGS.getFilterFlag());
        stand.readAdditionalSaveData(nbt);
        p.setItemInHand(InteractionHand.MAIN_HAND, stockings());
        stand.interactAt(p, new net.minecraft.world.phys.Vec3(0, .7, 0), InteractionHand.MAIN_HAND);
        h.assertTrue(!StockingsActions.wearing(stand) && p.getMainHandItem().getCount()==1, "Locked stand refuses placement without consuming item");
        nbt.putInt("DisabledSlots", 0);stand.readAdditionalSaveData(nbt);
        p.getAbilities().instabuild=true;
        stand.interactAt(p, new net.minecraft.world.phys.Vec3(0, .7, 0), InteractionHand.MAIN_HAND);
        h.assertTrue(StockingsActions.wearing(stand) && p.getMainHandItem().getCount()==1, "Creative placement follows vanilla copy semantics");
        h.succeed();
    }
}
