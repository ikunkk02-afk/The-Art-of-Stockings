package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.theartofstockings.StockingsActions;
import com.shouyun.theartofstockings.StockingsItem;
import com.shouyun.theartofstockings.StockingsWear;
import com.shouyun.theartofstockings.WearSlot;
import com.shouyun.theartofstockings.TheArtofStockings;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class StockingsLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation PAW_TEXTURE = StockingsAssets.id("textures/entity/cat_paw_overlay.png");
    private static final java.util.Map<StockingsItem.Style,ResourceLocation> FABRICS = new java.util.EnumMap<>(StockingsItem.Style.class);
    private static final java.util.Map<StockingsItem.Style,ResourceLocation> SHEENS = new java.util.EnumMap<>(StockingsItem.Style.class);
    static {
        for (var style:StockingsItem.Style.values()) {
            String name=StockingsAssets.modelName(style);
            FABRICS.put(style,StockingsAssets.id("textures/entity/"+name+".png"));
            SHEENS.put(style,StockingsAssets.id("textures/entity/"+name+"_sheen.png"));
        }
    }
    private final boolean slim;
    public StockingsLayer(RenderLayerParent<T, M> parent) { this(parent,false); }
    public StockingsLayer(RenderLayerParent<T, M> parent,boolean slim) { super(parent);this.slim=slim; }
    @Override public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                       float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (entity.isSpectator() || StockingsAssets.current == null) return;
        if (entity instanceof ArmorStand stand) { if (stand.isMarker()) return; }
        else if (entity.isInvisible()) return;
        var state=ClientAnimations.get(entity);
        for(var slot:WearSlot.values()) {
            var active=state!=null && state.slot()==slot?state:null;
            var stack=active!=null?active.stack():entity instanceof net.minecraft.world.entity.player.Player player
                    ?slot==WearSlot.LEGS?StockingsWear.visual(player):StockingsWear.get(player,slot)
                    :slot==WearSlot.LEGS?StockingsActions.worn(entity):net.minecraft.world.item.ItemStack.EMPTY;
            if(StockingsActions.isStockings(stack))renderStack(pose,buffer,light,entity,partial,slot,stack,active);
        }
    }
    private void renderStack(PoseStack pose,MultiBufferSource buffer,int light,T entity,float partial,WearSlot slot,
                             net.minecraft.world.item.ItemStack stack,ClientAnimations.State state) {
        var style=StockingsItem.style(stack);
        int color=StockingsItem.color(stack), bottom=StockingsItem.twoColors(stack)?StockingsItem.secondaryColor(stack):color;
        float opacity=StockingsItem.opacity(stack)/100F;int depth=StockingsItem.depth(stack);
        if(opacity==0)return;
        var fabric=style==StockingsItem.Style.CUSTOM ? CustomTextureCache.get(stack.getOrDefault(com.shouyun.theartofstockings.TheArtofStockings.CUSTOM_TEXTURE.get(),"")) : FABRICS.get(style);
        int fabricDepth=style==StockingsItem.Style.CAT_PAW?15+Math.round(depth*.85F):depth;
        fabric=FabricTextureCache.get(fabric,stack.getOrDefault(TheArtofStockings.CUSTOM_TEXTURE.get(),""),fabricDepth,StockingsItem.opacity(stack));
        if(style==StockingsItem.Style.GLOW) light=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        float right=state==null?1:state.legAmount(entity,partial,false);
        float left=state==null?1:state.legAmount(entity,partial,true);
        pose.pushPose();
        if(getParentModel().young) { pose.scale(.5F,.5F,.5F);pose.translate(0,1.5F,0); }
        if(slot==WearSlot.LEGS) {
        drawPart(pose,buffer,light,getParentModel().rightLeg,false,false,style,color,bottom,right,opacity,fabric,depth);
        drawPart(pose,buffer,light,getParentModel().leftLeg,true,false,style,color,bottom,left,opacity,fabric,depth);
        if(style==StockingsItem.Style.PANTYHOSE || style==StockingsItem.Style.BODYSTOCKING) {
            // The joined waist follows the torso, and lifts only after both legs reach the hips.
            float waist=Mth.clamp((Math.min(right,left)-.85F)/.15F,0,1);
            drawPart(pose,buffer,light,getParentModel().body,false,true,style,color,bottom,waist,opacity,fabric,depth);
        }
        }
        boolean full=slot==WearSlot.LEGS && style==StockingsItem.Style.BODYSTOCKING;
        float covered=state==null?1:state.coverAmount(entity,partial);
        if(slot==WearSlot.HANDS || full) {
            float arms=full?Mth.clamp((Math.min(left,right)-.65F)/.35F,0,1):covered;
            drawArm(pose,buffer,light,getParentModel().rightArm,false,style,color,bottom,arms,opacity,fabric,depth,full,slim);
            drawArm(pose,buffer,light,getParentModel().leftArm,true,style,color,bottom,arms,opacity,fabric,depth,full,slim);
        }
        if(slot==WearSlot.HEAD || full) {
            float head=full?Mth.clamp((Math.min(left,right)-.85F)/.15F,0,1):covered;
            float width=full?4.06F:4.10F;
            drawCover(pose,buffer,light,getParentModel().head,style,color,bottom,head,opacity,fabric,depth,
                    -width,width,-4-width,width-4,-width,width,true);
        }
        pose.popPose();
    }
    private void drawPart(PoseStack pose,MultiBufferSource buffers,int light,ModelPart part,boolean left,boolean body,
                          StockingsItem.Style style,int color,int bottomColor,float amount,float opacity,ResourceLocation fabric) {
        drawPart(pose,buffers,light,part,left,body,style,color,bottomColor,amount,opacity,fabric,0);
    }
    private void drawPart(PoseStack pose,MultiBufferSource buffers,int light,ModelPart part,boolean left,boolean body,
                          StockingsItem.Style style,int color,int bottomColor,float amount,float opacity,ResourceLocation fabric,int depth) {
        if(amount<.002F)return;
        pose.pushPose();part.translateAndRotate(pose);
        float legHeight=style==StockingsItem.Style.PANTYHOSE || style==StockingsItem.Style.BODYSTOCKING?12:10.4F;
        int passes=style==StockingsItem.Style.CAT_PAW?3:style==StockingsItem.Style.MATTE || style==StockingsItem.Style.CUSTOM?1:2;
        for(int pass=0;pass<passes;pass++) {
            boolean paw=pass==2;
            var texture=pass==0?fabric:paw?PAW_TEXTURE:SHEENS.get(style);
            // Switching a non-fixed render type may finish the previous consumer immediately.
            // Complete every cube in this pass before requesting a different texture's consumer.
            VertexConsumer out=buffers.getBuffer(style==StockingsItem.Style.GLOW
                    ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityTranslucent(texture));
            float padding=pass==0?0:paw?.006F:.004F;
            for(var cube:StockingsAssets.current.cubes(style)) {
                if(cube.body()!=body || !body && cube.left()!=left)continue;
                if(paw && (cube.welt() || cube.low()!=0))continue;
                float max=body?Math.min(cube.high(),12+(style==StockingsItem.Style.BODYSTOCKING?12:4)*amount):cube.welt()?cube.high():Math.min(cube.high(),legHeight*amount);
                if(max<=cube.low())continue;
                float offset=!body && cube.welt()?-legHeight*(1-amount):0;
                float pivot=body?24:12;
                float top=pivot-max-offset,bottom=pivot-cube.low()-offset;
                float clip=(cube.high()-max)/(cube.high()-cube.low());
                float alpha=Mth.clamp(amount/.07F,0,1)*opacity;
                if(pass==1)alpha*=com.shouyun.theartofstockings.FabricDepth.sheen(Math.round(opacity*100),depth);
                int c0=pass==0?StockingsItem.mix(color,bottomColor,1-(max+offset)/11.12F):0xFFFFFFFF;
                int c1=pass==0?StockingsItem.mix(color,bottomColor,1-(cube.low()+offset)/11.12F):0xFFFFFFFF;
                if(style!=StockingsItem.Style.GRADIENT) {
                    int tint=color;
                    if(style==StockingsItem.Style.STRIPED && (int)Math.floor((cube.low()+cube.high())/2/1.3F)%2==0) tint=bottomColor;
                    if(style==StockingsItem.Style.FOX && cube.high()<=3.4F) tint=bottomColor;
                    if(pass==0) { c0=tint;c1=tint; }
                }
                if(paw) { c0=com.shouyun.theartofstockings.FabricDepth.tint(bottomColor,depth);c1=c0; }
                c0=((int)(255*alpha)<<24)|(c0&0xFFFFFF);c1=((int)(255*alpha)<<24)|(c1&0xFFFFFF);
                drawCube(pose.last(),out,light,c0,c1,cube,top,bottom,clip,padding);
                if(paw && !body)drawRaisedPads(pose.last(),out,light,c1,bottom);
            }
        }
        pose.popPose();
    }
    static void drawArm(PoseStack pose,MultiBufferSource buffers,int light,ModelPart arm,boolean left,StockingsItem.Style style,
                        int color,int detail,float amount,float opacity,ResourceLocation fabric,int depth,boolean full,boolean slim) {
        float x0=left?-1:-3,x1=left?3:1;
        if(slim) { if(left)x1--;else x0++; }
        float padding=full?.055F:.09F;
        drawCover(pose,buffers,light,arm,style,color,detail,amount,opacity,fabric,depth,x0-padding,x1+padding,
                full?-2.04F:3.5F,10+padding,-2-padding,2+padding,false);
    }
    public static void renderFirstPerson(PoseStack pose,MultiBufferSource buffers,int light,
            net.minecraft.client.player.AbstractClientPlayer player,ModelPart arm,boolean left) {
        if(player.isInvisible() || StockingsAssets.current==null)return;
        var state=ClientAnimations.get(player);
        var hands=StockingsWear.get(player,WearSlot.HANDS);
        var body=StockingsWear.visual(player);
        boolean full=hands.isEmpty() && StockingsItem.style(body)==StockingsItem.Style.BODYSTOCKING;
        var stack=full?body:hands;
        float covered=1;
        if(state!=null && (state.slot()==WearSlot.HANDS || hands.isEmpty() && state.slot()==WearSlot.LEGS
                && StockingsItem.style(state.stack())==StockingsItem.Style.BODYSTOCKING)) {
            stack=state.stack();full=state.slot()==WearSlot.LEGS;
            covered=state.equipping()?state.progress(player,0):1-state.progress(player,0);
        }
        if(!StockingsActions.isStockings(stack))return;
        var style=StockingsItem.style(stack);int depth=StockingsItem.depth(stack);
        String artwork=stack.getOrDefault(TheArtofStockings.CUSTOM_TEXTURE.get(),"");
        var fabric=style==StockingsItem.Style.CUSTOM?CustomTextureCache.get(artwork):FABRICS.get(style);
        fabric=FabricTextureCache.get(fabric,artwork,style==StockingsItem.Style.CAT_PAW?15+Math.round(depth*.85F):depth,StockingsItem.opacity(stack));
        drawArm(pose,buffers,style==StockingsItem.Style.GLOW?net.minecraft.client.renderer.LightTexture.FULL_BRIGHT:light,
                arm,left,style,StockingsItem.color(stack),StockingsItem.secondaryColor(stack),covered,StockingsItem.opacity(stack)/100F,
                fabric,depth,full,player.getSkin().model().toString().equalsIgnoreCase("slim"));
    }
    static void drawCover(PoseStack pose,MultiBufferSource buffers,int light,ModelPart part,StockingsItem.Style style,
                          int color,int detail,float amount,float opacity,ResourceLocation fabric,int depth,
                          float x0,float x1,float y0,float y1,float z0,float z1,boolean capTop) {
        if(amount<.002F)return;
        amount=Mth.clamp(amount,0,1);
        pose.pushPose();part.translateAndRotate(pose);
        // Keep the actual style's segmented UVs, cuff, stripe boundaries and toe colors.
        var sections=StockingsAssets.current.cubes(style).stream().filter(c->!c.body() && !c.left()).toList();
        float length=(float)sections.stream().mapToDouble(StockingsAssets.Cube::high).max().orElseThrow();
        var sole=sections.stream().filter(c->c.low()==0 && !c.welt()).findFirst().orElseThrow().faces().get("down");
        float height=y1-y0;
        int passes=style==StockingsItem.Style.MATTE || style==StockingsItem.Style.CUSTOM?1:2;
        for(int pass=0;pass<passes;pass++) {
            var texture=pass==0?fabric:SHEENS.get(style);
            var out=buffers.getBuffer(style==StockingsItem.Style.GLOW?RenderType.entityTranslucentEmissive(texture):RenderType.entityTranslucent(texture));
            float alpha=opacity*Mth.clamp(amount/.07F,0,1)*(pass==0?1:com.shouyun.theartofstockings.FabricDepth.sheen(Math.round(opacity*100),depth));
            float padding=pass==0?0:.004F;
            for(var section:sections) {
                float low=section.low()/length,high=Math.min(section.high()/length,amount);
                if(high<=low)continue;
                float clip=(section.high()-high*length)/(section.high()-section.low());
                var uv=new java.util.HashMap<String,StockingsAssets.Face>();
                for(String side:new String[]{"north","south","east","west"}) {
                    var source=section.faces().get(side);if(source==null)continue;
                    float v=capTop?source.v()+source.dv():source.v()+source.dv()*clip;
                    uv.put(side,new StockingsAssets.Face(source.u(),v,source.du(),source.dv()*(1-clip)*(capTop?-1:1)));
                }
                var cube=new StockingsAssets.Cube(false,false,false,x0,x1,1,2,z0,z1,uv);
                float topY=capTop?y0+low*height:y1-high*height;
                float bottomY=capTop?y0+high*height:y1-low*height;
                int top=upperColor(style,color,detail,section,capTop?low:high);
                int bottom=upperColor(style,color,detail,section,capTop?high:low);
                if(pass!=0) { top=0xFFFFFF;bottom=0xFFFFFF; }
                top=(int)(255*alpha)<<24 | top&0xFFFFFF;bottom=(int)(255*alpha)<<24 | bottom&0xFFFFFF;
                drawCube(pose.last(),out,light,top,bottom,cube,topY,bottomY,0,padding);
            }
            int tip=style==StockingsItem.Style.GRADIENT || style==StockingsItem.Style.FOX || style==StockingsItem.Style.STRIPED?detail:color;
            int capColor=(int)(255*alpha)<<24 | ((pass==0?tip:0xFFFFFF)&0xFFFFFF);
            // A hood's closed toe stays on the crown; its open edge travels downward.
            if(capTop)face(pose.last(),out,light,capColor,capColor,sole,0,0,-1,0,
                    x0-padding,y0,z0-padding,x1+padding,y0,z0-padding,x1+padding,y0,z1+padding,x0-padding,y0,z1+padding);
            else face(pose.last(),out,light,capColor,capColor,sole,0,0,1,0,
                    x0-padding,y1,z1+padding,x1+padding,y1,z1+padding,x1+padding,y1,z0-padding,x0-padding,y1,z0-padding);
        }
        if(style==StockingsItem.Style.CAT_PAW) {
            var out=buffers.getBuffer(RenderType.entityTranslucent(PAW_TEXTURE));
            int paw=(int)(255*opacity*Mth.clamp(amount/.07F,0,1))<<24
                    | com.shouyun.theartofstockings.FabricDepth.tint(detail,depth)&0xFFFFFF;
            float width=(x1-x0)*.82F,glyphHeight=Math.min(width,height*.8F);
            float glyphTop=capTop?(y0+y1-glyphHeight)/2:y1-glyphHeight-.28F;
            float top=Math.max(glyphTop,capTop?y0:y1-amount*height);
            float bottom=Math.min(glyphTop+glyphHeight,capTop?y0+amount*height:y1);
            if(bottom>top) {
                float v=capTop?208/256F:1,dv=capTop?48/256F:-48/256F;
                var uv=new StockingsAssets.Face(0,v+dv*(top-glyphTop)/glyphHeight,48/256F,dv*(bottom-top)/glyphHeight);
                float left=(x0+x1-width)/2,right=left+width;
                face(pose.last(),out,light,paw,paw,uv,0,0,0,-1,right,top,z0-.012F,left,top,z0-.012F,
                        left,bottom,z0-.012F,right,bottom,z0-.012F);
            }
        }
        pose.popPose();
    }
    private static int upperColor(StockingsItem.Style style,int color,int detail,StockingsAssets.Cube section,float position) {
        if(style==StockingsItem.Style.GRADIENT)return StockingsItem.mix(color,detail,1-position);
        if(style==StockingsItem.Style.STRIPED && (int)Math.floor((section.low()+section.high())/2/1.3F)%2==0)return detail;
        if(style==StockingsItem.Style.FOX && section.high()<=3.4F)return detail;
        return color;
    }
    private static void drawRaisedPads(PoseStack.Pose pose,VertexConsumer out,int light,int color,float sole) {
        // Five shallow rounded pads follow the sole UV, moving with each animated foot.
        float[][] pads={{24,238,11,9},{10,224,5,6},{20,218.5F,5,6.5F},{32,218.5F,5,6.5F},{42,225,5,6}};
        var white=new StockingsAssets.Face(24/256F,238/256F,0,0);
        for(var pad:pads) {
            float cx=2.035F-pad[0]*4.07F/48,cz=-2.035F+(pad[1]-208)*4.07F/48;
            float rx=pad[2]*4.07F/48,rz=pad[3]*4.07F/48;
            float raised=sole+(pad==pads[0]?.32F:.24F);
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6,b=(i+1)*Math.PI/6;
                float ax=cx+rx*(float)Math.cos(a),az=cz+rz*(float)Math.sin(a);
                float bx=cx+rx*(float)Math.cos(b),bz=cz+rz*(float)Math.sin(b);
                face(pose,out,light,color,color,white,0,0,1,0,cx,raised,cz,bx,raised,bz,ax,raised,az,cx,raised,cz);
                float nx=(float)Math.cos((a+b)/2),nz=(float)Math.sin((a+b)/2);
                face(pose,out,light,color,color,white,0,nx,0,nz,bx,sole,bz,ax,sole,az,ax,raised,az,bx,raised,bz);
            }
        }
    }
    private static void drawCube(PoseStack.Pose pose, VertexConsumer out, int light, int color, int bottomColor,
                                 StockingsAssets.Cube c, float y0, float y1, float clip, float padding) {
        float x0=c.x0()-padding, x1=c.x1()+padding, z0=c.z0()-padding, z1=c.z1()+padding;
        face(pose,out,light,color,bottomColor,c.faces().get("north"),clip,0,0,-1,
                x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0);
        face(pose,out,light,color,bottomColor,c.faces().get("south"),clip,0,0,1,
                x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        face(pose,out,light,color,bottomColor,c.faces().get("east"),clip,1,0,0,
                x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
        face(pose,out,light,color,bottomColor,c.faces().get("west"),clip,-1,0,0,
                x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        // Stockings have an open top; only the sole closes the fabric shell.
        if (!c.welt() && c.low() == 0)
            face(pose,out,light,bottomColor,bottomColor,c.faces().get("down"),0,0,1,0,
                    x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
    }
    private static void face(PoseStack.Pose pose, VertexConsumer out, int light, int color, int bottomColor, StockingsAssets.Face uv,
                             float clip, float nx, float ny, float nz, float... xyz) {
        if (uv == null) return;
        float v0=uv.v()+uv.dv()*clip, v1=uv.v()+uv.dv();
        for (int i=0;i<4;i++) {
            float u=uv.u()+(i==0 || i==3 ? 0 : uv.du());
            float v=i<2?v0:v1;
            out.addVertex(pose,xyz[i*3]/16,xyz[i*3+1]/16,xyz[i*3+2]/16).setColor(i<2?color:bottomColor)
                    .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);
        }
    }
}
