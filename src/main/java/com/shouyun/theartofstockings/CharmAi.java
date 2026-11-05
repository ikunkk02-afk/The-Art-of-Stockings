package com.shouyun.theartofstockings;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid=TheArtofStockings.MODID)
public final class CharmAi {
    private CharmAi() {}
    public static boolean friendly(Mob mob) {
        if(mob instanceof net.minecraft.world.entity.monster.Enemy)return false;
        return mob instanceof Animal || mob instanceof WaterAnimal || mob instanceof AmbientCreature
                || mob instanceof AbstractGolem || mob instanceof Allay;
    }
    private static boolean mayFollow(Mob mob,Player player) {
        if(!Charm.active(player) || player.level()!=mob.level() || mob.getTarget()!=null || mob.isAggressive() || mob.isLeashed() || mob.isSleeping()
                || !mob.isWithinRestriction(player.blockPosition()))return false;
        if(mob instanceof NeutralMob neutral && neutral.isAngry())return false;
        if(mob instanceof TamableAnimal pet && (pet.isOrderedToSit() || pet.isTame() && !pet.isOwnedBy(player)))return false;
        if(mob instanceof AbstractHorse horse && horse.getOwnerUUID()!=null && !horse.getOwnerUUID().equals(player.getUUID()))return false;
        if(mob instanceof Allay allay && allay.getBrain().getMemory(MemoryModuleType.LIKED_PLAYER)
                .filter(owner->!owner.equals(player.getUUID())).isPresent())return false;
        return !(mob instanceof WaterAnimal) || player.isInWater();
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !friendly(mob))return;
        if(mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET,MemoryStatus.REGISTERED)) {
            @SuppressWarnings("unchecked") var brain=(Brain<Mob>)(Brain<?>)mob.getBrain();
            brain.addActivity(Activity.CORE,2,com.google.common.collect.ImmutableList.of(new AttractFriendlyBrain()));
        } else if(mob.goalSelector.getAvailableGoals().stream().noneMatch(goal->goal.getGoal() instanceof FollowCharm))
            mob.goalSelector.addGoal(4,new FollowCharm(mob));
    }
    public static final class AttractFriendlyBrain extends OneShot<Mob> {
        private Player followed;
        @Override public boolean trigger(ServerLevel level,Mob mob,long gameTime) {
            if((gameTime+mob.getId())%10!=0)return false;
            var brain=mob.getBrain();
            var conditions=TargetingConditions.forNonCombat().range(Charm.RANGE).selector(entity->entity instanceof Player player && mayFollow(mob,player));
            var player=brain.isActive(Activity.PANIC) || brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    ? null:level.getNearestPlayer(conditions,mob,mob.getX(),mob.getY(),mob.getZ());
            if(player==null || mob.distanceToSqr(player)<=9) {
                var walk=brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                if(walk!=null && walk.getSpeedModifier()==.65F && walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity()==followed) {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);mob.getNavigation().stop();
                }
                followed=null;return false;
            }
            if(player!=followed)hearts(mob);followed=player;
            brain.setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(player,.65F,3));return true;
        }
    }
    private static void hearts(Mob mob) {
        if(mob.level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.HEART,mob.getX(),mob.getY()+mob.getBbHeight()+.15,mob.getZ(),1,.12,.12,.12,0);
    }
    public static final class FollowCharm extends Goal {
        private final Mob mob;
        private Player source;
        private int nextCheck,nextPath;
        public FollowCharm(Mob mob) { this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() {
            if(mob.tickCount<nextCheck)return false;nextCheck=mob.tickCount+20;
            var conditions=TargetingConditions.forNonCombat().range(Charm.RANGE).selector(entity->entity instanceof Player player && mayFollow(mob,player));
            source=mob.level().getNearestPlayer(conditions,mob,mob.getX(),mob.getY(),mob.getZ());
            return source!=null && mob.distanceToSqr(source)>9;
        }
        @Override public boolean canContinueToUse() { return source!=null && mayFollow(mob,source) && mob.distanceToSqr(source)<=400 && mob.distanceToSqr(source)>9; }
        @Override public void start() { nextPath=0;hearts(mob); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(source==null)return;
            mob.getLookControl().setLookAt(source,10,mob.getMaxHeadXRot());
            if(--nextPath<=0) { nextPath=10;mob.getNavigation().moveTo(source,1.0); }
        }
        @Override public void stop() { source=null;mob.getNavigation().stop(); }
    }
    /** Villagers use a Brain, so ordinary goal-selector following alone would be overwritten. */
    public static final class AttractVillager extends OneShot<Villager> {
        private static final float SPEED=.65F;
        private Player followed;
        private void release(Villager villager) {
            var walk=villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if(walk!=null && walk.getSpeedModifier()==SPEED && walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity()==followed) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);villager.getNavigation().stop();
            }
            followed=null;
        }
        @Override public boolean trigger(ServerLevel level,Villager villager,long gameTime) {
            if((gameTime+villager.getId())%10!=0)return false;
            var brain=villager.getBrain();
            if(villager.isSleeping() || villager.isTrading() || brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE)
                    || brain.isActive(Activity.PRE_RAID) || brain.isActive(Activity.RAID)) { release(villager);return false; }
            var conditions=TargetingConditions.forNonCombat().range(Charm.RANGE)
                    .selector(entity->entity instanceof Player player && Charm.active(player) && !player.hasEffect(FootOdor.EFFECT)
                            && villager.isWithinRestriction(player.blockPosition()));
            var player=level.getNearestPlayer(conditions,villager,villager.getX(),villager.getY(),villager.getZ());
            if(player==null || villager.distanceToSqr(player)<=9) { release(villager);return false; }
            if(player!=followed)hearts(villager);
            followed=player;
            brain.setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(player,SPEED,3));
            return true;
        }
    }
}
