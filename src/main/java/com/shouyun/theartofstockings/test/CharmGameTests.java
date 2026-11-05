package com.shouyun.theartofstockings.test;

import com.shouyun.theartofstockings.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class CharmGameTests {
    private static void floor(GameTestHelper h) { for(int x=0;x<5;x++)for(int z=0;z<5;z++)h.setBlock(x,0,z,Blocks.STONE); }
    @GameTest(template="empty") public static void wornAuraSupportsStandaloneAndFunctionalStockings(GameTestHelper h) {
        var p=ModdedTestPlayers.create(h);
        try {
            p.setData(TheArtofStockings.WORN_STOCKINGS,TheArtofStockings.STOCKINGS.toStack());
            Charm.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.hasEffect(Charm.EFFECT) && p.getEffect(Charm.EFFECT).isInfiniteDuration(),"Standalone clothing grants charm without depending on an accessory slot");
            p.removeData(TheArtofStockings.WORN_STOCKINGS);
            Charm.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!p.hasEffect(Charm.EFFECT),"Removing standalone clothing removes charm");
            p.setItemSlot(EquipmentSlot.LEGS,TheArtofStockings.STOCKINGS.toStack());Charm.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!p.hasEffect(Charm.EFFECT),"Armor-stand style leg equipment does not grant accessory charm");
            p.setItemSlot(EquipmentSlot.LEGS,ItemStack.EMPTY);
            for(var clothing:java.util.List.of(TheArtofStockings.STOCKINGS,TheArtofStockings.BODYSTOCKING)) {
                StockingsWear.set(p,clothing.toStack());Charm.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(p.hasEffect(Charm.EFFECT),"Stockings and full-body clothing grant the same aura with or without accessory mods");
                StockingsWear.set(p,ItemStack.EMPTY);Charm.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(!p.hasEffect(Charm.EFFECT),"Actual removal clears the aura in every storage backend");
            }
            if(StockingsWear.accessoryAvailable(p)) {
                if(StockingsWear.accessoriesLoaded())CosmeticCheck.verify(h,p);
                StockingsWear.set(p,TheArtofStockings.FOX.toStack());Charm.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(p.hasEffect(Charm.EFFECT) && p.getEffect(Charm.EFFECT).isInfiniteDuration(),"Functional native accessory grants a stable charm buff");
                StockingsWear.set(p,ItemStack.EMPTY);Charm.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(!p.hasEffect(Charm.EFFECT),"Removing the accessory immediately removes its aura");
            }
        } finally { StockingsWear.set(p,ItemStack.EMPTY);h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180) public static void animatedWearGrantsAndRemovesCharm(GameTestHelper h) {
        var p=ModdedTestPlayers.create(h);
        p.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.STOCKINGS.toStack());
        StockingsActions.startEquip(p,InteractionHand.MAIN_HAND);Charm.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(!p.hasEffect(Charm.EFFECT),"Holding stockings or starting the animation does not grant charm early");
        h.runAfterDelay(73,()->{
            StockingsActions.tick(new PlayerTickEvent.Pre(p));Charm.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.hasEffect(Charm.EFFECT),"Completing the normal wear action grants charm without an accessory dependency");
            p.setShiftKeyDown(true);StockingsActions.requestRemoval(p);Charm.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.hasEffect(Charm.EFFECT),"Charm remains while the clothing is still being removed");
        });
        h.runAfterDelay(146,()->{
            try {
                StockingsActions.tick(new PlayerTickEvent.Pre(p));Charm.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(StockingsWear.get(p).isEmpty() && !p.hasEffect(Charm.EFFECT),"Finishing removal returns the clothing and removes charm");h.succeed();
            } finally { StockingsWear.set(p,ItemStack.EMPTY);h.getLevel().getServer().getPlayerList().remove(p); }
        });
    }
    @GameTest(template="empty") public static void charmDiscountUsesRealTradingAndResets(GameTestHelper h) {
        var p=ModdedTestPlayers.create(h);var villager=h.spawn(EntityType.VILLAGER,new BlockPos(2,1,2));
        var offers=villager.getOffers();offers.clear();
        var normal=new MerchantOffer(new ItemCost(Items.EMERALD,10),new ItemStack(Items.BREAD),64,1,0);
        var cheap=new MerchantOffer(new ItemCost(Items.EMERALD,1),new ItemStack(Items.APPLE),64,1,0);
        offers.add(normal);offers.add(cheap);
        try {
            p.setPos(villager.position());villager.mobInteract(p,InteractionHand.MAIN_HAND);
            h.assertTrue(normal.getCostA().getCount()==10,"Uncharmed customer pays the original price");p.closeContainer();villager.setTradingPlayer(null);
            StockingsWear.set(p,TheArtofStockings.STOCKINGS.toStack());Charm.tick(new PlayerTickEvent.Post(p));villager.mobInteract(p,InteractionHand.MAIN_HAND);
            h.assertTrue(normal.getCostA().getCount()==8,"Actual villager trade opening applies a 20-percent charm discount");
            h.assertTrue(cheap.getCostA().getCount()==1,"Discounts never create free trades");p.closeContainer();villager.setTradingPlayer(null);
            h.assertTrue(normal.getSpecialPriceDiff()==0,"Ending the trade resets its temporary charm price");
            villager.mobInteract(p,InteractionHand.MAIN_HAND);h.assertTrue(normal.getCostA().getCount()==8,"Reopening does not accumulate the discount");p.closeContainer();villager.setTradingPlayer(null);
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,200));villager.mobInteract(p,InteractionHand.MAIN_HAND);
            h.assertTrue(normal.getCostA().getCount()==5,"Charm composes with vanilla Hero of the Village prices");p.closeContainer();villager.setTradingPlayer(null);
            StockingsWear.set(p,ItemStack.EMPTY);Charm.tick(new PlayerTickEvent.Post(p));p.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);villager.mobInteract(p,InteractionHand.MAIN_HAND);
            h.assertTrue(normal.getCostA().getCount()==10,"Clean later trades do not inherit a previous discount");
        } finally { StockingsWear.set(p,ItemStack.EMPTY);p.closeContainer();villager.setTradingPlayer(null);h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="empty") public static void friendlyAttractionAndPetRestrictions(GameTestHelper h) {
        floor(h);var p=ModdedTestPlayers.create(h);
        try {
            p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(.5,1,.5)));p.addEffect(new MobEffectInstance(Charm.EFFECT,200));
            var cow=h.spawn(EntityType.COW,new BlockPos(3,1,3));
            cow.setOnGround(true); // This synchronous assertion runs before the first physics tick.
            var goal=cow.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(g->g instanceof CharmAi.FollowCharm).findFirst().orElseThrow();
            h.assertTrue(goal.canUse(),"A nearby friendly animal acquires a charmed player");goal.start();goal.tick();
            h.assertTrue(!cow.getNavigation().isDone(),"Charm requests an actual navigation path");
            p.removeEffect(Charm.EFFECT);h.assertTrue(!goal.canContinueToUse(),"Friendly following stops when charm disappears");goal.stop();
            p.addEffect(new MobEffectInstance(Charm.EFFECT,200));var wolf=h.spawn(EntityType.WOLF,new BlockPos(3,1,3));
            wolf.setTame(true,false);wolf.setOwnerUUID(java.util.UUID.randomUUID());
            h.assertTrue(!new CharmAi.FollowCharm(wolf).canUse(),"Charm does not take another player's pet");
            wolf.setOwnerUUID(p.getUUID());wolf.setOrderedToSit(true);
            h.assertTrue(!new CharmAi.FollowCharm(wolf).canUse(),"A sitting pet keeps its stay order");
            h.assertTrue(!CharmAi.friendly(h.spawn(EntityType.CREEPER,new BlockPos(4,1,4))),"Hostile mobs are excluded");
        } finally { h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="empty") public static void villagerBrainFollowsCharmAndRespectsOdor(GameTestHelper h) {
        floor(h);var p=ModdedTestPlayers.create(h);var villager=h.spawn(EntityType.VILLAGER,new BlockPos(3,1,3));
        try {
            p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(.5,1,.5)));p.addEffect(new MobEffectInstance(Charm.EFFECT,200));
            var behavior=new CharmAi.AttractVillager();
            for(int t=0;t<10;t++)behavior.trigger(h.getLevel(),villager,h.getLevel().getGameTime()+t);
            var walk=villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            h.assertTrue(walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity()==p,"Villager Brain tracks the charmed player");
            p.addEffect(new MobEffectInstance(FootOdor.EFFECT,200));
            for(int t=10;t<20;t++)behavior.trigger(h.getLevel(),villager,h.getLevel().getGameTime()+t);
            h.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(target->!(target.getTarget() instanceof EntityTracker tracker) || tracker.getEntity()!=p).orElse(true),"Existing foot-odor avoidance takes precedence over attraction to that player");
        } finally { h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="empty") public static void friendlyBrainsAndAllayOwnership(GameTestHelper h) {
        var p=ModdedTestPlayers.create(h);var allay=h.spawn(EntityType.ALLAY,new BlockPos(3,1,3));
        try {
            p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(.5,1,.5)));p.addEffect(new MobEffectInstance(Charm.EFFECT,200));
            var behavior=new CharmAi.AttractFriendlyBrain();
            for(int t=0;t<10;t++)behavior.trigger(h.getLevel(),allay,h.getLevel().getGameTime()+t);
            var target=allay.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            h.assertTrue(target.getTarget() instanceof EntityTracker tracker && tracker.getEntity()==p,"A brain-driven friendly mob acquires the charmed player");
            allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER,java.util.UUID.randomUUID());
            for(int t=10;t<20;t++)behavior.trigger(h.getLevel(),allay,h.getLevel().getGameTime()+t);
            h.assertTrue(allay.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(),"A different player's allay is not taken by charm");
        } finally { h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160) public static void wornAuraMovesFriendlyAnimals(GameTestHelper h) {
        floor(h);var p=ModdedTestPlayers.create(h);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(.5,1,.5)));
        StockingsWear.set(p,TheArtofStockings.STRIPED.toStack());Charm.tick(new PlayerTickEvent.Post(p));
        var cow=h.spawn(EntityType.COW,new BlockPos(3,1,3));cow.getRandom().setSeed(87022);
        double initial=cow.distanceToSqr(p);
        h.succeedWhen(()->{
            h.assertTrue(p.hasEffect(Charm.EFFECT) && cow.distanceToSqr(p)<initial-2,"Worn clothing aura attracts an animal through actual world ticks, including standalone storage");
            StockingsWear.set(p,ItemStack.EMPTY);h.getLevel().getServer().getPlayerList().remove(p);
        });
    }
    @GameTest(template="empty",timeoutTicks=180) public static void wornAuraMovesVillagers(GameTestHelper h) {
        floor(h);var p=ModdedTestPlayers.create(h);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(.5,1,.5)));
        StockingsWear.set(p,TheArtofStockings.FOX.toStack());Charm.tick(new PlayerTickEvent.Post(p));
        var villager=h.spawn(EntityType.VILLAGER,new BlockPos(3,1,3));villager.getRandom().setSeed(80451);
        double initial=villager.distanceToSqr(p);var seen=new java.util.concurrent.atomic.AtomicBoolean();
        h.onEachTick(()->villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk->walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity()==p).ifPresent(walk->seen.set(true)));
        h.succeedWhen(()->{
            h.assertTrue(seen.get() && villager.distanceToSqr(p)<initial-2,"A villager actually walks toward the stockings wearer, including standalone storage");
            StockingsWear.set(p,ItemStack.EMPTY);h.getLevel().getServer().getPlayerList().remove(p);
        });
    }
    private static final class CosmeticCheck {
        static void verify(GameTestHelper h,net.minecraft.world.entity.player.Player p) {
            var container=io.wispforest.accessories.api.AccessoriesCapability.get(p).getContainers().get("stockings");
            container.getCosmeticAccessories().setItem(0,TheArtofStockings.STOCKINGS.toStack());Charm.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!p.hasEffect(Charm.EFFECT),"A cosmetic-only pair does not grant a functional buff");container.getCosmeticAccessories().setItem(0,ItemStack.EMPTY);
        }
    }
}
