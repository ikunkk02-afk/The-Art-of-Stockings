package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shouyun.theartofstockings.FabricDepth;
import com.shouyun.theartofstockings.StockingsItem.Style;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import static com.shouyun.theartofstockings.client.StockingsRenderingRegression.*;

/** Inspect emitted colors, materials and clipping, rather than merely counting vertices. */
final class UpperGarmentRegression {
    private static final int PRIMARY=0xFF294B8F,DETAIL=0xFFE7A543;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static List<MeshQuad> mesh(Style style,boolean head,float amount) {
        return mesh(style,head,amount,false);
    }
    private static List<MeshQuad> mesh(Style style,boolean head,float amount,boolean defaults) {
        int base=defaults?switch(style) {
            case STRIPED -> 0xFFB8B0C2;case CAT_PAW -> 0xFFB9AFC0;case FOX -> 0xFFB84B16;case GRADIENT -> 0xFFCC77BB;default -> 0xFF30303B;
        }:PRIMARY;
        int detail=defaults?switch(style) {
            case STRIPED -> 0xFF17131D;case CAT_PAW -> 0xFFB94F78;case FOX -> 0xFF180E0B;default -> 0xFF659FE6;
        }:DETAIL;
        try(var out=new RetiringBuffers()) {
            var fabric=StockingsAssets.id("textures/entity/"+StockingsAssets.modelName(style)+".png");
            var part=new ModelPart(List.of(),Map.of());
            if(head)StockingsLayer.drawCover(new PoseStack(),out,0xF000F0,part,style,base,detail,amount,1,fabric,0,
                    -4.1F,4.1F,-8.1F,.1F,-4.1F,4.1F,true);
            else StockingsLayer.drawArm(new PoseStack(),out,0xF000F0,part,false,style,base,detail,amount,1,fabric,0,false,false);
            out.finish();return List.copyOf(out.quads);
        }
    }
    static void run() throws Exception {
        for(boolean head:new boolean[]{false,true}) {
            for(var style:new Style[]{Style.STRIPED,Style.FOX,Style.GRADIENT}) {
                var colors=new HashSet<Integer>();
                for(var quad:mesh(style,head,1))if(!quad.texture().getPath().contains("_sheen"))
                    for(var vertex:quad.vertices())colors.add(vertex.color()&0xFFFFFF);
                if(style==Style.GRADIENT)check(colors.size()>3,"Upper gradient needs intermediate colors");
                else check(colors.contains(PRIMARY&0xFFFFFF) && colors.contains(DETAIL&0xFFFFFF),style+" must keep both colors on "+(head?"head":"hands"));
            }
            var paw=mesh(Style.CAT_PAW,head,1).stream().filter(q->q.texture().getPath().endsWith("cat_paw_overlay.png")).toList();
            check(!paw.isEmpty(),"Cat paw overlay must be emitted for "+(head?"head":"hands"));
            for(var quad:paw)for(var point:quad.vertices())check((point.color()&0xFFFFFF)==(DETAIL&0xFFFFFF),"Upper paw uses the pattern dye");
        }
        for(float amount:new float[]{.1F,.25F,.5F,.75F,1}) {
            var hood=mesh(Style.CAT_PAW,true,amount);float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
            for(var quad:hood)for(var point:quad.vertices()) { min=Math.min(min,point.y()*16);max=Math.max(max,point.y()*16); }
            check(Math.abs(min+8.1F)<.002F,"The crown must stay covered from the first visible hood frame");
            check(Math.abs(max-(-8.1F+8.2F*amount))<.002F,"The hood's open edge travels down toward the neck");
            var hand=mesh(Style.STRIPED,false,amount);max=-Float.MAX_VALUE;
            for(var quad:hand)for(var point:quad.vertices())max=Math.max(max,point.y()*16);
            check(Math.abs(max-10.09F)<.002F,"Hands still start at the fingertips");
        }
        for(int i=0;i<=100;i++) {
            float p=i/100F;var on=HeadDressing.sample(true,p);var off=HeadDressing.sample(false,p);
            check(Math.abs(on.coverage()+off.coverage()-1)<.0001F,"Head removal reverses coverage");
            if(p<=.18F)check(on.coverage()==0,"Hands lift to the crown before the hood descends");
            if(p>=.82F)check(Math.abs(on.coverage()-1)<.0001F,"The hood reaches the neck before hands return");
        }
        check(HeadDressing.sample(true,.2F).armAngle()<HeadDressing.sample(true,.8F).armAngle(),"Putting on pulls hands from crown toward neck");
        check(HeadDressing.sample(false,.2F).armAngle()>HeadDressing.sample(false,.8F).armAngle(),"Taking off lifts hands from neck toward crown");
        preview();
        System.out.println("Passed upper-garment pattern colors, paw overlays, crown-to-neck clipping and reversed head removal regressions.");
    }
    private static final Map<ResourceLocation,BufferedImage> IMAGES=new HashMap<>();
    private static BufferedImage image(ResourceLocation texture) throws Exception {
        var found=IMAGES.get(texture);if(found!=null)return found;
        try(var input=UpperGarmentRegression.class.getResourceAsStream("/assets/"+texture.getNamespace()+"/"+texture.getPath())) {
            var loaded=ImageIO.read(input);IMAGES.put(texture,loaded);return loaded;
        }
    }
    private static void preview() throws Exception {
        var sheet=new BufferedImage(1320,740,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();
        g.setColor(new Color(0x252B35));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,17));g.setColor(Color.WHITE);
        g.drawString("实际网格正面映射检查（离线渲染，非游戏截图）",20,28);
        g.drawString("款式默认配色；统一底色表示露出的皮肤",760,28);
        var styles=new Style[]{Style.STRIPED,Style.CAT_PAW,Style.FOX,Style.GRADIENT,Style.FISHNET};
        String[] names={"条纹","猫肉垫","狐狸双色","渐变","渔网"};
        for(int col=0;col<styles.length;col++) {
            int x=col*260+20;g.setColor(Color.WHITE);g.drawString(names[col],x,60);
            drawPreview(sheet,g,mesh(styles[col],true,1,true),true,x,80,200,180);
            drawPreview(sheet,g,mesh(styles[col],false,1,true),false,x,292,200,165);
        }
        g.setColor(Color.WHITE);g.drawString("头套：从头顶逐步向下覆盖",20,500);
        for(int col=0;col<5;col++) {
            float amount=new float[]{.1F,.25F,.5F,.75F,1}[col];int x=col*260+20;
            g.setColor(Color.WHITE);g.drawString(Math.round(amount*100)+"%",x,527);
            drawPreview(sheet,g,mesh(Style.CAT_PAW,true,amount,true),true,x,542,200,174);
        }
        g.dispose();var target=Path.of("build/upper-garment-preview.png");Files.createDirectories(target.getParent());ImageIO.write(sheet,"PNG",target.toFile());
    }
    private static void drawPreview(BufferedImage sheet,Graphics2D g,List<MeshQuad> quads,boolean head,int x,int y,int w,int h) throws Exception {
        float minX=head?-4.1F:-3.09F,maxX=head?4.1F:1.09F,minY=head?-8.1F:3.5F,maxY=head?.1F:10.09F;
        float scale=Math.min(w/(maxX-minX),h/(maxY-minY));
        int left=x+(int)((w-(maxX-minX)*scale)/2);
        g.setColor(new Color(0xBA9A85));g.fillRect(left,y,Math.round((maxX-minX)*scale),Math.round((maxY-minY)*scale));
        float front=head?-4.1F:-2.09F;
        for(var quad:quads) {
            boolean north=true;for(var v:quad.vertices())if(Math.abs(v.z()*16-front)>.02F)north=false;
            if(!north)continue;
            var p=quad.vertices();var tex=image(quad.texture());
            boolean fabric=!quad.texture().getPath().contains("_sheen") && !quad.texture().getPath().contains("paw_overlay");
            triangle(sheet,tex,p[0],p[1],p[2],left,y,minX,minY,scale,fabric);
            triangle(sheet,tex,p[0],p[2],p[3],left,y,minX,minY,scale,fabric);
        }
    }
    private static void triangle(BufferedImage sheet,BufferedImage tex,MeshVertex a,MeshVertex b,MeshVertex c,int ox,int oy,float minX,float minY,float scale,boolean fabric) {
        float ax=ox+(a.x()*16-minX)*scale,ay=oy+(a.y()*16-minY)*scale;
        float bx=ox+(b.x()*16-minX)*scale,by=oy+(b.y()*16-minY)*scale;
        float cx=ox+(c.x()*16-minX)*scale,cy=oy+(c.y()*16-minY)*scale;
        float d=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy);if(Math.abs(d)<.001F)return;
        for(int y=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,cy))));y<Math.min(sheet.getHeight(),Math.ceil(Math.max(ay,Math.max(by,cy))));y++)
            for(int x=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,cx))));x<Math.min(sheet.getWidth(),Math.ceil(Math.max(ax,Math.max(bx,cx))));x++) {
                float wa=((by-cy)*(x+.5F-cx)+(cx-bx)*(y+.5F-cy))/d;
                float wb=((cy-ay)*(x+.5F-cx)+(ax-cx)*(y+.5F-cy))/d,wc=1-wa-wb;
                if(wa<-.0001F || wb<-.0001F || wc<-.0001F)continue;
                int tx=Math.clamp((int)((a.u()*wa+b.u()*wb+c.u()*wc)*tex.getWidth()),0,tex.getWidth()-1);
                int ty=Math.clamp((int)((a.v()*wa+b.v()*wb+c.v()*wc)*tex.getHeight()),0,tex.getHeight()-1);
                int pixel=tex.getRGB(tx,ty);if(fabric)pixel=FabricDepth.pixel(pixel,0,100);
                float alpha=(pixel>>>24)/255F*channel(a,b,c,wa,wb,wc,24)/255F;if(alpha==0)continue;
                int back=sheet.getRGB(x,y),result=0xFF000000;
                for(int shift:new int[]{16,8,0}) {
                    float value=((pixel>>>shift)&255)*channel(a,b,c,wa,wb,wc,shift)/255F;
                    result|=Math.round(value*alpha+((back>>>shift)&255)*(1-alpha))<<shift;
                }
                sheet.setRGB(x,y,result);
            }
    }
    private static float channel(MeshVertex a,MeshVertex b,MeshVertex c,float wa,float wb,float wc,int shift) {
        return ((a.color()>>>shift)&255)*wa+((b.color()>>>shift)&255)*wb+((c.color()>>>shift)&255)*wc;
    }
}
