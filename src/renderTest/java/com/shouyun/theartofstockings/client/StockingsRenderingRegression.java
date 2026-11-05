package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.vertex.*;
import com.shouyun.theartofstockings.StockingsItem;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reproduces a buffer source that immediately ends the old builder on a texture switch. */
public final class StockingsRenderingRegression {
    record MeshVertex(float x,float y,float z,int color,float u,float v) {}
    record MeshQuad(ResourceLocation texture,MeshVertex[] vertices) {}
    static final class RetiringBuffers implements MultiBufferSource, AutoCloseable {
        private final ByteBufferBuilder memory = new ByteBufferBuilder(4096);
        private BufferBuilder current;
        private int vertices;
        private int batches;
        private float maxY=-Float.MAX_VALUE;
        private RenderType currentType;
        final List<MeshQuad> quads=new ArrayList<>();
        @Override public VertexConsumer getBuffer(RenderType type) {
            finish();
            current=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            currentType=type;
            return current;
        }
        void finish() {
            if(current==null)return;
            var mesh=current.build();current=null;
            if(mesh!=null) {
                vertices+=mesh.drawState().vertexCount();batches++;
                var data=mesh.vertexBuffer();int stride=DefaultVertexFormat.NEW_ENTITY.getVertexSize();
                for(int i=0;i<mesh.drawState().vertexCount();i++)maxY=Math.max(maxY,data.getFloat(i*stride+4));
                for(int i=0;i<mesh.drawState().vertexCount();i+=4) {
                    var points=new MeshVertex[4];
                    for(int j=0;j<4;j++) {
                        int offset=(i+j)*stride;
                        int color=(data.get(offset+15)&255)<<24 | (data.get(offset+12)&255)<<16
                                | (data.get(offset+13)&255)<<8 | data.get(offset+14)&255;
                        points[j]=new MeshVertex(data.getFloat(offset),data.getFloat(offset+4),data.getFloat(offset+8),color,
                                data.getFloat(offset+16),data.getFloat(offset+20));
                    }
                    quads.add(new MeshQuad(texture(currentType),points));
                }
                mesh.close();
            }
        }
        @Override public void close() { finish();memory.close(); }
    }
    private static ResourceLocation texture(RenderType type) {
        var paw=StockingsAssets.id("textures/entity/cat_paw_overlay.png");
        if(type==RenderType.entityTranslucent(paw))return paw;
        for(var style:StockingsItem.Style.values())for(String suffix:new String[]{"","_sheen"}) {
            var source=StockingsAssets.id("textures/entity/"+StockingsAssets.modelName(style)+suffix+".png");
            if(type==RenderType.entityTranslucent(source) || type==RenderType.entityTranslucentEmissive(source))return source;
        }
        throw new AssertionError("Unexpected material "+type);
    }
    private static ResourceManager resources() {
        return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(proxy,method,args)->{
            if(!method.getName().equals("openAsReader"))throw new UnsupportedOperationException(method.getName());
            var id=(ResourceLocation)args[0];
            var stream=StockingsRenderingRegression.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
            if(stream==null)throw new IllegalStateException("Missing fixture "+id);
            return new java.io.BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
        });
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.server.Bootstrap.bootStrap();
        var models=new EnumMap<StockingsItem.Style,List<StockingsAssets.Cube>>(StockingsItem.Style.class);
        var loader=StockingsAssets.class.getDeclaredMethod("loadModel",ResourceManager.class,String.class);loader.setAccessible(true);
        for(var style:StockingsItem.Style.values()) {
            @SuppressWarnings("unchecked") var cubes=(List<StockingsAssets.Cube>)loader.invoke(null,resources(),StockingsAssets.modelName(style));
            models.put(style,cubes);
        }
        StockingsAssets.current=new StockingsAssets.Data(Map.copyOf(models),null,null);
        var draw=StockingsLayer.class.getDeclaredMethod("drawPart",PoseStack.class,MultiBufferSource.class,int.class,ModelPart.class,
                boolean.class,boolean.class,StockingsItem.Style.class,int.class,int.class,float.class,float.class,ResourceLocation.class);
        draw.setAccessible(true);
        var layer=new StockingsLayer<>(null);
        int scenarios=0;
        for(var style:StockingsItem.Style.values()) for(float amount:new float[]{.04F,.35F,1F}) for(float opacity:new float[]{.25F,1F}) {
            try(var buffers=new RetiringBuffers()) {
                var pose=new PoseStack();var part=new ModelPart(List.of(),Map.of());
                var fabric=StockingsAssets.id("textures/entity/"+StockingsAssets.modelName(style)+".png");
                for(boolean left:new boolean[]{false,true}) {
                    draw.invoke(layer,pose,buffers,0xF000F0,part,left,false,style,0xFFFFFFFF,0xFFEF99B4,amount,opacity,fabric);
                }
                buffers.finish();
                if(buffers.vertices==0)throw new AssertionError("No mesh emitted for "+style);
                if(style==StockingsItem.Style.CAT_PAW && buffers.batches!=6)
                    throw new AssertionError("Cat paw needs cloth, sheen and paw batches on both legs");
                if(style==StockingsItem.Style.CAT_PAW && buffers.maxY<12.2F/16)
                    throw new AssertionError("Paw pads need real outward depth below the sole");
                scenarios++;
            } catch(InvocationTargetException failure) {
                throw new AssertionError("Render failed for "+style+" at amount="+amount+", opacity="+opacity,failure.getCause());
            }
        }
        for(var style:StockingsItem.Style.values())for(boolean slim:new boolean[]{false,true})for(boolean full:new boolean[]{false,true})
            for(float amount:new float[]{.04F,.35F,1F})for(int depth:new int[]{0,50,100}) {
                try(var buffers=new RetiringBuffers()) {
                    var pose=new PoseStack();var part=new ModelPart(List.of(),Map.of());
                    var fabric=StockingsAssets.id("textures/entity/"+StockingsAssets.modelName(style)+".png");
                    for(boolean left:new boolean[]{false,true})StockingsLayer.drawArm(pose,buffers,0xF000F0,part,left,style,
                            0xFF302238,0xFFBA527E,amount,.8F,fabric,depth,full,slim);
                    StockingsLayer.drawCover(pose,buffers,0xF000F0,part,style,0xFF302238,0xFFBA527E,amount,.8F,fabric,depth,
                            -4.06F,4.06F,-8.06F,.06F,-4.06F,4.06F,true);
                    buffers.finish();
                    if(buffers.vertices==0)throw new AssertionError("Upper garments emitted no geometry for "+style);
                    scenarios++;
                }
            }
        try(var buffers=new RetiringBuffers()) {
            var part=new ModelPart(List.of(),Map.of());
            draw.invoke(layer,new PoseStack(),buffers,0xF000F0,part,false,true,StockingsItem.Style.BODYSTOCKING,
                    0xFF302238,0xFF302238,1F,1F,StockingsAssets.id("textures/entity/bodystocking.png"));
            buffers.finish();if(buffers.vertices==0)throw new AssertionError("Full-body garment needs torso geometry");scenarios++;
        }
        System.out.println("Passed "+scenarios+" actual stockings mesh scenarios with immediate buffer retirement.");
        UpperGarmentRegression.run();
        TextureEditingRegression.run();
    }
}
