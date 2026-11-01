package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.theartofstockings.StockingsActions;
import com.shouyun.theartofstockings.StockingsItem;
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
    private static final java.util.Map<StockingsItem.Style,ResourceLocation> FABRICS = new java.util.EnumMap<>(StockingsItem.Style.class);
    private static final java.util.Map<StockingsItem.Style,ResourceLocation> SHEENS = new java.util.EnumMap<>(StockingsItem.Style.class);
    static {
        for (var style:StockingsItem.Style.values()) {
            String name=StockingsAssets.modelName(style);
            FABRICS.put(style,StockingsAssets.id("textures/entity/"+name+".png"));
            SHEENS.put(style,StockingsAssets.id("textures/entity/"+name+"_sheen.png"));
        }
    }
    public StockingsLayer(RenderLayerParent<T, M> parent) { super(parent); }
    @Override public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                       float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (entity.isSpectator() || StockingsAssets.current == null) return;
        if (entity instanceof ArmorStand stand) { if (stand.isMarker()) return; }
        else if (entity.isInvisible()) return;
        var state=ClientAnimations.get(entity);
        var stack=state==null ? StockingsActions.worn(entity) : state.stack();
        if(!StockingsActions.isStockings(stack)) return;
        var style=StockingsItem.style(stack);
        int color=StockingsItem.color(stack), bottom=style==StockingsItem.Style.GRADIENT?StockingsItem.secondaryColor(stack):color;
        if(style==StockingsItem.Style.GLOW) light=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        float right=state==null?1:state.legAmount(entity,partial,false);
        float left=state==null?1:state.legAmount(entity,partial,true);
        pose.pushPose();
        if(getParentModel().young) { pose.scale(.5F,.5F,.5F);pose.translate(0,1.5F,0); }
        drawPart(pose,buffer,light,getParentModel().rightLeg,false,false,style,color,bottom,right);
        drawPart(pose,buffer,light,getParentModel().leftLeg,true,false,style,color,bottom,left);
        if(style==StockingsItem.Style.PANTYHOSE) {
            // The joined waist follows the torso, and lifts only after both legs reach the hips.
            float waist=Mth.clamp((Math.min(right,left)-.85F)/.15F,0,1);
            drawPart(pose,buffer,light,getParentModel().body,false,true,style,color,bottom,waist);
        }
        pose.popPose();
    }
    private void drawPart(PoseStack pose,MultiBufferSource buffers,int light,ModelPart part,boolean left,boolean body,
                          StockingsItem.Style style,int color,int bottomColor,float amount) {
        if(amount<.002F)return;
        pose.pushPose();part.translateAndRotate(pose);
        float legHeight=style==StockingsItem.Style.PANTYHOSE?12:10.4F;
        for(int pass=0;pass<2;pass++) {
            var texture=pass==0?FABRICS.get(style):SHEENS.get(style);
            VertexConsumer out=buffers.getBuffer(style==StockingsItem.Style.GLOW
                    ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityTranslucent(texture));
            float padding=pass==0?0:.004F;
            for(var cube:StockingsAssets.current.cubes(style)) {
                if(cube.body()!=body || !body && cube.left()!=left)continue;
                float max=body?Math.min(cube.high(),12+4*amount):cube.welt()?cube.high():Math.min(cube.high(),legHeight*amount);
                if(max<=cube.low())continue;
                float offset=!body && cube.welt()?-legHeight*(1-amount):0;
                float pivot=body?24:12;
                float top=pivot-max-offset,bottom=pivot-cube.low()-offset;
                float clip=(cube.high()-max)/(cube.high()-cube.low());
                float alpha=Mth.clamp(amount/.07F,0,1);
                int c0=pass==0?StockingsItem.mix(color,bottomColor,1-(max+offset)/11.12F):0xFFFFFFFF;
                int c1=pass==0?StockingsItem.mix(color,bottomColor,1-(cube.low()+offset)/11.12F):0xFFFFFFFF;
                c0=((int)(255*alpha)<<24)|(c0&0xFFFFFF);c1=((int)(255*alpha)<<24)|(c1&0xFFFFFF);
                drawCube(pose.last(),out,light,c0,c1,cube,top,bottom,clip,padding);
            }
        }
        pose.popPose();
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
