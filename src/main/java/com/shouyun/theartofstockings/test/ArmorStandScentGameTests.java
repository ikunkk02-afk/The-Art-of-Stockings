package com.shouyun.theartofstockings.test;

import com.shouyun.theartofstockings.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorStandScentGameTests {
    private static Player viewer(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(h.absoluteVec(new Vec3(1.5,1,1.5)));
        p.setShiftKeyDown(true);p.setPose(Pose.CROUCHING);return p;
    }
    private static ItemStack used() { var pair=TheArtofStockings.CAT_PAW.toStack();StockingsHistory.mark(pair);return pair; }
    private static ArmorStand stand(GameTestHelper h,ItemStack pair) {
        var stand=h.spawn(EntityType.ARMOR_STAND,new BlockPos(1,1,3));stand.setNoGravity(true);
        stand.setPos(h.absoluteVec(new Vec3(1.5,1,3)));stand.setItemSlot(EquipmentSlot.LEGS,pair.copy());return stand;
    }
    private static void aim(Player p,ArmorStand stand) {
        var delta=stand.position().add(0,stand.isSmall()?.125:.25,0).subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
    }
    @GameTest(template="empty") public static void historySurvivesRealStandTransferSaveNetworkAndTailoring(GameTestHelper h) {
        var p=viewer(h);var pair=TheArtofStockings.STOCKINGS.toStack();
        pair.set(DataComponents.CUSTOM_NAME,Component.literal("Recorded pair"));pair.set(DataComponents.DYED_COLOR,new DyedItemColor(0x56284D,true));
        pair.set(TheArtofStockings.DEPTH.get(),71);pair.set(TheArtofStockings.OPACITY.get(),94);
        StockingsWear.set(p,pair);StockingsHistory.record(p);var worn=StockingsWear.get(p).copy();
        h.assertTrue(worn.has(TheArtofStockings.WORN_BEFORE.get()) && !pair.has(TheArtofStockings.WORN_BEFORE.get()),"Actual wear is recorded on the owned stack without mutating unrelated source copies");
        var expected=worn.copy();StockingsWear.set(p,ItemStack.EMPTY);p.setItemInHand(InteractionHand.MAIN_HAND,worn);
        var stand=stand(h,ItemStack.EMPTY);stand.interactAt(p,new Vec3(0,.7,0),InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().isEmpty() && ItemStack.matches(stand.getItemBySlot(EquipmentSlot.LEGS),expected),"Vanilla placement moves the recorded pair");
        var reloaded=new ArmorStand(h.getLevel(),0,0,0);reloaded.load(stand.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        var saved=reloaded.getItemBySlot(EquipmentSlot.LEGS);
        h.assertTrue(StockingsHistory.wasWorn(saved) && saved.getHoverName().getString().equals("Recorded pair") && StockingsItem.depth(saved)==71,"Stand save retains history, name and appearance");
        var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buf,saved);h.assertTrue(ItemStack.matches(ItemStack.STREAM_CODEC.decode(buf),saved),"History survives item sync"); }
        finally { buf.release(); }
        reloaded.interactAt(p,new Vec3(0,.7,0),InteractionHand.MAIN_HAND);
        h.assertTrue(StockingsHistory.wasWorn(p.getMainHandItem()),"Taking the item back preserves its history");
        var input=CraftingInput.of(2,1,List.of(saved.copy(),new ItemStack(Items.LEATHER)));
        var converted=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input,h.getLevel().registryAccess());
        h.assertTrue(converted.is(TheArtofStockings.BODYSTOCKING.get()) && StockingsHistory.wasWorn(converted),"Tailoring preserves prior wear");h.succeed();
    }
    @GameTest(template="empty") public static void cancelledEquipDoesNotCreateWearHistory(GameTestHelper h) {
        var p=viewer(h);var pair=TheArtofStockings.STOCKINGS.toStack();p.setItemInHand(InteractionHand.MAIN_HAND,pair);
        StockingsActions.startEquip(p,InteractionHand.MAIN_HAND);StockingsHistory.record(p);
        h.assertTrue(!StockingsHistory.wasWorn(pair),"Starting or holding a new pair does not count as completed wear");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));StockingsActions.tick(new PlayerTickEvent.Pre(p));
        h.assertTrue(!StockingsActions.busy(p) && !StockingsHistory.wasWorn(pair),"Cancelled dressing leaves the pair new");h.succeed();
    }
    @GameTest(template="empty") public static void allActualClothingSlotsRecordedButCosmeticsExcluded(GameTestHelper h) {
        var p=ModdedTestPlayers.create(h);
        try {
            for(var slot:WearSlot.values()) {
                StockingsWear.set(p,slot,TheArtofStockings.STOCKINGS.toStack());p.doTick();
                h.assertTrue(StockingsWear.get(p,slot).has(TheArtofStockings.WORN_BEFORE.get()),"Real wear records history for "+slot);
                StockingsWear.set(p,slot,ItemStack.EMPTY);
            }
            if(StockingsWear.accessoriesLoaded())CosmeticHistory.verify(h,p);
        } finally { for(var slot:WearSlot.values())StockingsWear.set(p,slot,ItemStack.EMPTY);h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="empty") public static void armorStandEligibilityAndObstructions(GameTestHelper h) {
        var p=viewer(h);var stand=stand(h,TheArtofStockings.STOCKINGS.toStack());aim(p,stand);
        h.assertTrue(!Excitement.canSmell(p,stand),"New stockings displayed on a stand are ineligible");
        stand.setItemSlot(EquipmentSlot.LEGS,used());h.assertTrue(Excitement.canSmell(p,stand),"Previously worn stockings qualify");
        p.setShiftKeyDown(false);h.assertTrue(!Excitement.canSmell(p,stand),"Standing cannot sniff");p.setShiftKeyDown(true);
        p.setXRot(-60);h.assertTrue(!Excitement.canSmell(p,stand),"Aim must point to the stockings");aim(p,stand);
        h.setBlock(new BlockPos(1,1,2),Blocks.STONE);h.setBlock(new BlockPos(1,2,2),Blocks.STONE);
        h.assertTrue(!Excitement.canSmell(p,stand),"Walls block stand sniffing");
        h.setBlock(new BlockPos(1,1,2),Blocks.AIR);h.setBlock(new BlockPos(1,2,2),Blocks.AIR);
        var tag=stand.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putBoolean("Small",true);stand.load(tag);aim(p,stand);
        h.assertTrue(Excitement.canSmell(p,stand),"Small stands use scaled stockings bounds");
        tag=stand.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putBoolean("Marker",true);stand.load(tag);
        h.assertTrue(!Excitement.canSmell(p,stand),"Marker-only stands do not qualify");
        var legacy=TheArtofStockings.FOX.toStack();legacy.set(TheArtofStockings.STINKY.get(),net.minecraft.util.Unit.INSTANCE);
        h.assertTrue(StockingsHistory.wasWorn(legacy),"Older stinky pairs retain evidence of prior wear");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void standSniffGrantsThirtySecondsWithCooldown(GameTestHelper h) {
        var p=viewer(h);var stand=stand(h,used());aim(p,stand);
        h.onEachTick(()->{aim(p,stand);Excitement.update(p,stand);});
        h.runAtTickTime(15,()->h.assertTrue(!p.hasEffect(Excitement.EFFECT),"A brief sniff does not grant the buff"));
        h.runAtTickTime(35,()->{
            h.assertTrue(p.hasEffect(Excitement.EFFECT) && p.getEffect(Excitement.EFFECT).getDuration()==600,"A sustained stand sniff grants exactly thirty seconds");
            p.removeEffect(Excitement.EFFECT);
        });
        h.runAtTickTime(70,()->{h.assertTrue(!p.hasEffect(Excitement.EFFECT),"The same cooldown applies to stands");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void swappingDisplayedPairRestartsSniff(GameTestHelper h) {
        var p=viewer(h);var stand=stand(h,used());aim(p,stand);
        h.onEachTick(()->{aim(p,stand);Excitement.update(p,stand);});
        h.runAtTickTime(20,()->{var replacement=used();replacement.set(DataComponents.CUSTOM_NAME,Component.literal("Other pair"));stand.setItemSlot(EquipmentSlot.LEGS,replacement);});
        h.runAtTickTime(38,()->h.assertTrue(!p.hasEffect(Excitement.EFFECT),"Different pairs on the same stand cannot combine sniff progress"));
        h.runAtTickTime(55,()->{h.assertTrue(p.hasEffect(Excitement.EFFECT),"A complete sniff of the replacement succeeds");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void removingDisplayedPairBreaksSniff(GameTestHelper h) {
        var p=viewer(h);var stand=stand(h,used());aim(p,stand);
        h.onEachTick(()->{aim(p,stand);Excitement.update(p,stand);});
        h.runAtTickTime(20,()->stand.setItemSlot(EquipmentSlot.LEGS,ItemStack.EMPTY));
        h.runAtTickTime(25,()->stand.setItemSlot(EquipmentSlot.LEGS,used()));
        h.runAtTickTime(45,()->h.assertTrue(!p.hasEffect(Excitement.EFFECT),"Taking away the stockings clears partial progress"));
        h.runAtTickTime(61,()->{h.assertTrue(p.hasEffect(Excitement.EFFECT),"Replacing a used pair permits a fresh complete sniff");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=240) public static void actualWearRemoveStandAndBothPlayersSniff(GameTestHelper h) {
        var owner=ModdedTestPlayers.create(h);var other=ModdedTestPlayers.create(h);var stand=stand(h,ItemStack.EMPTY);
        for(var p:List.of(owner,other)) {
            p.setPos(h.absoluteVec(new Vec3(1.5,1,1.5)));p.setNoGravity(true);p.setInvulnerable(true);
        }
        owner.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.CAT_PAW.toStack());StockingsActions.startEquip(owner,InteractionHand.MAIN_HAND);
        h.runAtTickTime(73,()->{
            StockingsActions.tick(new PlayerTickEvent.Pre(owner));
            h.assertTrue(StockingsWear.get(owner).has(TheArtofStockings.WORN_BEFORE.get()),"A real completed wear action records prior use immediately");
            owner.setShiftKeyDown(true);StockingsActions.requestRemoval(owner);
        });
        h.runAtTickTime(146,()->{
            StockingsActions.tick(new PlayerTickEvent.Pre(owner));
            var pair=owner.getInventory().removeItem(0,1);h.assertTrue(StockingsHistory.wasWorn(pair),"A completed removal returns the used pair");
            owner.setItemInHand(InteractionHand.MAIN_HAND,pair);stand.interactAt(owner,new Vec3(0,.7,0),InteractionHand.MAIN_HAND);
            h.assertTrue(StockingsHistory.wasWorn(stand.getItemBySlot(EquipmentSlot.LEGS)),"Actual placement preserves history");
        });
        h.onEachTick(()->{if(h.getTick()>146)for(var p:List.of(owner,other)) { p.setShiftKeyDown(true);p.setPose(Pose.CROUCHING);aim(p,stand);p.doTick(); }});
        h.runAtTickTime(190,()->{
            try {
                h.assertTrue(owner.hasEffect(Excitement.EFFECT),"A player can sniff their own previously worn stockings on a stand");
                h.assertTrue(other.hasEffect(Excitement.EFFECT),"Another player can sniff the same used pair through real server tick detection");h.succeed();
            } finally { h.getLevel().getServer().getPlayerList().remove(owner);h.getLevel().getServer().getPlayerList().remove(other); }
        });
    }
    @GameTest(template="empty") public static void recordedHistorySurvivesDeathDrop(GameTestHelper h) {
        var rule=h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);boolean previous=rule.get();var p=viewer(h);
        try {
            rule.set(false,h.getLevel().getServer());StockingsWear.set(p,TheArtofStockings.STOCKINGS.toStack());StockingsHistory.record(p);
            var expected=StockingsWear.get(p).copy();p.die(p.damageSources().generic());
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(2),e->ItemStack.matches(e.getItem(),expected));
            h.assertTrue(drops.size()==1 && StockingsHistory.wasWorn(drops.getFirst().getItem()),"Death drops exactly one pair with wear history");
        } finally { rule.set(previous,h.getLevel().getServer()); }
        h.succeed();
    }
    private static final class CosmeticHistory {
        static void verify(GameTestHelper h,Player p) {
            var container=io.wispforest.accessories.api.AccessoriesCapability.get(p).getContainers().get("stockings");
            container.getCosmeticAccessories().setItem(0,TheArtofStockings.STOCKINGS.toStack());StockingsHistory.record(p);
            h.assertTrue(!StockingsHistory.wasWorn(container.getCosmeticAccessories().getItem(0)),"Cosmetic-only display is not actual player wear");
            container.getCosmeticAccessories().setItem(0,ItemStack.EMPTY);
        }
    }
}
