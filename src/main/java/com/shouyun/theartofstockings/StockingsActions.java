package com.shouyun.theartofstockings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TheArtofStockings.MODID)
public final class StockingsActions {
    public static final int DURATION = 72;
    private static final Map<UUID, Action> ACTIONS = new HashMap<>();
    private record Action(boolean equip, InteractionHand hand, ItemStack source, ItemStack snapshot,
                          long started, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private StockingsActions() {}
    public static boolean isStockings(ItemStack stack) { return stack.getItem() instanceof StockingsItem; }
    public static ItemStack worn(LivingEntity entity) {
        if(entity instanceof Player player) {
            var separate=StockingsWear.get(player);
            if(!separate.isEmpty())return separate;
        }
        var legacy=entity.getItemBySlot(EquipmentSlot.LEGS);
        return isStockings(legacy)?legacy:ItemStack.EMPTY;
    }
    public static boolean wearing(LivingEntity entity) { return isStockings(worn(entity)); }
    public static boolean busy(Player player) { return !player.level().isClientSide && ACTIONS.containsKey(player.getUUID()); }
    public static void requestRemoval(Player player) {
        if (player.level().isClientSide || !player.isAlive() || player.isSpectator() || busy(player)) return;
        StockingsWear.migrateLegacy(player);
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && wearing(player))
            begin(player, false, InteractionHand.MAIN_HAND, StockingsWear.get(player));
    }
    public static void startEquip(Player player, InteractionHand hand) {
        if (player.level().isClientSide || !player.isAlive() || player.isSpectator() || busy(player)) return;
        if (!isStockings(player.getItemInHand(hand))) return;
        StockingsWear.migrateLegacy(player);
        if (wearing(player)) { message(player, "already_wearing"); return; }
        begin(player, true, hand, player.getItemInHand(hand));
    }
    private static void begin(Player player, boolean equip, InteractionHand hand, ItemStack source) {
        ACTIONS.put(player.getUUID(), new Action(equip, hand, source, source.copy(), player.level().getGameTime(), player.level().dimension()));
        broadcast(player, new StockingsNetwork.Animation(player.getId(), equip, source.copy(), 0, true));
        message(player, equip ? "putting_on" : "taking_off");
        sound(player, 0.8F);
    }
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        StockingsWear.migrateLegacy(player);
        Action action = ACTIONS.get(player.getUUID());
        if (action == null) return;
        ItemStack actual = action.equip ? player.getItemInHand(action.hand) : StockingsWear.get(player);
        boolean valid = player.isAlive() && !player.isSpectator() && player.level().dimension().equals(action.dimension)
                && actual == action.source && ItemStack.matches(actual, action.snapshot)
                && (!action.equip || !wearing(player));
        if (!valid) { cancel(player); return; }
        if (player.level().getGameTime() - action.started < DURATION) return;
        ACTIONS.remove(player.getUUID());
        if (action.equip) StockingsWear.set(player, actual.split(1));
        else {
            StockingsWear.set(player, ItemStack.EMPTY);
            returnItem(player, actual);
        }
        player.inventoryMenu.broadcastChanges();
        sound(player, 1.2F);
        message(player, action.equip ? "equipped" : "removed");
        // Let animation expire naturally while the attachment update reaches clients.
    }
    private static void returnItem(Player player, ItemStack stack) {
        // Unlike Inventory.add, this does not silently discard overflow in creative mode.
        player.getInventory().placeItemBackInInventory(stack);
    }
    @SubscribeEvent
    public static void removeWhileTargetingBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty() && wearing(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            if (!player.level().isClientSide) requestRemoval(player);
        }
    }
    public static void cancel(Player player) {
        if (ACTIONS.remove(player.getUUID()) != null) {
            broadcast(player, new StockingsNetwork.Animation(player.getId(), false, ItemStack.EMPTY, 0, false));
            message(player, "cancelled");
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ACTIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { StockingsWear.migrateLegacy(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { ACTIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer) {
            Action a = ACTIONS.get(target.getUUID());
            if (a != null) PacketDistributor.sendToPlayer(viewer, new StockingsNetwork.Animation(target.getId(), a.equip,
                    a.snapshot.copy(), (int)(target.level().getGameTime() - a.started), true));
        }
    }
    private static void broadcast(Player player, StockingsNetwork.Animation packet) {
        if (player instanceof ServerPlayer serverPlayer) PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }
    private static void sound(Player player, float pitch) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.65F, pitch);
    }
    private static void message(Player player, String key) { player.displayClientMessage(Component.translatable("message.theartofstockings." + key), true); }
}
