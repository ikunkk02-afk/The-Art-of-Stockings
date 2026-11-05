package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.shouyun.theartofstockings.CustomStockingsTexture;
import com.shouyun.theartofstockings.TheArtofStockings;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/** A local draft: brush strokes update one owned texture; only Done returns PNG data to the palette. */
public final class StockingsPaintScreen extends Screen {
    private static final int W=580,H=330,CANVAS=256;
    private enum Tool { BRUSH,ERASER,FILL,PICKER }
    private final PaletteScreen parent;
    private final ItemStack expected;
    private final String initial;
    private final int opacity;
    private final Consumer<String> done;
    private final String token=previewToken();
    private StockingsCanvas canvas;
    private StockingsCanvas.Region region=StockingsCanvas.Region.FRONT;
    private Tool tool=Tool.BRUSH;
    private int rgb,size=3,alpha=255,x,y,viewX,viewY,viewW,viewH,lastX,lastY;
    private float scale=1;
    private boolean stroke,dirty=true,settingHex;
    private DynamicTexture dynamic;
    private ResourceLocation texture;
    private ArmorStand mannequin;
    private EditBox hex;
    private Button undo,redo;
    private Component status=Component.empty();
    private static String previewToken() {
        // Keep the local preview component serializable: entity appearance mods may inspect NBT.
        var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var id=java.util.UUID.randomUUID();
        image.setRGB(0,0,(int)id.getMostSignificantBits());image.setRGB(1,0,(int)(id.getMostSignificantBits()>>>32));
        image.setRGB(2,0,(int)id.getLeastSignificantBits());image.setRGB(3,0,(int)(id.getLeastSignificantBits()>>>32));
        try { return CustomStockingsTexture.encode(image); }
        catch(java.io.IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public StockingsPaintScreen(PaletteScreen parent,ItemStack expected,String initial,int opacity,int color,Consumer<String> done) {
        super(text("title"));this.parent=parent;this.expected=expected.copy();this.initial=initial;this.opacity=opacity;this.done=done;
        rgb=color==0xFFFFFF?0xDE719B:color;
    }
    private static Component text(String key) { return Component.translatable("gui.theartofstockings.paint."+key); }
    private Button button(int bx,int by,int width,Component label,Button.OnPress action) {
        return addRenderableWidget(Button.builder(label,action).bounds(x+bx,y+by,width,20).build());
    }
    @Override protected void init() {
        scale=Math.min(1,Math.min((width-8F)/W,(height-8F)/H));x=(int)((width/scale-W)/2);y=(int)((height/scale-H)/2);
        if(canvas==null)try {
            java.awt.image.BufferedImage source;
            if(initial.isEmpty())try(var stream=minecraft.getResourceManager().open(StockingsAssets.id("textures/entity/custom_stockings.png"))) {
                source=javax.imageio.ImageIO.read(stream);
            } else source=CustomStockingsTexture.decode(initial);
            canvas=new StockingsCanvas(source);
        } catch(Exception invalid) {
            canvas=new StockingsCanvas(new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB));status=text("load_failed");
        }
        for(int i=0;i<StockingsCanvas.Region.values().length;i++) {
            var choice=StockingsCanvas.Region.values()[i];
            button(282+(i%4)*70,34+(i/4)*24,66,text(choice.name().toLowerCase(java.util.Locale.ROOT)),b->{region=choice;updateView();});
        }
        for(int i=0;i<Tool.values().length;i++) {
            var choice=Tool.values()[i];
            button(282+i*70,86,66,text(choice.name().toLowerCase(java.util.Locale.ROOT)),b->{tool=choice;stroke=false;});
        }
        hex=addRenderableWidget(new EditBox(font,x+282,y+116,144,18,text("color")));hex.setMaxLength(7);
        hex.setResponder(value->{if(!settingHex && value.matches("#?[0-9a-fA-F]{6}"))rgb=Integer.parseInt(value.replace("#",""),16);});syncHex();
        for(int i=0;i<DyeColor.values().length;i++) {
            int color=DyeColor.values()[i].getTextureDiffuseColor()&0xFFFFFF;
            button(282+(i%8)*18,140+(i/8)*18,16,Component.literal("■").withColor(color),b->{rgb=color;syncHex();});
        }
        addRenderableWidget(new AbstractSliderButton(x+282,y+182,144,20,Component.empty(),(size-1)/15.0) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.translatable("gui.theartofstockings.paint.size",1+Math.round(value*15))); }
            @Override protected void applyValue() { size=1+(int)Math.round(value*15);updateMessage(); }
        });
        addRenderableWidget(new AbstractSliderButton(x+282,y+206,144,20,Component.empty(),alpha/255.0) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.translatable("gui.theartofstockings.paint.alpha",Math.round(value*100))); }
            @Override protected void applyValue() { alpha=(int)Math.round(value*255);updateMessage(); }
        });
        undo=button(282,232,68,text("undo"),b->{canvas.undo();dirty=true;});
        redo=button(358,232,68,text("redo"),b->{canvas.redo();dirty=true;});
        button(282,256,68,text("clear"),b->{canvas.clear(region);dirty=true;});
        button(358,256,68,text("restore"),b->{canvas.restore();dirty=true;});
        button(282,300,68,Component.translatable("gui.cancel"),b->onClose());
        button(356,300,86,text("export"),b->{
            try {
                var directory=minecraft.gameDirectory.toPath().resolve("config/theartofstockings");
                java.nio.file.Files.createDirectories(directory);
                var file=directory.resolve("stockings_drawing_"+java.util.UUID.randomUUID()+".png");
                java.nio.file.Files.write(file,java.util.Base64.getDecoder().decode(CustomStockingsTexture.encode(canvas.image())));
                net.minecraft.Util.getPlatform().openFile(directory.toFile());status=text("exported");
            } catch(Exception failed) { status=text("save_failed"); }
        });
        button(450,300,112,text("done"),b->{
            try { String png=CustomStockingsTexture.encode(canvas.image());done.accept(png);minecraft.setScreen(parent); }
            catch(Exception failed) { status=text("save_failed"); }
        });
        if(dynamic==null) {
            dynamic=new DynamicTexture(new NativeImage(256,256,true));texture=minecraft.getTextureManager().register("stockings_paint",dynamic);
            CustomTextureCache.local(token,texture);
        }
        mannequin=new ArmorStand(minecraft.level,0,0,0);mannequin.setNoBasePlate(true);mannequin.setShowArms(false);
        var pair=expected.copy();pair.set(DataComponents.DYED_COLOR,new DyedItemColor(0xFFFFFF,true));
        pair.set(TheArtofStockings.CUSTOM_TEXTURE.get(),token);pair.set(TheArtofStockings.OPACITY.get(),opacity);
        mannequin.setItemSlot(EquipmentSlot.LEGS,pair);dirty=true;updateView();
    }
    private void syncHex() { settingHex=true;if(hex!=null)hex.setValue(String.format("#%06X",rgb));settingHex=false; }
    private void updateView() {
        float zoom=Math.min(CANVAS/(float)region.w,CANVAS/(float)region.h);
        viewW=Math.round(region.w*zoom);viewH=Math.round(region.h*zoom);viewX=x+12+(CANVAS-viewW)/2;viewY=y+38+(CANVAS-viewH)/2;
    }
    private int[] point(double mx,double my) {
        if(mx<viewX || my<viewY || mx>=viewX+viewW || my>=viewY+viewH)return null;
        return new int[]{region.x+Math.min(region.w-1,(int)((mx-viewX)*region.w/viewW)),region.y+Math.min(region.h-1,(int)((my-viewY)*region.h/viewH))};
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        mx/=scale;my/=scale;var point=point(mx,my);
        if(point==null)return super.mouseClicked(mx,my,button);
        setFocused(null);
        if(button==1 || tool==Tool.PICKER) { rgb=canvas.sample(point[0],point[1])&0xFFFFFF;syncHex();return true; }
        if(button!=0)return false;
        if(tool==Tool.FILL)canvas.fill(point[0],point[1],(alpha<<24)|rgb,region);
        else { canvas.beginStroke();stroke=true;lastX=point[0];lastY=point[1];canvas.line(lastX,lastY,lastX,lastY,size,(alpha<<24)|rgb,tool==Tool.ERASER,region); }
        dirty=true;return true;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        mx/=scale;my/=scale;
        if(stroke && button==0) {
            var point=point(mx,my);
            if(point!=null) { canvas.line(lastX,lastY,point[0],point[1],size,(alpha<<24)|rgb,tool==Tool.ERASER,region);lastX=point[0];lastY=point[1];dirty=true; }
            return true;
        }
        return super.mouseDragged(mx,my,button,dx/scale,dy/scale);
    }
    @Override public boolean mouseReleased(double mx,double my,int button) { stroke=false;return super.mouseReleased(mx/scale,my/scale,button); }
    @Override public void mouseMoved(double mx,double my) { super.mouseMoved(mx/scale,my/scale); }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(hasControlDown() && !(getFocused() instanceof EditBox)) {
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Z) { canvas.undo();dirty=true;return true; }
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Y) { canvas.redo();dirty=true;return true; }
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() {
        CustomTextureCache.removeLocal(token);
        if(texture!=null)minecraft.getTextureManager().release(texture);
        dynamic=null;texture=null;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics,int mx,int my,float partial) { renderTransparentBackground(graphics); }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        if(dirty && dynamic!=null) {
            var image=dynamic.getPixels();
            for(int py=0;py<256;py++)for(int px=0;px<256;px++)image.setPixelRGBA(px,py,net.minecraft.util.FastColor.ABGR32.fromArgb32(canvas.sample(px,py)));
            dynamic.upload();FabricTextureCache.invalidate(texture);dirty=false;
        }
        renderBackground(g,mx,my,partial);g.pose().pushPose();g.pose().scale(scale,scale,1);mx=(int)(mx/scale);my=(int)(my/scale);
        g.fill(x,y,x+W,y+H,0xFF38333D);g.fill(x+2,y+2,x+W-2,y+H-2,0xFFC6C6C6);
        g.drawString(font,title,x+12,y+10,0x303030,false);
        g.drawString(font,text(region.name().toLowerCase(java.util.Locale.ROOT)),x+180,y+10,0x555555,false);
        g.drawString(font,text("hint"),x+12,y+24,0x505050,false);
        g.fill(x+10,y+36,x+270,y+296,0xFF505050);
        for(int py=0;py<CANVAS;py+=8)for(int px=0;px<CANVAS;px+=8)
            g.fill(x+12+px,y+38+py,x+20+px,y+46+py,((px/8+py/8)&1)==0?0xFFBCBCBC:0xFFEEEEEE);
        g.blit(texture,viewX,viewY,viewW,viewH,region.x,region.y,region.w,region.h,256,256);
        g.fill(x+432,y+111,x+567,y+280,0xFF777777);g.flush();
        if(mannequin!=null) {
            g.pose().pushPose();g.pose().scale(1/scale,1/scale,1);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,Math.round((x+433)*scale),Math.round((y+112)*scale),
                    Math.round((x+566)*scale),Math.round((y+279)*scale),Math.round(105*scale),-.48F,.55F,0,mannequin);
            g.pose().popPose();
        }
        g.drawString(font,text(tool.name().toLowerCase(java.util.Locale.ROOT)),x+450,y+285,0x404040,false);
        g.drawString(font,font.plainSubstrByWidth(status.getString(),250),x+12,y+304,0xAA2222,false);
        undo.active=canvas.canUndo();redo.active=canvas.canRedo();
        for(Renderable widget:renderables)widget.render(g,mx,my,partial);
        g.pose().popPose();
    }
}
