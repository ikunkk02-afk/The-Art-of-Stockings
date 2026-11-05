package com.shouyun.theartofstockings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The server verifies sustained crouching, range, foot aim and an unobstructed ray. */
@EventBusSubscriber(modid=TheArtofStockings.MODID)
public final class Excitement extends MobEffect {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,TheArtofStockings.MODID);
    public static final DeferredHolder<MobEffect,Excitement> EFFECT=EFFECTS.register("excitement",Excitement::new);
    public static final int SNIFF_TICKS=30,DURATION=600,COOLDOWN=100;
    public static final double RANGE=2.25;
    private record Progress(UUID target,ItemStack stockings,int ticks,long last,long cooldown) {}
    private static final Map<UUID,Progress> PROGRESS=new HashMap<>();
    private Excitement() {
        super(MobEffectCategory.BENEFICIAL,0xE788A8);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,ResourceLocation.fromNamespaceAndPath(TheArtofStockings.MODID,"excitement_speed"),
                .10,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
    static void register(IEventBus bus) { EFFECTS.register(bus); }
    public static boolean canSmell(Player viewer,LivingEntity target) {
        if(viewer==target || !viewer.isAlive() || viewer.isSpectator() || !viewer.isShiftKeyDown()
                || viewer.isPassenger() || viewer.isSwimming() || StockingsActions.busy(viewer)
                || !target.isAlive() || target.isSpectator() || target.isSleeping()
                || viewer.level()!=target.level() || !StockingsActions.wearing(target)
                || viewer.distanceToSqr(target)>RANGE*RANGE) return false;
        float scale=1;
        if(target instanceof ArmorStand stand) {
            if(stand.isMarker() || !StockingsHistory.wasWorn(StockingsActions.worn(stand)))return false;
            if(stand.isSmall())scale=.5F;
        } else if(!(target instanceof Player) || target.isInvisible())return false;
        var feet=new AABB(target.getX()-.34*scale,target.getY(),target.getZ()-.34*scale,
                target.getX()+.34*scale,target.getY()+.55*scale,target.getZ()+.34*scale);
        Vec3 eye=viewer.getEyePosition(),end=eye.add(viewer.getLookAngle().scale(RANGE));
        var hit=feet.clip(eye,end);
        if(hit.isEmpty())return false;
        return viewer.level().clip(new ClipContext(eye,hit.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,viewer)).getType()==HitResult.Type.MISS;
    }
    public static void update(Player player,LivingEntity target) {
        if(player.level().isClientSide)return;
        long now=player.level().getGameTime();var previous=PROGRESS.get(player.getUUID());
        long cooldown=previous==null?0:previous.cooldown;
        if(target==null || !canSmell(player,target)) {
            if(cooldown>now)PROGRESS.put(player.getUUID(),new Progress(null,ItemStack.EMPTY,0,now,cooldown));
            else PROGRESS.remove(player.getUUID());
            return;
        }
        if(cooldown>now)return;
        var stockings=StockingsActions.worn(target);
        boolean continuing=previous!=null && target.getUUID().equals(previous.target) && previous.last==now-1
                && ItemStack.matches(stockings,previous.stockings);
        int count=continuing?previous.ticks+1:1;
        if(count>=SNIFF_TICKS) {
            player.addEffect(new MobEffectInstance(EFFECT,DURATION,0,false,true,true));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.theartofstockings.excitement"),true);
            PROGRESS.put(player.getUUID(),new Progress(null,ItemStack.EMPTY,0,now,now+COOLDOWN));
        } else PROGRESS.put(player.getUUID(),new Progress(target.getUUID(),continuing?previous.stockings:stockings.copy(),count,now,cooldown));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        var player=event.getEntity();if(player.level().isClientSide)return;
        LivingEntity target=null;
        if(player.isShiftKeyDown() && player.isAlive() && !player.isSpectator()) {
            double distance=Double.MAX_VALUE;
            for(var other:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(RANGE),
                    entity->entity!=player && (entity instanceof Player || entity instanceof ArmorStand))) {
                if(canSmell(player,other) && player.distanceToSqr(other)<distance) { target=other;distance=player.distanceToSqr(other); }
            }
        }
        update(player,target);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { PROGRESS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { PROGRESS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { PROGRESS.remove(event.getEntity().getUUID()); }
}
