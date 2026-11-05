package com.shouyun.theartofstockings.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.shouyun.theartofstockings.FootOdor;
import com.shouyun.theartofstockings.StockingsWear;
import com.shouyun.theartofstockings.TheArtofStockings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class FootOdorGameTests {
    @GameTest(template = "empty")
    public static void commandGivesOdorImmediately(GameTestHelper helper) throws CommandSyntaxException {
        var player = ModdedTestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        try {
            var dispatcher = server.getCommands().getDispatcher();
            var source = player.createCommandSourceStack();
            var command = dispatcher.getRoot().getChild("footodor");
            helper.assertTrue(command != null && !command.canUse(source.withPermission(0)),
                    "Foot odor command requires operator permission");
            int result = dispatcher.execute("footodor", source.withPermission(2));
            var odor = player.getEffect(FootOdor.EFFECT);
            helper.assertTrue(result == 1 && odor != null && odor.getDuration() == 1_200,
                    "Command immediately gives the unequipped player one minute of foot odor");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty")
    public static void fullDayOfWearSurvivesReload(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StockingsWear.set(player, TheArtofStockings.FISHNET.toStack());
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        tickWear(player, 23_999);
        helper.assertTrue(!player.hasEffect(FootOdor.EFFECT), "Odor requires a full day");

        Player reloaded = helper.makeMockPlayer(GameType.SURVIVAL);
        reloaded.load(player.saveWithoutId(new CompoundTag()));
        tickWear(reloaded, 1);
        helper.assertTrue(reloaded.hasEffect(FootOdor.EFFECT), "Wear progress survives save and reload");

        reloaded.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        tickWear(reloaded, 1);
        helper.assertTrue(reloaded.hasEffect(FootOdor.EFFECT), "Odor lingers after undressing");
        reloaded.removeEffect(FootOdor.EFFECT);
        tickWear(reloaded, 24_000);
        helper.assertTrue(!reloaded.hasEffect(FootOdor.EFFECT), "No odor without boots");
        reloaded.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        tickWear(reloaded, 1);
        helper.assertTrue(!reloaded.hasEffect(FootOdor.EFFECT), "Undressing resets the timer");
        tickWear(reloaded, 23_999);
        helper.assertTrue(reloaded.hasEffect(FootOdor.EFFECT), "A second full day brings odor back");

        reloaded.removeEffect(FootOdor.EFFECT);
        tickWear(reloaded, 1);
        helper.assertTrue(!reloaded.hasEffect(FootOdor.EFFECT), "Clearing odor starts another full day");
        helper.succeed();
    }

    private static void tickWear(Player player, int ticks) {
        var event = new PlayerTickEvent.Post(player);
        for (int i = 0; i < ticks; i++) FootOdor.tick(event);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void villagerMovesAwayFromScent(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.getRandom().setSeed(0x570C1A65L);
        var player = ModdedTestPlayers.create(helper);
        var source = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1.0, 2.5));
        player.setPos(source);
        player.addEffect(new MobEffectInstance(FootOdor.EFFECT, 200));
        double startingDistance = villager.distanceToSqr(player);

        // Check that the villager takes the scent-specific walking route, then moves away.
        var fled = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                .filter(target -> target.getSpeedModifier() == 0.8F).ifPresent(target -> fled.set(true)));
        // Observe the escape when it happens, rather than sampling one arbitrary AI tick.
        helper.succeedWhen(() -> {
            double endingDistance = villager.distanceToSqr(source);
            helper.assertTrue(fled.get() && endingDistance > startingDistance + 1, "Villager walks away from the odor");
            level.getServer().getPlayerList().remove(player);
        });
    }

    @GameTest(template = "empty")
    public static void creeperTracksAndLosesDistantScent(GameTestHelper helper) {
        var player = ModdedTestPlayers.create(helper);
        try {
            player.setGameMode(GameType.SURVIVAL);
            var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(2, 1, 2));
            player.setPos(creeper.position().add(32, 0, 0));
            double normalRange = creeper.getAttributeValue(Attributes.FOLLOW_RANGE);
            tickTargets(creeper, 100);
            helper.assertTrue(creeper.getTarget() == null && normalRange < 32, "Clean player outside normal range");
            player.addEffect(new MobEffectInstance(FootOdor.EFFECT, 200));
            tickTargets(creeper, 100);
            helper.assertTrue(creeper.getTarget() == player && creeper.getAttributeValue(Attributes.FOLLOW_RANGE) >= 48,
                    "Odor attracts the creeper beyond normal range");

            player.removeEffect(FootOdor.EFFECT);
            tickTargets(creeper, 100);
            helper.assertTrue(creeper.getTarget() == null && creeper.getAttributeValue(Attributes.FOLLOW_RANGE) == normalRange,
                    "Losing scent restores ordinary targeting and navigation range");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static void tickTargets(net.minecraft.world.entity.Mob mob, int ticks) {
        for (int i = 0; i < ticks; i++) mob.targetSelector.tick();
    }
}
