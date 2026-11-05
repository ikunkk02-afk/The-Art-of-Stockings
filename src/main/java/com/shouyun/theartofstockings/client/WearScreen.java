package com.shouyun.theartofstockings.client;

import com.shouyun.theartofstockings.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A small vanilla menu, opened by crouch-use, for independent clothing locations. */
public final class WearScreen extends Screen {
    private final StockingsNetwork.OpenWear request;
    public WearScreen(StockingsNetwork.OpenWear request) {
        super(Component.translatable("gui.theartofstockings.wear."+(request.equip()?"equip":"remove")));this.request=request;
    }
    @Override protected void init() {
        if(minecraft.player==null)return;
        int y=height/2-43;
        for(var slot:WearSlot.values()) {
            var worn=StockingsWear.get(minecraft.player,slot);
            var held=minecraft.player.getItemInHand(request.hand());
            Component label=Component.translatable(slot.key());
            if(slot==WearSlot.LEGS && StockingsItem.style(request.equip()?held:worn)==StockingsItem.Style.BODYSTOCKING)
                label=Component.translatable("gui.theartofstockings.wear.full_body");
            if(!request.equip() && !worn.isEmpty()) label=label.copy().append(" · ").append(worn.getHoverName());
            var button=addRenderableWidget(Button.builder(label,b->{
                var expected=(request.equip()?minecraft.player.getItemInHand(request.hand()):StockingsWear.get(minecraft.player,slot)).copy();
                PacketDistributor.sendToServer(new StockingsNetwork.WearRequest(slot.ordinal(),request.hand(),request.equip(),expected));onClose();
            }).bounds(width/2-125,y,250,20).build());
            button.active=request.equip()?worn.isEmpty() && (StockingsItem.style(held)!=StockingsItem.Style.BODYSTOCKING || slot==WearSlot.LEGS):!worn.isEmpty();
            y+=24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(width/2-60,y+6,120,20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        super.render(g,mx,my,partial);
        g.drawCenteredString(font,title,width/2,height/2-67,0xFFFFFF);
    }
}
