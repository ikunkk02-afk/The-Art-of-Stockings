package com.shouyun.theartofstockings.client;

import com.shouyun.theartofstockings.StockingsItem;
import com.shouyun.theartofstockings.StockingsNetwork;
import com.shouyun.theartofstockings.TheArtofStockings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla widgets and container frame; changes remain local until Apply. */
public final class PaletteScreen extends Screen {
    private static final int W=316,H=238;
    private static final ResourceLocation INVENTORY=ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private final int slot;
    private final ItemStack expected;
    private final int[] colors,original;
    private final boolean gradient;
    private final Channel[] channels=new Channel[3];
    private int selected,x,y;
    private boolean syncing,validHex=true;
    private EditBox hex;
    private Button apply,topButton,bottomButton;
    private ArmorStand mannequin;

    public PaletteScreen(StockingsNetwork.OpenPalette packet) {
        super(text("title"));slot=packet.slot();expected=packet.stack().copy();
        gradient=StockingsItem.style(expected)==StockingsItem.Style.GRADIENT;
        colors=new int[]{StockingsItem.color(expected)&0xFFFFFF,StockingsItem.secondaryColor(expected)&0xFFFFFF};
        original=colors.clone();
    }
    private static Component text(String key) { return Component.translatable("gui.theartofstockings.palette."+key); }
    private Button button(int bx,int by,int w,int h,Component label,Button.OnPress action) {
        return addRenderableWidget(Button.builder(label,action).bounds(x+bx,y+by,w,h).build());
    }
    @Override protected void init() {
        x=(width-W)/2;y=(height-H)/2;hex=null;apply=null;topButton=null;bottomButton=null;
        if(gradient) {
            topButton=button(14,40,80,20,text("top"),b->select(0));
            bottomButton=button(98,40,80,20,text("bottom"),b->select(1));
            button(182,40,20,20,Component.literal("⇄"),b->{
                int c=colors[0];colors[0]=colors[1];colors[1]=c;sync();
            }).setTooltip(Tooltip.create(text("swap")));
        }
        for(int i=0;i<3;i++)channels[i]=addRenderableWidget(new Channel(x+14,y+65+i*23,i));
        hex=addRenderableWidget(new EditBox(font,x+14,y+145,100,18,text("hex")));
        hex.setMaxLength(7);
        hex.setResponder(value->{
            if(syncing)return;
            validHex=value.matches("#?[0-9a-fA-F]{6}");
            if(apply!=null)apply.active=validHex;
            hex.setTextColor(validHex?0xFFFFFF:0xFF7777);
            if(validHex) {
                colors[selected]=Integer.parseInt(value.replace("#",""),16);
                for(var channel:channels)if(channel!=null)channel.sync();updatePreview();
            }
        });
        DyeColor[] dyes=DyeColor.values();
        for(int i=0;i<dyes.length;i++) {
            DyeColor dye=dyes[i];int color=dye.getTextureDiffuseColor()&0xFFFFFF;
            var swatch=button(14+(i%8)*23,168+(i/8)*18,21,16,Component.literal("■").withColor(color),b->{
                colors[selected]=color;sync();
            });
            swatch.setTooltip(Tooltip.create(Component.translatable("color.minecraft."+dye.getName())));
        }
        button(14,210,54,20,Component.translatable("gui.cancel"),b->onClose());
        button(74,210,54,20,text("reset"),b->{colors[0]=original[0];colors[1]=original[1];sync();});
        apply=button(206,210,96,20,text("apply"),b->{
            PacketDistributor.sendToServer(new StockingsNetwork.ApplyPalette(slot,expected,colors[0],colors[1]));onClose();
        });
        if(minecraft.level!=null) {
            mannequin=new ArmorStand(minecraft.level,0,0,0);mannequin.setNoBasePlate(true);mannequin.setShowArms(false);
        }
        sync();
    }
    private void select(int endpoint) { selected=endpoint;sync(); }
    private void sync() {
        syncing=true;validHex=true;
        if(hex!=null){hex.setValue(String.format("#%06X",colors[selected]));hex.setTextColor(0xFFFFFF);}
        for(var channel:channels)if(channel!=null)channel.sync();
        if(topButton!=null)topButton.setMessage(text("top").copy().withStyle(selected==0?ChatFormatting.YELLOW:ChatFormatting.WHITE));
        if(bottomButton!=null)bottomButton.setMessage(text("bottom").copy().withStyle(selected==1?ChatFormatting.YELLOW:ChatFormatting.WHITE));
        if(apply!=null)apply.active=true;updatePreview();syncing=false;
    }
    private void updatePreview() {
        var pair=expected.copy();pair.set(DataComponents.DYED_COLOR,new DyedItemColor(colors[0],true));
        if(gradient)pair.set(TheArtofStockings.SECONDARY_COLOR.get(),colors[1]);
        if(mannequin!=null)mannequin.setItemSlot(EquipmentSlot.LEGS,pair);
    }
    private final class Channel extends AbstractSliderButton {
        private final int index;
        Channel(int x,int y,int index) { super(x,y,184,20,Component.empty(),0);this.index=index;sync(); }
        void sync() { value=((colors[selected]>>(16-index*8))&255)/255.0;updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("RGB".charAt(index)+": "+Math.round(value*255))); }
        @Override protected void applyValue() {
            int shift=16-index*8;
            colors[selected]=(colors[selected]&~(255<<shift))|((int)Math.round(value*255)<<shift);
            PaletteScreen.this.sync();
        }
    }
    private static void inset(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,0xFF373737);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFF8B8B8B);
        g.fill(x,y+h-1,x+w,y+h,0xFFFFFFFF);g.fill(x+w-1,y,x+w,y+h,0xFFFFFFFF);
    }
    private void panel(GuiGraphics g) {
        g.fill(x+3,y+3,x+W-3,y+H-3,0xFFC6C6C6);
        // Reuse vanilla inventory's four corners and continuous border pixels.
        g.blit(INVENTORY,x,y,0,0,4,4,256,256);
        g.blit(INVENTORY,x+W-4,y,172,0,4,4,256,256);
        g.blit(INVENTORY,x,y+H-4,0,162,4,4,256,256);
        g.blit(INVENTORY,x+W-4,y+H-4,172,162,4,4,256,256);
        for(int i=4;i<W-4;i++) {
            g.blit(INVENTORY,x+i,y,4,0,1,4,256,256);
            g.blit(INVENTORY,x+i,y+H-4,4,162,1,4,256,256);
        }
        for(int i=4;i<H-4;i++) {
            g.blit(INVENTORY,x,y+i,0,4,4,1,256,256);
            g.blit(INVENTORY,x+W-4,y+i,172,4,4,1,256,256);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {
        renderTransparentBackground(g);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        renderBackground(g,mouseX,mouseY,partial);panel(g);
        g.drawString(font,title,x+14,y+10,0x404040,false);
        g.drawString(font,font.plainSubstrByWidth(expected.getHoverName().getString(),278),x+14,y+24,0x606060,false);
        if(!gradient)g.drawString(font,text("solid"),x+14,y+47,0x404040,false);
        g.drawString(font,text("preview"),x+216,y+51,0x404040,false);
        inset(g,x+214,y+65,88,138);
        g.flush();
        if(mannequin!=null)InventoryScreen.renderEntityInInventoryFollowsAngle(g,x+215,y+66,x+301,y+202,96,-.48F,.55F,0,mannequin);
        g.drawString(font,validHex?Component.literal("HEX"):text("invalid"),x+14,y+135,validHex?0x404040:0xAA2222,false);
        inset(g,x+120,y+145,78,18);
        g.fill(x+121,y+146,x+197,y+162,0xFF000000|colors[selected]);
        // Do not call Screen.render: it would invoke the background a second time.
        for(Renderable widget:renderables)widget.render(g,mouseX,mouseY,partial);
    }
}
