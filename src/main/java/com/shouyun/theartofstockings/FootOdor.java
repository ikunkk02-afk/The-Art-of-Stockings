package com.shouyun.theartofstockings;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = TheArtofStockings.MODID)
public final class FootOdor extends MobEffect {
    private static final int DAY_TICKS = 24_000;
    private static final int LINGER_TICKS = 1_200;
    private static final int REFRESH_INTERVAL = 20;
    private static final SimpleCommandExceptionType EFFECT_FAILED =
            new SimpleCommandExceptionType(Component.translatable("commands.effect.give.failed"));
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, TheArtofStockings.MODID);

    public static final DeferredHolder<MobEffect, FootOdor> EFFECT = EFFECTS.register("foot_odor", FootOdor::new);
    private static final Supplier<AttachmentType<Integer>> WEAR_TICKS =
            TheArtofStockings.ATTACHMENTS.register("foot_odor_wear_ticks",
                    () -> AttachmentType.builder(() -> 0).serialize(Codec.intRange(0, DAY_TICKS), ticks -> ticks > 0).build());

    private FootOdor() {
        super(MobEffectCategory.HARMFUL, 0x8B9B45);
    }

    static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    static boolean isScentedPlayer(LivingEntity entity) {
        return entity instanceof Player player && player.isAlive() && !player.isSpectator() && player.hasEffect(EFFECT);
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("footodor")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayerOrException();
                    if (!player.addEffect(new MobEffectInstance(EFFECT, LINGER_TICKS)) && !player.hasEffect(EFFECT)) {
                        throw EFFECT_FAILED.create();
                    }
                    source.sendSuccess(() -> Component.translatable("commands.theartofstockings.footodor.success"), false);
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.isAlive()) return;

        if (player.isSpectator() || !StockingsActions.wearing(player)
                || StockingsActions.isStockings(player.getItemBySlot(EquipmentSlot.LEGS))
                || !hasEquipment(player, EquipmentSlot.LEGS) || !hasEquipment(player, EquipmentSlot.FEET)) {
            if (player.hasData(WEAR_TICKS)) player.removeData(WEAR_TICKS);
            return;
        }

        MobEffectInstance current = player.getEffect(EFFECT);
        int ticks = player.getData(WEAR_TICKS);
        if (ticks == DAY_TICKS && current == null) ticks = 0;
        if (ticks < DAY_TICKS) {
            player.setData(WEAR_TICKS, ++ticks);
            if (ticks < DAY_TICKS) return;
        }

        if (current == null || current.endsWithin(LINGER_TICKS - REFRESH_INTERVAL)) {
            player.addEffect(new MobEffectInstance(EFFECT, LINGER_TICKS));
        }
    }

    private static boolean hasEquipment(Player player, EquipmentSlot slot) {
        var stack = player.getItemBySlot(slot);
        return !stack.isEmpty() && player.getEquipmentSlotForItem(stack) == slot;
    }
}
