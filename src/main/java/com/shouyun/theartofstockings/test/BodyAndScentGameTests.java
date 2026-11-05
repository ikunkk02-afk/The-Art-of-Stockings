package com.shouyun.theartofstockings.test;

import com.shouyun.theartofstockings.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class BodyAndScentGameTests {
    private static Player player(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(h.absoluteVec(new Vec3(1.5,1,1.5)));return p;
    }
    private static void aim(Player viewer,Player target) {
        var diff=target.position().add(0,.25,0).subtract(viewer.getEyePosition());
        viewer.setYRot((float)Math.toDegrees(Math.atan2(-diff.x,diff.z)));
        viewer.setXRot((float)-Math.toDegrees(Math.atan2(diff.y,Math.hypot(diff.x,diff.z))));
    }
    @GameTest(template="empty") public static void bodystockingTailoringAndDeepPalettePersistence(GameTestHelper h) {
        var source=TheArtofStockings.STOCKINGS.toStack();source.set(TheArtofStockings.DEPTH.get(),87);
        source.set(TheArtofStockings.OPACITY.get(),64);source.set(TheArtofStockings.STINKY.get(),net.minecraft.util.Unit.INSTANCE);
        source.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Deep body"));
        var input=CraftingInput.of(2,1,List.of(source,new ItemStack(Items.LEATHER)));
        var pair=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow()
                .value().assemble(input,h.getLevel().registryAccess());
        h.assertTrue(pair.is(TheArtofStockings.BODYSTOCKING.get()) && StockingsItem.depth(pair)==87,"Leather conversion preserves depth");
        h.assertTrue(StockingsItem.opacity(pair)==64 && pair.has(TheArtofStockings.STINKY.get()) && pair.getHoverName().getString().contains("Deep body"),"Conversion preserves appearance, odor and name");
        var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());p.setItemInHand(InteractionHand.OFF_HAND,pair);
        var token=PaletteEdits.open(p,40,pair);
        var packet=new StockingsNetwork.PaletteChunk(token,0x08080E,0,100,100,0,1,new byte[0]);
        var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            StockingsNetwork.PaletteChunk.CODEC.encode(buf,packet);var decoded=StockingsNetwork.PaletteChunk.CODEC.decode(buf);
            h.assertTrue(decoded.depth()==100 && PaletteEdits.receive(p,decoded)==PaletteEdits.Result.SUCCESS,"Maximum depth survives the real upload codec and commit");
        } finally { buf.release(); }
        var loaded=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)pair.save(h.getLevel().registryAccess()));
        h.assertTrue(ItemStack.matches(pair,loaded) && StockingsItem.depth(loaded)==100,"Depth survives NBT save/reload");
        h.assertTrue(StockingsItem.depth(TheArtofStockings.STOCKINGS.toStack())==0,"Old items preserve default fabric depth");
        var before=pair.copy();
        for(int invalid:new int[]{-1,101,Integer.MAX_VALUE})
            h.assertTrue(!PaletteItem.apply(p,40,before,0,0,100,invalid,"") && ItemStack.matches(pair,before),"Invalid depth never mutates an item");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180) public static void handsHeadAnimatedTransferAndArmorCoexist(GameTestHelper h) {
        var p=player(h);var hands=TheArtofStockings.CAT_PAW.toStack();hands.set(TheArtofStockings.DEPTH.get(),72);
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
        StockingsWear.set(p,TheArtofStockings.GRADIENT.toStack());
        p.setItemInHand(InteractionHand.MAIN_HAND,hands.copy());StockingsActions.startEquip(p,InteractionHand.MAIN_HAND,WearSlot.HANDS);
        h.assertTrue(StockingsWear.get(p,WearSlot.HANDS).isEmpty(),"Hands wait for the complete transaction");
        h.runAfterDelay(73,()->{
            StockingsActions.tick(new PlayerTickEvent.Pre(p));
            StockingsHistory.mark(hands);
            h.assertTrue(ItemStack.matches(StockingsWear.get(p,WearSlot.HANDS),hands) && p.getMainHandItem().isEmpty(),"One pair transfers to both hands");
            StockingsWear.set(p,WearSlot.HEAD,TheArtofStockings.MATTE.toStack());
            p.setShiftKeyDown(true);
            new StockingsNetwork.WearRequest(WearSlot.HANDS.ordinal(),InteractionHand.MAIN_HAND,false,hands.copy()).apply(p);
        });
        h.runAfterDelay(146,()->{
            StockingsActions.tick(new PlayerTickEvent.Pre(p));
            h.assertTrue(StockingsWear.get(p,WearSlot.HANDS).isEmpty() && p.getInventory().countItem(hands.getItem())==1,"Removing hands returns exactly one pair");
            h.assertTrue(!StockingsWear.get(p).isEmpty() && !StockingsWear.get(p,WearSlot.HEAD).isEmpty(),"Other clothing locations remain intact");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && p.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),"Helmet and chestplate survive all transfers");
            h.succeed();
        });
    }
    @GameTest(template="empty") public static void extraClothingSaveCloneAndPalette(GameTestHelper h) {
        var p=player(h);var pair=TheArtofStockings.FOX.toStack();pair.set(TheArtofStockings.DEPTH.get(),63);
        StockingsWear.set(p,WearSlot.HANDS,pair);StockingsWear.set(p,WearSlot.HEAD,TheArtofStockings.MATTE.toStack());
        var save=new net.minecraft.nbt.CompoundTag();p.saveWithoutId(save);var loaded=player(h);loaded.load(save);
        h.assertTrue(ItemStack.matches(StockingsWear.get(loaded,WearSlot.HANDS),pair) && !StockingsWear.get(loaded,WearSlot.HEAD).isEmpty(),"Both extra locations survive player save/reload");
        var clone=player(h);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(clone,p,false));
        h.assertTrue(ItemStack.matches(StockingsWear.get(clone,WearSlot.HANDS),pair) && StockingsWear.get(clone,WearSlot.HANDS)!=StockingsWear.get(p,WearSlot.HANDS),"Clones have independent copies");
        p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        h.assertTrue(PaletteItem.apply(p,PaletteItem.HAND_SLOT,pair.copy(),0x222222,0x111111,80,94,""),"Palette edits the real hand attachment");
        h.assertTrue(StockingsItem.depth(StockingsWear.get(p,WearSlot.HANDS))==94 && StockingsItem.depth(StockingsWear.get(p,WearSlot.HEAD))==0,"Palette edits do not leak into another location");
        h.succeed();
    }
    @GameTest(template="empty") public static void extraClothingDeathAndKeepInventory(GameTestHelper h) {
        var rule=h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);boolean previous=rule.get();
        try {
            rule.set(false,h.getLevel().getServer());var p=player(h);var pair=TheArtofStockings.CAT_PAW.toStack();
            StockingsWear.set(p,WearSlot.HANDS,pair);StockingsWear.set(p,WearSlot.HEAD,pair);p.die(p.damageSources().generic());
            h.assertTrue(!StockingsWear.hasExtra(p),"Death clears both extra locations");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(2),e->ItemStack.matches(e.getItem(),pair));
            h.assertTrue(drops.size()==2,"Two worn pairs produce exactly two drops");
            var clone=player(h);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(clone,p,true));
            h.assertTrue(!StockingsWear.hasExtra(clone),"Respawn cannot duplicate drops");
            rule.set(true,h.getLevel().getServer());var kept=player(h);StockingsWear.set(kept,WearSlot.HEAD,pair);kept.die(kept.damageSources().generic());
            var keptClone=player(h);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(keptClone,kept,true));
            h.assertTrue(ItemStack.matches(StockingsWear.get(keptClone,WearSlot.HEAD),pair),"KeepInventory retains head clothing");
        } finally { rule.set(previous,h.getLevel().getServer()); }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void changedHeldItemCancelsHeadWear(GameTestHelper h) {
        var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.STOCKINGS.toStack());
        StockingsActions.startEquip(p,InteractionHand.MAIN_HAND,WearSlot.HEAD);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        StockingsActions.tick(new PlayerTickEvent.Pre(p));
        h.assertTrue(!StockingsActions.busy(p) && StockingsWear.get(p,WearSlot.HEAD).isEmpty(),"Changing the source cancels head wear atomically");
        new StockingsNetwork.WearRequest(99,InteractionHand.MAIN_HAND,true,ItemStack.EMPTY).apply(p);
        h.assertTrue(p.getMainHandItem().is(Items.STICK),"Invalid location packets do not move items");
        p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.BODYSTOCKING.toStack());StockingsActions.startEquip(p,InteractionHand.MAIN_HAND,WearSlot.HEAD);
        h.assertTrue(!StockingsActions.busy(p),"A full garment uses its full-body location");
        h.succeed();
    }
    @GameTest(template="empty") public static void scentRequiresFeetAimRangeAndClearView(GameTestHelper h) {
        var viewer=player(h);var target=player(h);target.setPos(h.absoluteVec(new Vec3(1.5,1,3)));
        viewer.setShiftKeyDown(true);viewer.setPose(Pose.CROUCHING);StockingsWear.set(target,TheArtofStockings.STOCKINGS.toStack());aim(viewer,target);
        h.assertTrue(Excitement.canSmell(viewer,target),"Crouching close to worn stockings and aiming at feet is valid");
        h.assertTrue(!Excitement.canSmell(viewer,viewer),"Self sniffing never grants the effect");
        viewer.setShiftKeyDown(false);h.assertTrue(!Excitement.canSmell(viewer,target),"Standing does not qualify");viewer.setShiftKeyDown(true);
        viewer.setXRot(-60);h.assertTrue(!Excitement.canSmell(viewer,target),"Looking away from the feet does not qualify");aim(viewer,target);
        h.setBlock(new BlockPos(1,1,2),Blocks.STONE);h.setBlock(new BlockPos(1,2,2),Blocks.STONE);
        h.assertTrue(!Excitement.canSmell(viewer,target),"Solid blocks obstruct the scent interaction");
        h.setBlock(new BlockPos(1,1,2),Blocks.AIR);h.setBlock(new BlockPos(1,2,2),Blocks.AIR);
        target.setPos(h.absoluteVec(new Vec3(1.5,1,5)));aim(viewer,target);h.assertTrue(!Excitement.canSmell(viewer,target),"Distant players do not qualify");
        target.setPos(h.absoluteVec(new Vec3(1.5,1,3)));StockingsWear.set(target,ItemStack.EMPTY);StockingsWear.set(target,WearSlot.HANDS,TheArtofStockings.STOCKINGS.toStack());aim(viewer,target);
        h.assertTrue(!Excitement.canSmell(viewer,target),"Hand-only clothing is not worn foot stockings");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void sustainedScentGrantsRealExcitementWithCooldown(GameTestHelper h) {
        var viewer=player(h);var target=player(h);target.setPos(h.absoluteVec(new Vec3(1.5,1,3)));
        viewer.setShiftKeyDown(true);viewer.setPose(Pose.CROUCHING);StockingsWear.set(target,TheArtofStockings.BODYSTOCKING.toStack());aim(viewer,target);
        double before=viewer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        h.onEachTick(()->{aim(viewer,target);Excitement.update(viewer,target);});
        h.runAtTickTime(15,()->h.assertTrue(!viewer.hasEffect(Excitement.EFFECT),"A brief crouch does not grant the buff"));
        h.runAtTickTime(35,()->{
            h.assertTrue(viewer.hasEffect(Excitement.EFFECT),"Sustained foot interaction grants the custom effect");
            h.assertTrue(viewer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)>before,"The effect actually increases speed");
            viewer.removeEffect(Excitement.EFFECT);
        });
        h.runAtTickTime(70,()->{h.assertTrue(!viewer.hasEffect(Excitement.EFFECT),"The cooldown prevents immediate repeated grants");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void changingScentTargetResetsProgress(GameTestHelper h) {
        var viewer=player(h);var first=player(h);var second=player(h);
        first.setPos(h.absoluteVec(new Vec3(1.5,1,3)));second.setPos(h.absoluteVec(new Vec3(1.7,1,3)));
        StockingsWear.set(first,TheArtofStockings.STOCKINGS.toStack());StockingsWear.set(second,TheArtofStockings.STOCKINGS.toStack());
        viewer.setShiftKeyDown(true);viewer.setPose(Pose.CROUCHING);
        h.onEachTick(()->{
            var target=h.getTick()<20?first:second;aim(viewer,target);Excitement.update(viewer,target);
        });
        h.runAtTickTime(38,()->h.assertTrue(!viewer.hasEffect(Excitement.EFFECT),"Two short interactions with different players do not combine"));
        h.runAtTickTime(55,()->{h.assertTrue(viewer.hasEffect(Excitement.EFFECT),"Staying with the second target grants the effect after a fresh duration");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void serverPlayerTickDetectsOtherPlayerFeet(GameTestHelper h) {
        var viewer=ModdedTestPlayers.create(h);var target=ModdedTestPlayers.create(h);
        viewer.setPos(h.absoluteVec(new Vec3(1.5,1,1.5)));target.setPos(h.absoluteVec(new Vec3(1.5,1,3)));
        viewer.setNoGravity(true);target.setNoGravity(true);viewer.setInvulnerable(true);target.setInvulnerable(true);
        StockingsWear.set(target,TheArtofStockings.BODYSTOCKING.toStack());
        h.onEachTick(()->{
            viewer.setShiftKeyDown(true);viewer.setPose(Pose.CROUCHING);aim(viewer,target);
            // Embedded test connections are not live clients; drive the real player tick.
            viewer.doTick();
        });
        h.runAtTickTime(50,()->{
            try {
                h.assertTrue(viewer.hasEffect(Excitement.EFFECT),"Real server player ticks discover the other player's stockings and grant excitement; valid="+Excitement.canSmell(viewer,target)+", ticks="+viewer.tickCount+", distance="+viewer.distanceTo(target));
                h.assertTrue(!target.hasEffect(Excitement.EFFECT),"Only the crouching observer receives the effect");h.succeed();
            } finally {
                h.getLevel().getServer().getPlayerList().remove(viewer);h.getLevel().getServer().getPlayerList().remove(target);
            }
        });
    }
}
