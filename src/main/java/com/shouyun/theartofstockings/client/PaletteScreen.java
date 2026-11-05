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
    private static final int W=370,H=262;
    private static final ResourceLocation INVENTORY=ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private final int slot;
    private final ItemStack expected;
    private final java.util.UUID session;
    private final int[] colors,original;
    private final boolean gradient;
    private final boolean custom;
    private final int originalOpacity;
    private final String originalTexture;
    private int opacity;
    private final int originalDepth;
    private int depth;
    private Depth depthSlider;
    private String texture;
    private Component textureStatus=Component.empty();
    private Opacity opacitySlider;
    private final Channel[] channels=new Channel[3];
    private int selected,x,y;
    private float uiScale=1;
    private boolean syncing,validHex=true;
    private EditBox hex;
    private Button apply,topButton,bottomButton;
    private ArmorStand mannequin;
    private boolean sending;
    private int sendingTicks;

    public PaletteScreen(StockingsNetwork.OpenPalette packet) {
        super(text("title"));slot=packet.slot();expected=packet.stack().copy();session=packet.session();
        gradient=StockingsItem.twoColors(expected);
        custom=StockingsItem.style(expected)==StockingsItem.Style.CUSTOM;
        opacity=originalOpacity=StockingsItem.opacity(expected);
        depth=originalDepth=StockingsItem.depth(expected);
        texture=originalTexture=expected.getOrDefault(TheArtofStockings.CUSTOM_TEXTURE.get(),"");
        colors=new int[]{StockingsItem.color(expected)&0xFFFFFF,StockingsItem.secondaryColor(expected)&0xFFFFFF};
        original=colors.clone();
    }
    private static Component text(String key) { return Component.translatable("gui.theartofstockings.palette."+key); }
    private Button button(int bx,int by,int w,int h,Component label,Button.OnPress action) {
        return addRenderableWidget(Button.builder(label,action).bounds(x+bx,y+by,w,h).build());
    }
    @Override protected void init() {
        uiScale=Math.min(1,Math.min((width-8F)/W,(height-8F)/H));
        x=(int)((width/uiScale-W)/2);y=(int)((height/uiScale-H)/2);hex=null;apply=null;topButton=null;bottomButton=null;
        if(gradient) {
            topButton=button(14,36,80,20,endpoint(false),b->select(0));
            bottomButton=button(98,36,80,20,endpoint(true),b->select(1));
            button(182,36,20,20,Component.literal("⇄"),b->{
                int c=colors[0];colors[0]=colors[1];colors[1]=c;sync();
            }).setTooltip(Tooltip.create(text("swap")));
        }
        for(int i=0;i<3;i++)channels[i]=addRenderableWidget(new Channel(x+14,y+59+i*21,i));
        opacitySlider=addRenderableWidget(new Opacity(x+14,y+122));
        opacitySlider.setTooltip(Tooltip.create(text("opacity_hint")));
        depthSlider=addRenderableWidget(new Depth(x+14,y+145));
        depthSlider.setTooltip(Tooltip.create(text("depth_hint")));
        hex=addRenderableWidget(new EditBox(font,x+14,y+179,100,18,text("hex")));
        hex.setMaxLength(7);
        hex.setResponder(value->{
            if(syncing)return;
            validHex=value.matches("#?[0-9a-fA-F]{6}");
            if(apply!=null)apply.active=validHex && !sending;
            hex.setTextColor(validHex?0xFFFFFF:0xFF7777);
            if(validHex) {
                colors[selected]=Integer.parseInt(value.replace("#",""),16);
                for(var channel:channels)if(channel!=null)channel.sync();updatePreview();
            }
        });
        DyeColor[] dyes=DyeColor.values();
        for(int i=0;i<dyes.length;i++) {
            DyeColor dye=dyes[i];int color=dye.getTextureDiffuseColor()&0xFFFFFF;
            var swatch=button(14+(i%8)*23,201+(i/8)*16,21,14,Component.literal("■").withColor(color),b->{
                colors[selected]=color;sync();
            });
            swatch.setTooltip(Tooltip.create(Component.translatable("color.minecraft."+dye.getName())));
        }
        if(custom) {
            button(214,149,69,20,text("import"),b->{
                String file=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(text("import").getString(),
                        minecraft.gameDirectory.toPath().resolve("config/theartofstockings/").toAbsolutePath().toString()+java.io.File.separator,null,"PNG",false);
                if(file!=null)importTexture(java.nio.file.Path.of(file));
            });
            button(287,149,69,20,text("clear"),b->{texture="";textureStatus=Component.empty();updatePreview();});
            button(214,173,69,20,text("paint"),b->minecraft.setScreen(new StockingsPaintScreen(this,expected,texture,opacity,colors[0],png->{
                texture=png;colors[0]=0xFFFFFF;textureStatus=text("painted");sync();
            })));
            button(287,173,69,20,text("template_short"),b->exportTemplate());
        }
        button(14,238,54,20,Component.translatable("gui.cancel"),b->onClose());
        button(74,238,54,20,text("reset"),b->{colors[0]=original[0];colors[1]=original[1];opacity=originalOpacity;depth=originalDepth;texture=originalTexture;textureStatus=Component.empty();sync();});
        apply=button(260,238,96,20,text("apply"),b->{
            save();
        });
        if(minecraft.level!=null) {
            mannequin=new ArmorStand(minecraft.level,0,0,0);mannequin.setNoBasePlate(true);mannequin.setShowArms(true);
        }
        sync();
    }
    private void select(int endpoint) { selected=endpoint;sync(); }
    private Component endpoint(boolean second) {
        return text(StockingsItem.style(expected)==StockingsItem.Style.GRADIENT ? (second?"bottom":"top") : (second?"detail":"base"));
    }
    private void sync() {
        syncing=true;validHex=true;
        if(hex!=null){hex.setValue(String.format("#%06X",colors[selected]));hex.setTextColor(0xFFFFFF);}
        for(var channel:channels)if(channel!=null)channel.sync();
        if(opacitySlider!=null)opacitySlider.sync();
        if(depthSlider!=null)depthSlider.sync();
        if(topButton!=null)topButton.setMessage(endpoint(false).copy().withStyle(selected==0?ChatFormatting.YELLOW:ChatFormatting.WHITE));
        if(bottomButton!=null)bottomButton.setMessage(endpoint(true).copy().withStyle(selected==1?ChatFormatting.YELLOW:ChatFormatting.WHITE));
        if(apply!=null)apply.active=!sending;updatePreview();syncing=false;
    }
    private void updatePreview() {
        var pair=expected.copy();pair.set(DataComponents.DYED_COLOR,new DyedItemColor(colors[0],true));
        if(gradient)pair.set(TheArtofStockings.SECONDARY_COLOR.get(),colors[1]);
        pair.set(TheArtofStockings.OPACITY.get(),opacity);
        pair.set(TheArtofStockings.DEPTH.get(),depth);
        if(texture.isEmpty())pair.remove(TheArtofStockings.CUSTOM_TEXTURE.get());
        else pair.set(TheArtofStockings.CUSTOM_TEXTURE.get(),texture);
        if(mannequin!=null)mannequin.setItemSlot(EquipmentSlot.LEGS,pair);
    }
    private final class Opacity extends AbstractSliderButton {
        Opacity(int x,int y) { super(x,y,184,20,Component.empty(),0);sync(); }
        void sync() { value=opacity/100.0;updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("gui.theartofstockings.palette.opacity",Math.round(value*100))); }
        @Override protected void applyValue() { opacity=(int)Math.round(value*100);updateMessage();updatePreview(); }
    }
    private final class Depth extends AbstractSliderButton {
        Depth(int x,int y) { super(x,y,184,20,Component.empty(),0);sync(); }
        void sync() { value=depth/100.0;updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("gui.theartofstockings.palette.depth",Math.round(value*100))); }
        @Override protected void applyValue() { depth=(int)Math.round(value*100);updateMessage();updatePreview(); }
    }
    @Override public void onFilesDrop(java.util.List<java.nio.file.Path> files) {
        if(custom && files.size()==1)importTexture(files.getFirst());
    }
    private void importTexture(java.nio.file.Path file) {
        try {
            if(java.nio.file.Files.size(file)>com.shouyun.theartofstockings.CustomStockingsTexture.MAX_IMPORT_BYTES)
                throw new com.shouyun.theartofstockings.CustomStockingsTexture.ImportFailure("too_large");
            texture=com.shouyun.theartofstockings.CustomStockingsTexture.normalize(java.nio.file.Files.readAllBytes(file));
            colors[0]=0xFFFFFF;textureStatus=text("imported");sync();
        } catch(com.shouyun.theartofstockings.CustomStockingsTexture.ImportFailure invalid) { importError(invalid.reason); }
        catch(Exception unreadable) { importError("unreadable"); }
    }
    private void importError(String reason) {
        textureStatus=text("import_"+reason).copy().withStyle(ChatFormatting.RED);
        if(minecraft.player!=null)minecraft.player.displayClientMessage(textureStatus,false);
    }
    private void save() {
        if(sending || !validHex)return;
        byte[] bytes=java.util.Base64.getDecoder().decode(texture);
        int total=Math.max(1,(bytes.length+com.shouyun.theartofstockings.PaletteEdits.CHUNK_BYTES-1)/com.shouyun.theartofstockings.PaletteEdits.CHUNK_BYTES);
        sending=true;sendingTicks=0;apply.active=false;textureStatus=text("saving");
        for(int i=0;i<total;i++) {
            int start=i*com.shouyun.theartofstockings.PaletteEdits.CHUNK_BYTES;
            var chunk=java.util.Arrays.copyOfRange(bytes,start,Math.min(bytes.length,start+com.shouyun.theartofstockings.PaletteEdits.CHUNK_BYTES));
            PacketDistributor.sendToServer(new StockingsNetwork.PaletteChunk(session,colors[0],colors[1],opacity,depth,i,total,chunk));
        }
    }
    public void receive(StockingsNetwork.PaletteResult result) {
        if(!session.equals(result.session()))return;
        if(result.success()) { onClose();return; }
        sending=false;apply.active=false;textureStatus=text("save_failed").copy().withStyle(ChatFormatting.RED);
        if(minecraft.player!=null)minecraft.player.displayClientMessage(textureStatus,false);
    }
    @Override public void tick() {
        if(sending && ++sendingTicks>300) { sending=false;apply.active=true;textureStatus=text("save_timeout").copy().withStyle(ChatFormatting.RED); }
    }
    private void exportTemplate() {
        try {
            var directory=minecraft.gameDirectory.toPath().resolve("config/theartofstockings");
            java.nio.file.Files.createDirectories(directory);
            var target=directory.resolve("custom_stockings_template.png");
            // Never replace artwork the player may have edited after a previous export.
            if(!java.nio.file.Files.exists(target))try(var input=minecraft.getResourceManager().open(StockingsAssets.id("textures/entity/custom_stockings.png"))) {
                java.nio.file.Files.copy(input,target);
            }
            net.minecraft.Util.getPlatform().openFile(directory.toFile());
        } catch(Exception failed) { textureStatus=text("template_error").copy().withStyle(ChatFormatting.RED); }
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
    @Override public boolean mouseClicked(double mx,double my,int button) { return !sending && super.mouseClicked(mx/uiScale,my/uiScale,button); }
    @Override public boolean mouseReleased(double mx,double my,int button) { return !sending && super.mouseReleased(mx/uiScale,my/uiScale,button); }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) { return !sending && super.mouseDragged(mx/uiScale,my/uiScale,button,dx/uiScale,dy/uiScale); }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy) { return super.mouseScrolled(mx/uiScale,my/uiScale,sx,sy); }
    @Override public void mouseMoved(double mx,double my) { super.mouseMoved(mx/uiScale,my/uiScale); }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {
        renderTransparentBackground(g);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        renderBackground(g,mouseX,mouseY,partial);
        g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);
        mouseX=(int)(mouseX/uiScale);mouseY=(int)(mouseY/uiScale);panel(g);
        g.drawString(font,title,x+14,y+10,0x404040,false);
        g.drawString(font,font.plainSubstrByWidth(expected.getHoverName().getString(),278),x+14,y+24,0x606060,false);
        if(!gradient)g.drawString(font,text("solid"),x+14,y+43,0x404040,false);
        g.drawString(font,text("preview"),x+216,y+40,0x404040,false);
        inset(g,x+214,y+53,142,custom?90:154);
        g.flush();
        if(mannequin!=null) {
            // Vanilla's scissor coordinates ignore the pose matrix; pass physical GUI coordinates.
            g.pose().pushPose();g.pose().scale(1/uiScale,1/uiScale,1);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,Math.round((x+215)*uiScale),Math.round((y+54)*uiScale),
                    Math.round((x+355)*uiScale),Math.round((y+(custom?142:206))*uiScale),Math.round((custom?76:106)*uiScale),-.48F,.55F,0,mannequin);
            g.pose().popPose();
        }
        g.drawString(font,validHex?Component.literal("HEX"):text("invalid"),x+14,y+169,validHex?0x404040:0xAA2222,false);
        inset(g,x+120,y+179,78,18);
        g.fill(x+121,y+180,x+197,y+196,0xFF000000|colors[selected]);
        if(custom)g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(textureStatus,142)),x+214,y+196,0x404040,false);
        // Do not call Screen.render: it would invoke the background a second time.
        for(Renderable widget:renderables)widget.render(g,mouseX,mouseY,partial);
        g.pose().popPose();
    }
}
