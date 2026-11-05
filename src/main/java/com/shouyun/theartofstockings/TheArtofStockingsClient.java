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
        StockingsNetwork.wearReceiver = p -> Minecraft.getInstance().setScreen(new com.shouyun.theartofstockings.client.WearScreen(p));
        StockingsNetwork.paletteReceiver = p -> Minecraft.getInstance().setScreen(new com.shouyun.theartofstockings.client.PaletteScreen(p));
        StockingsNetwork.paletteResultReceiver = p -> {
            if(Minecraft.getInstance().screen instanceof com.shouyun.theartofstockings.client.PaletteScreen screen)screen.receive(p);
        };
        bus.addListener(this::reloadListeners);
        bus.addListener(this::colors);
        bus.addListener(this::layers);
    }
    private void reloadListeners(RegisterClientReloadListenersEvent event) { event.registerReloadListener(new StockingsAssets()); }
    private void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            boolean deeper=StockingsItem.style(stack)==StockingsItem.Style.STRIPED || StockingsItem.style(stack)==StockingsItem.Style.FOX || StockingsItem.style(stack)==StockingsItem.Style.CAT_PAW;
            float highlight=deeper?.08F:.28F;
            if (tint == 0) return FabricDepth.tint(StockingsItem.mix(StockingsItem.color(stack), 0xFFFFFFFF, highlight),StockingsItem.depth(stack));
            if (tint == 1 && StockingsItem.twoColors(stack)) {
                return FabricDepth.tint(StockingsItem.mix(StockingsItem.secondaryColor(stack), 0xFFFFFFFF, highlight),StockingsItem.depth(stack));
            }
            return -1;
        }, TheArtofStockings.STOCKINGS.get(), TheArtofStockings.GRADIENT.get(), TheArtofStockings.PANTYHOSE.get(),
                TheArtofStockings.FISHNET.get(), TheArtofStockings.GLOW.get(),TheArtofStockings.STRIPED.get(),
                TheArtofStockings.CAT_PAW.get(),TheArtofStockings.MATTE.get(),TheArtofStockings.FOX.get(),TheArtofStockings.CUSTOM.get(),TheArtofStockings.BODYSTOCKING.get());
    }
    private void layers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new StockingsLayer<>(renderer,skin.toString().equalsIgnoreCase("slim")));
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
                && player.getMainHandItem().isEmpty() && StockingsWear.any(player)
                && ClientAnimations.get(player) == null)
            PacketDistributor.sendToServer(new StockingsNetwork.Remove());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var animation = ClientAnimations.get(mc.player);
        if (animation == null) return;
        int x = event.getGuiGraphics().guiWidth() / 2 - 56;
        int y = event.getGuiGraphics().guiHeight() / 2 + 24;
        var graphics=event.getGuiGraphics();float progress=animation.progress(mc.player,0);
        graphics.fill(x+2,y+3,x+110,y+11,0xB02C2430);
        graphics.fill(x+3,y+4,x+3+(int)(106*progress),y+10,animation.equipping()?0xFFD77BA7:0xFFB898C3);
        graphics.blit(StockingsAssets.id("textures/gui/dressing_progress.png"),x,y,0,0,112,14,112,14);
        var label=net.minecraft.network.chat.Component.translatable(animation.equipping()?"hud.theartofstockings.equipping":"hud.theartofstockings.removing",Math.round(progress*100));
        graphics.drawCenteredString(mc.font,label,x+56,y-11,0xFFF0DBE7);
    }
}
