package com.shouyun.theartofstockings.client;

import com.shouyun.theartofstockings.CustomStockingsTexture;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import net.minecraft.nbt.*;

final class TextureEditingRegression {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void run() throws Exception {
        var noisy=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var random=new Random(1782);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++)noisy.setRGB(x,y,random.nextInt());
        var output=new ByteArrayOutputStream();ImageIO.write(noisy,"PNG",output);
        check(output.size()>65535,"Fixture exercises the old import and NBT UTF limits");
        var normalized=CustomStockingsTexture.normalize(output.toByteArray());
        check(CustomStockingsTexture.valid(normalized),"Large normalized artwork is accepted");
        var bytes=java.util.Base64.getDecoder().decode(normalized);
        check(bytes[24]==8 && bytes[25]==6,"Imported images become portable 8-bit RGBA PNGs");
        var tag=CustomStockingsTexture.CODEC.encodeStart(NbtOps.INSTANCE,normalized).getOrThrow();
        check(tag instanceof ByteArrayTag,"Large PNG persists as a byte array instead of an NBT UTF string");
        check(CustomStockingsTexture.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow().equals(normalized),"Byte-array component round trip");
        check(CustomStockingsTexture.CODEC.parse(NbtOps.INSTANCE,StringTag.valueOf(normalized)).getOrThrow().equals(normalized),"Legacy string components migrate");
        var small=new BufferedImage(64,64,BufferedImage.TYPE_USHORT_GRAY);output.reset();ImageIO.write(small,"PNG",output);
        var converted=CustomStockingsTexture.decode(CustomStockingsTexture.normalize(output.toByteArray()));
        check(converted.getWidth()==256 && converted.getHeight()==256,"Small and 16-bit PNGs normalize to the editor atlas");
        output.reset();ImageIO.write(new BufferedImage(32,64,BufferedImage.TYPE_INT_ARGB),"PNG",output);
        try { CustomStockingsTexture.normalize(output.toByteArray());throw new AssertionError("Rectangle was accepted"); }
        catch(CustomStockingsTexture.ImportFailure expected) { check(expected.reason.equals("not_square"),"Import error identifies non-square images"); }
        try { CustomStockingsTexture.normalize(new byte[]{1,2,3});throw new AssertionError("Non-PNG was accepted"); }
        catch(CustomStockingsTexture.ImportFailure expected) { check(expected.reason.equals("not_png"),"Import error identifies file format"); }

        var canvas=new StockingsCanvas(new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB));
        canvas.beginStroke();canvas.line(0,70,47,70,1,0xFFCE4271,false,StockingsCanvas.Region.FRONT);
        for(int x=0;x<48;x++)check(canvas.sample(x,70)==0xFFCE4271,"A dragged brush leaves no gaps");
        check(canvas.sample(48,70)==0,"Drawing respects the selected UV face");
        canvas.beginStroke();canvas.line(20,70,20,70,1,0,true,StockingsCanvas.Region.FRONT);
        check(canvas.sample(20,70)==0,"Eraser restores transparency");canvas.undo();check(canvas.sample(20,70)==0xFFCE4271,"Undo restores pixels");
        canvas.redo();check(canvas.sample(20,70)==0,"Redo reapplies edits");canvas.undo();
        canvas.beginStroke();canvas.line(10,32,10,199,1,0xFF000000,false,StockingsCanvas.Region.FRONT);
        canvas.fill(0,40,0x8000FF00,StockingsCanvas.Region.FRONT);
        check(canvas.sample(0,40)==0x8000FF00 && canvas.sample(11,40)==0,"Bucket respects contiguous regions and brush alpha");
        canvas.clear(StockingsCanvas.Region.FRONT);check(canvas.sample(10,60)==0,"Clear affects the selected face");
        canvas.undo();check(canvas.sample(10,60)==0xFF000000,"Clear is undoable");
        var encoded=CustomStockingsTexture.encode(canvas.image());
        check(CustomStockingsTexture.decode(encoded).getRGB(0,40)==0x8000FF00,"Drawing exports without losing alpha or color");
        try(var input=TextureEditingRegression.class.getResourceAsStream("/assets/theartofstockings/textures/entity/matte_stockings_s.png")) {
            int spec=ImageIO.read(input).getRGB(10,50);
            check(((spec>>>16)&255)==0 && ((spec>>>8)&255)<=5 && (spec>>>24)==255,"Matte material declares full roughness, low reflectance and no emission");
        }
        try(var input=TextureEditingRegression.class.getResourceAsStream("/assets/theartofstockings/textures/entity/matte_stockings.png")) {
            int fabric=ImageIO.read(input).getRGB(10,50);
            check((fabric>>>24)>=210,"Matte fabric has a dense base instead of classic sheer nylon");
        }
        for(String style:new String[]{"striped_stockings","fox_stockings"}) {
            try(var input=TextureEditingRegression.class.getResourceAsStream("/assets/theartofstockings/textures/entity/"+style+".png")) {
                check((ImageIO.read(input).getRGB(10,60)>>>24)>=205,"Striped and fox colors have stronger coverage");
            }
        }
        for(String asset:new String[]{"/assets/theartofstockings/textures/slot/stockings.png",
                "/assets/theartofstockings/textures/mob_effect/charm.png","/assets/theartofstockings/textures/mob_effect/foot_odor.png"}) {
            try(var input=TextureEditingRegression.class.getResourceAsStream(asset)) {
                var icon=ImageIO.read(input);int expected=asset.contains("/slot/")?16:18;
                check(icon.getWidth()==expected && icon.getHeight()==expected && icon.getColorModel().hasAlpha(),"Generated UI assets have the actual game dimensions and alpha");
            }
        }
        for(int sample:new int[]{0,0xFF305078,0x805A6070,0xFFFFFFFF}) {
            check(com.shouyun.theartofstockings.FabricDepth.pixel(sample,0)==sample,"Zero depth preserves the original pixel exactly");
            int deep=com.shouyun.theartofstockings.FabricDepth.pixel(sample,100);
            if(sample>>>24==0)check(deep==sample,"Depth never fills fishnet holes or erased custom artwork");
            else {
                check(deep>>>24==255,"Maximum depth provides full coverage of fabric pixels");
                check((deep&255)<=(sample&255) && ((deep>>>8)&255)<=((sample>>>8)&255),"Depth darkens existing colors");
            }
        }
        for(String asset:new String[]{"item/bodystocking","mob_effect/excitement"}) {
            try(var input=TextureEditingRegression.class.getResourceAsStream("/assets/theartofstockings/textures/"+asset+".png")) {
                var icon=ImageIO.read(input);int expected=asset.startsWith("item/")?32:18;
                check(icon.getWidth()==expected && icon.getHeight()==expected && icon.getColorModel().hasAlpha(),"New generated icons use valid game dimensions and alpha");
            }
        }
        int fabricPixels=0,holes=0;
        for(var style:com.shouyun.theartofstockings.StockingsItem.Style.values()) {
            String name=StockingsAssets.modelName(style);
            try(var input=TextureEditingRegression.class.getResourceAsStream("/assets/theartofstockings/textures/entity/"+name+".png");
                var fabric=com.mojang.blaze3d.platform.NativeImage.read(input)) {
                int[] original=new int[fabric.getWidth()*fabric.getHeight()];
                for(int y=0;y<fabric.getHeight();y++)for(int x=0;x<fabric.getWidth();x++)original[y*fabric.getWidth()+x]=fabric.getPixelRGBA(x,y);
                FabricTextureCache.apply(fabric,0,100);
                for(int y=0;y<fabric.getHeight();y++)for(int x=0;x<fabric.getWidth();x++) {
                    int before=original[y*fabric.getWidth()+x],after=fabric.getPixelRGBA(x,y);
                    if(before>>>24==0) { check(after==before,"Maximum opacity preserves open holes in "+name);holes++; }
                    else { check(after>>>24==255,"Maximum opacity removes built-in sheerness in "+name);fabricPixels++; }
                    check((before&0xFFFFFF)==(after&0xFFFFFF),"Opacity preserves RGB color and ABGR channel order");
                }
            }
        }
        check(fabricPixels>0 && holes>0,"The production-texture test covers fabric pixels and transparent holes");
        for(int alpha=0;alpha<=255;alpha++)for(int depth:new int[]{0,15,50,100}) {
            int sample=(alpha<<24)|0x345678;float previous=-1;
            for(int opacity=0;opacity<=100;opacity++) {
                int adjusted=com.shouyun.theartofstockings.FabricDepth.pixel(sample,depth,opacity);
                float visible=(adjusted>>>24)*opacity/100F;
                check(visible>=previous,"Increasing opacity never makes fabric more transparent");previous=visible;
                if(opacity==0)check(visible==0,"Zero opacity hides the fabric");
                if(opacity==100)check(visible==(alpha==0?0:255),"Maximum opacity is fully opaque independent of color depth");
                if(depth==0 && opacity<=75)check(adjusted==sample,"Lower opacity retains the original sheer texture");
            }
        }
        check(com.shouyun.theartofstockings.FabricDepth.sheen(100,0)<.11F,"Maximum opacity suppresses the white sheen");
        check(com.shouyun.theartofstockings.FabricDepth.sheen(75,0)==1,"Lower opacity retains the original sheen");
        System.out.println("Passed full-opacity coverage on all 11 production textures ("+fabricPixels+" fabric pixels, "+holes+" holes), monotonic slider and sheen regressions.");
        System.out.println("Passed color-depth, raised-paw, upper-body, slim-arm and new-icon regressions.");
        System.out.println("Passed PNG import, large NBT, painting, undo/redo, alpha and matte material regressions.");
    }
}
