package com.shouyun.theartofstockings.test;

import com.shouyun.theartofstockings.StockingsHistory;

import com.shouyun.theartofstockings.FootOdor;
import com.shouyun.theartofstockings.StockingsActions;
import com.shouyun.theartofstockings.StockingsWear;
import com.shouyun.theartofstockings.TheArtofStockings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class FootbathWaterGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void undressingKeepsOriginalStockingsData(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var original = TheArtofStockings.GRADIENT.toStack();
        original.set(DataComponents.CUSTOM_NAME, Component.literal("My gradient"));
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0x903060, true));
        original.set(TheArtofStockings.SECONDARY_COLOR.get(), 0x4080AB);
        StockingsWear.set(player, original);
        player.addEffect(new MobEffectInstance(FootOdor.EFFECT, 300));
        player.setShiftKeyDown(true);
        StockingsActions.requestRemoval(player);

        helper.runAfterDelay(StockingsActions.DURATION + 1, () -> {
            StockingsActions.tick(new PlayerTickEvent.Pre(player));
            helper.assertTrue(StockingsWear.get(player).isEmpty(), "Removal clears the clothing slot");
            var expected = original.copy();
            StockingsHistory.mark(expected);
            expected.set(TheArtofStockings.STINKY.get(), Unit.INSTANCE);
            var dirty = player.getInventory().getItem(0);
            helper.assertTrue(ItemStack.matches(dirty, expected), "Removal adds only the stinky marker");
            var registries = helper.getLevel().registryAccess();
            var reloaded = ItemStack.parse(registries, dirty.save(registries)).orElseThrow();
            helper.assertTrue(ItemStack.matches(reloaded, expected), "The marker, variant, dye and name survive saving");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void brewsOnlyWithStinkyStockingsAndWater(GameTestHelper helper) {
        var level = helper.getLevel();
        var brewing = level.potionBrewing();
        var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        var clean = TheArtofStockings.STOCKINGS.toStack();
        helper.assertTrue(!brewing.hasMix(water, clean), "Clean stockings cannot brew footbath water");
        var dirty = TheArtofStockings.FISHNET.toStack();
        dirty.set(TheArtofStockings.STINKY.get(), Unit.INSTANCE);
        dirty.set(DataComponents.CUSTOM_NAME, Component.literal("Worn fishnet"));
        var awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        helper.assertTrue(!brewing.hasMix(awkward, dirty), "The ingredient requires ordinary water");

        var position = new BlockPos(2, 1, 2);
        helper.setBlock(position, Blocks.BREWING_STAND);
        var stand = (BrewingStandBlockEntity)level.getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(stand.canPlaceItem(3, dirty) && !stand.canPlaceItem(3, clean), "The brewing stand validates the ingredient slot");
        stand.setItem(0, water.copy());
        stand.setItem(1, awkward);
        stand.setItem(2, water.copy());
        stand.setItem(3, dirty);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        for (int tick = 0; tick <= 400; tick++) {
            BrewingStandBlockEntity.serverTick(level, stand.getBlockPos(), stand.getBlockState(), stand);
        }
        helper.assertTrue(stand.getItem(0).is(TheArtofStockings.FOOTBATH_WATER.get())
                        && stand.getItem(2).is(TheArtofStockings.FOOTBATH_WATER.get()),
                "Both water bottles become footbath water");
        helper.assertTrue(stand.getItem(3).isEmpty() && stand.getItem(4).isEmpty(), "Brewing consumes one ingredient and uses fuel");
        helper.assertTrue(ItemStack.matches(stand.getItem(1), awkward), "The other potion stays in its slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void drinkPoisonsAndThrownBottleSpreadsOdor(GameTestHelper helper) {
        var level = helper.getLevel();
        var user = helper.makeMockPlayer(GameType.SURVIVAL);
        user.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 2.5)));
        var item = TheArtofStockings.FOOTBATH_WATER.get();
        var drink = item.getDefaultInstance();
        user.setItemInHand(InteractionHand.MAIN_HAND, drink);
        item.use(level, user, InteractionHand.MAIN_HAND);
        helper.assertTrue(user.isUsingItem(), "Right click starts drinking");
        var emptyBottle = item.finishUsingItem(drink, level, user);
        helper.assertTrue(emptyBottle.is(Items.GLASS_BOTTLE) && user.hasEffect(MobEffects.POISON)
                && !user.hasEffect(FootOdor.EFFECT), "Drinking poisons the user and returns the empty bottle");
        user.stopUsingItem();

        var target = ModdedTestPlayers.create(helper);
        try {
            target.setGameMode(GameType.SURVIVAL);
            target.setPos(user.position().add(2, 0, 0));
            user.setShiftKeyDown(true);
            user.setItemInHand(InteractionHand.MAIN_HAND, item.getDefaultInstance());
            item.use(level, user, InteractionHand.MAIN_HAND);
            helper.assertTrue(user.getMainHandItem().isEmpty(), "Throwing consumes the bottle");
            var bottles = level.getEntitiesOfClass(ThrownPotion.class, user.getBoundingBox().inflate(4),
                    bottle -> bottle.getOwner() == user);
            helper.assertTrue(bottles.size() == 1, "Sneak-right-click spawns a single projectile");
            var projectile = bottles.getFirst();
            projectile.setPos(target.getX() - 0.9, target.getY() + 0.8, target.getZ());
            projectile.setDeltaMovement(1, 0, 0);
            projectile.tick();
            var odor = target.getEffect(FootOdor.EFFECT);
            helper.assertTrue(projectile.isRemoved() && odor != null && odor.getDuration() == 1_200
                            && !target.hasEffect(MobEffects.POISON),
                    "Direct hit spreads a full minute of foot odor to the other player");
            helper.succeed();
        } finally {
            level.getServer().getPlayerList().remove(target);
        }
    }
}
