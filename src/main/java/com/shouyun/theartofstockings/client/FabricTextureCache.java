package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.shouyun.theartofstockings.FabricDepth;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Item depth and high opacity increase coverage without filling transparent pattern holes. */
public final class FabricTextureCache {
    private record Key(ResourceLocation source,String artwork,int depth,int coverage) {}
    private static final Map<Key,ResourceLocation> CACHE=new LinkedHashMap<>(16,.75F,true);
    private FabricTextureCache() {}
    public static ResourceLocation get(ResourceLocation source,String artwork,int depth,int opacity) {
        int coverage=FabricDepth.opacityBoost(opacity);
        if(depth==0 && coverage==0)return source;
        var key=new Key(source,artwork,depth,coverage);var present=CACHE.get(key);
        if(present!=null)return present;
        try {
            var pixels=read(source,artwork);
            apply(pixels,depth,opacity);
            var texture=Minecraft.getInstance().getTextureManager().register("stockings_depth",new DynamicTexture(pixels));
            CACHE.put(key,texture);
            while(CACHE.size()>256) {
                var iterator=CACHE.entrySet().iterator();var oldest=iterator.next();iterator.remove();
                Minecraft.getInstance().getTextureManager().release(oldest.getValue());
            }
            return texture;
        } catch(Exception invalid) { return source; }
    }
    static void apply(NativeImage pixels,int depth,int opacity) {
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)
            pixels.setPixelRGBA(x,y,FabricDepth.pixel(pixels.getPixelRGBA(x,y),depth,opacity));
    }
    private static NativeImage read(ResourceLocation source,String artwork) throws java.io.IOException {
        if(CustomTextureCache.isLocal(artwork)) {
            var texture=Minecraft.getInstance().getTextureManager().getTexture(source);
            if(!(texture instanceof DynamicTexture live) || live.getPixels()==null)throw new java.io.IOException("Missing live canvas");
            var original=live.getPixels();var copy=new NativeImage(original.getWidth(),original.getHeight(),true);
            copy.copyFrom(original);return copy;
        }
        try(var input=artwork.isEmpty()?Minecraft.getInstance().getResourceManager().open(source)
                :new ByteArrayInputStream(Base64.getDecoder().decode(artwork))) { return NativeImage.read(input); }
    }
    static void invalidate(ResourceLocation source) {
        var iterator=CACHE.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(entry.getKey().source().equals(source)) {
                Minecraft.getInstance().getTextureManager().release(entry.getValue());iterator.remove();
            }
        }
    }
    static void clear() {
        for(var texture:CACHE.values())Minecraft.getInstance().getTextureManager().release(texture);
        CACHE.clear();
    }
}
