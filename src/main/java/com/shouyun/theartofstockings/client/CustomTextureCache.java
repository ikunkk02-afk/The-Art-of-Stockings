package com.shouyun.theartofstockings.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.shouyun.theartofstockings.CustomStockingsTexture;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Bounds GPU use while letting each item carry its own multiplayer-visible texture. */
public final class CustomTextureCache {
    private static final Map<String,ResourceLocation> CACHE = new LinkedHashMap<>(16,.75F,true);
    private static final Map<String,ResourceLocation> LOCAL = new java.util.HashMap<>();
    private CustomTextureCache() {}
    public static ResourceLocation get(String png) {
        if(LOCAL.containsKey(png))return LOCAL.get(png);
        if (png.isEmpty()) return StockingsAssets.id("textures/entity/custom_stockings.png");
        var present=CACHE.get(png);
        if (present!=null) return present;
        var fallback=StockingsAssets.id("textures/entity/custom_stockings.png");
        ResourceLocation texture=fallback;
        if (CustomStockingsTexture.valid(png)) {
            try {
                var decoded=NativeImage.read(new ByteArrayInputStream(Base64.getDecoder().decode(png)));
                texture=Minecraft.getInstance().getTextureManager().register("stockings_custom",new DynamicTexture(decoded));
            } catch (Exception invalid) { /* A malformed texture must not break the player renderer. */ }
        }
        CACHE.put(png,texture);
        while(CACHE.size()>64) {
            var iterator=CACHE.entrySet().iterator();var oldest=iterator.next();iterator.remove();
            if (!oldest.getValue().equals(fallback)) Minecraft.getInstance().getTextureManager().release(oldest.getValue());
        }
        return texture;
    }
    static void local(String token,ResourceLocation texture) { LOCAL.put(token,texture); }
    static boolean isLocal(String token) { return LOCAL.containsKey(token); }
    static void removeLocal(String token) {
        var texture=LOCAL.remove(token);if(texture!=null)FabricTextureCache.invalidate(texture);
    }
    public static void clear() {
        var fallback=StockingsAssets.id("textures/entity/custom_stockings.png");
        for(var texture:CACHE.values()) if(!texture.equals(fallback)) Minecraft.getInstance().getTextureManager().release(texture);
        CACHE.clear();
    }
}
