package com.shouyun.theartofstockings;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = TheArtofStockings.MODID)
public final class FootOdorAi {
    private static final double SCENT_RANGE = 48.0;
    private static final ResourceLocation SCENT_FOLLOW_RANGE =
            ResourceLocation.fromNamespaceAndPath(TheArtofStockings.MODID, "scent_follow_range");

    private FootOdorAi() {}

    @SubscribeEvent
    public static void addScentGoal(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (mob instanceof Zombie && !(mob instanceof ZombifiedPiglin)
                || mob instanceof AbstractSkeleton || mob instanceof Creeper) {
            if (mob.targetSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof FollowScent)) {
                mob.targetSelector.addGoal(2, new FollowScent(mob));
            }
        }
    }

    private static final class FollowScent extends NearestAttackableTargetGoal<Player> {
        FollowScent(Mob mob) {
            super(mob, Player.class, 20, false, false, entity -> FootOdor.isScentedPlayer(entity)
                    && (!(mob instanceof Drowned drowned) || drowned.okTarget(entity)));
            targetConditions.ignoreLineOfSight().ignoreInvisibilityTesting();
        }

        @Override
        protected double getFollowDistance() {
            return SCENT_RANGE;
        }

        @Override
        public boolean canUse() {
            return mob.level().getDifficulty() != Difficulty.PEACEFUL
                    && super.canUse() && mob.isWithinRestriction(target.blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && mob.getTarget() == target
                    && mob.level().getDifficulty() != Difficulty.PEACEFUL
                    && mob.isWithinRestriction(target.blockPosition()) && targetConditions.test(mob, target);
        }

        @Override
        public void start() {
            super.start();
            // Vanilla navigation also uses FOLLOW_RANGE when building a path.
            var attribute = mob.getAttribute(Attributes.FOLLOW_RANGE);
            double range = attribute.getValue();
            if (mob.getTarget() == target && range > 0 && range < SCENT_RANGE) {
                attribute.addTransientModifier(new AttributeModifier(SCENT_FOLLOW_RANGE,
                        SCENT_RANGE / range - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        @Override
        public void stop() {
            if (mob.getTarget() == target) super.stop();
            mob.getAttribute(Attributes.FOLLOW_RANGE).removeModifier(SCENT_FOLLOW_RANGE);
            target = null;
        }
    }

    public static final class AvoidScent extends OneShot<Villager> {
        private static final float FLEE_SPEED = 0.8F;
        private static final TargetingConditions NEARBY_SCENT = TargetingConditions.forNonCombat().range(8.0)
                .ignoreLineOfSight().ignoreInvisibilityTesting().selector(FootOdor::isScentedPlayer);

        @Override
        public boolean trigger(ServerLevel level, Villager villager, long gameTime) {
            if ((gameTime + villager.getId()) % 10 != 0 || villager.isSleeping() || villager.isTrading()) return false;
            var brain = villager.getBrain();
            if (brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE)
                    || brain.isActive(Activity.PRE_RAID) || brain.isActive(Activity.RAID)) return false;

            Player source = level.getNearestPlayer(NEARBY_SCENT, villager, villager.getX(), villager.getY(), villager.getZ());
            if (source == null) return false;

            var current = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if (current != null && current.getSpeedModifier() >= FLEE_SPEED
                    && current.getTarget().currentPosition().subtract(villager.position())
                            .dot(source.position().subtract(villager.position())) < 0) return false;

            var away = LandRandomPos.getPosAway(villager, 12, 4, source.position());
            if (away == null) return false;

            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, FLEE_SPEED, 0));
            villager.playSound(SoundEvents.VILLAGER_NO, 0.6F, villager.getVoicePitch());
            return true;
        }
    }
}
