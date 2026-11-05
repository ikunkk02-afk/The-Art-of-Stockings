package com.shouyun.theartofstockings;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** An aura from actual leg/full-body clothing, using native or standalone storage. */
@EventBusSubscriber(modid=TheArtofStockings.MODID)
public final class Charm extends MobEffect {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,TheArtofStockings.MODID);
    public static final DeferredHolder<MobEffect,Charm> EFFECT=EFFECTS.register("charm",Charm::new);
    public static final double RANGE=16;
    private Charm() { super(MobEffectCategory.BENEFICIAL,0xDC5598); }
    static void register(IEventBus bus) { EFFECTS.register(bus); }
    public static boolean active(Player player) { return player.isAlive() && !player.isSpectator() && player.hasEffect(EFFECT); }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        Player player=event.getEntity();
        if(player.level().isClientSide)return;
        boolean wearing=player.isAlive() && !player.isSpectator() && StockingsActions.isStockings(StockingsWear.get(player));
        if(wearing) {
            if(!player.hasEffect(EFFECT))player.addEffect(new MobEffectInstance(EFFECT,-1,0,false,true,true));
        } else if(player.hasEffect(EFFECT))player.removeEffect(EFFECT);
    }
    public static void discount(Villager villager,Player player) {
        if(!active(player))return;
        for(var offer:villager.getOffers()) {
            int reduction=Math.max(1,(int)Math.floor(offer.getBaseCostA().getCount()*.20));
            offer.addToSpecialPriceDiff(-reduction);
        }
    }
}
