package com.shouyun.theartofstockings;

import com.shouyun.theartofstockings.client.ClientAnimations;
import com.shouyun.theartofstockings.client.StockingsLayer;
import com.shouyun.theartofstockings.client.StockingsAssets;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = TheArtofStockings.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TheArtofStockings.MODID, value = Dist.CLIENT)
public final class TheArtofStockingsClient {
    public TheArtofStockingsClient(IEventBus bus) {
        StockingsNetwork.clientReceiver = ClientAnimations::receive;
        StockingsNetwork.paletteReceiver = p -> Minecraft.getInstance().setScreen(new com.shouyun.theartofstockings.client.PaletteScreen(p));
        bus.addListener(this::reloadListeners);
        bus.addListener(this::colors);
        bus.addListener(this::layers);
    }
    private void reloadListeners(RegisterClientReloadListenersEvent event) { event.registerReloadListener(new StockingsAssets()); }
    private void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            if (tint == 0) return StockingsItem.mix(StockingsItem.color(stack), 0xFFFFFFFF, 0.28F);
            if (tint == 1 && stack.is(TheArtofStockings.GRADIENT.get())) {
                return StockingsItem.mix(StockingsItem.secondaryColor(stack), 0xFFFFFFFF, 0.28F);
            }
            return -1;
        }, TheArtofStockings.STOCKINGS.get(), TheArtofStockings.GRADIENT.get(), TheArtofStockings.PANTYHOSE.get(),
                TheArtofStockings.FISHNET.get(), TheArtofStockings.GLOW.get());
    }
    private void layers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new StockingsLayer<>(renderer));
        }
        ArmorStandRenderer stand = event.getRenderer(EntityType.ARMOR_STAND);
        if (stand != null) stand.addLayer(new StockingsLayer<>(stand));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ClientAnimations.clean();
    }
    @SubscribeEvent public static void emptyRightClick(PlayerInteractEvent.RightClickEmpty event) {
        var player = event.getEntity();
        if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty() && StockingsActions.wearing(player)
                && ClientAnimations.get(player) == null)
            PacketDistributor.sendToServer(new StockingsNetwork.Remove());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var animation = ClientAnimations.get(mc.player);
        if (animation == null) return;
        int x = event.getGuiGraphics().guiWidth() / 2 - 40;
        int y = event.getGuiGraphics().guiHeight() / 2 + 24;
        event.getGuiGraphics().fill(x, y, x + 80, y + 3, 0xA0302938);
        event.getGuiGraphics().fill(x, y, x + (int)(80 * animation.progress(mc.player, 0)), y + 3, 0xFFE9B9D5);
    }
}
