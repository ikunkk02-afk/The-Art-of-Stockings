package com.shouyun.theartofstockings.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shouyun.theartofstockings.AnimationClip;
import com.shouyun.theartofstockings.TheArtofStockings;
import com.shouyun.theartofstockings.StockingsItem.Style;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Loads the actual Blockbench exports, including face UVs, on each resource reload. */
public final class StockingsAssets extends SimplePreparableReloadListener<StockingsAssets.Data> {
    public record Face(float u, float v, float du, float dv) {}
    public record Cube(boolean left, boolean welt, boolean body, float x0, float x1, float low, float high,
                       float z0, float z1, Map<String, Face> faces) {}
    public record Data(Map<Style,List<Cube>> models, AnimationClip putOn, AnimationClip takeOff) {
        public List<Cube> cubes(Style style) { return models.get(style); }
    }
    public static volatile Data current;

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(TheArtofStockings.MODID, path); }
    private static JsonObject json(ResourceManager manager, String path) {
        try (var reader = manager.openAsReader(id(path))) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (IOException e) { throw new IllegalStateException("Unable to read stockings asset " + path, e); }
    }
    @Override protected Data prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Style,List<Cube>> models = new java.util.EnumMap<>(Style.class);
        for (Style style:Style.values()) models.put(style, loadModel(manager, modelName(style)));
        var animations = json(manager, "animations/stockings.animation.json").getAsJsonObject("animations");
        return new Data(Map.copyOf(models), new AnimationClip(animations.getAsJsonObject("animation.stockings.put_on")),
                new AnimationClip(animations.getAsJsonObject("animation.stockings.take_off")));
    }
    public static String modelName(Style style) {
        return switch(style) { case CLASSIC -> "stockings";case GRADIENT -> "gradient_stockings";case PANTYHOSE -> "pantyhose";
            case FISHNET -> "fishnet_stockings";case GLOW -> "glow_stockings";
            case STRIPED -> "striped_stockings";case CAT_PAW -> "cat_paw_stockings";case MATTE -> "matte_stockings";
            case FOX -> "fox_stockings";case CUSTOM -> "custom_stockings";case BODYSTOCKING -> "bodystocking"; };
    }
    private static List<Cube> loadModel(ResourceManager manager,String model) {
        var geometry = json(manager, "geo/"+model+".geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var description = geometry.getAsJsonObject("description");
        float width = description.get("texture_width").getAsFloat(), height = description.get("texture_height").getAsFloat();
        List<Cube> cubes = new ArrayList<>();
        for (var entry : geometry.getAsJsonArray("bones")) {
            var bone = entry.getAsJsonObject();
            if (!bone.has("cubes")) continue;
            String name = bone.get("name").getAsString();
            boolean left = name.startsWith("left_");
            boolean body = name.equals("tights_hips");
            float center = body ? 0 : left ? -2 : 2; // Bedrock X is mirrored by the Blockbench GeckoLib exporter.
            for (var cubeEntry : bone.getAsJsonArray("cubes")) {
                var cube = cubeEntry.getAsJsonObject();
                var o = cube.getAsJsonArray("origin"); var s = cube.getAsJsonArray("size");
                Map<String, Face> faces = new HashMap<>();
                for (var faceEntry : cube.getAsJsonObject("uv").entrySet()) {
                    var f = faceEntry.getValue().getAsJsonObject();var uv = f.getAsJsonArray("uv");var size = f.getAsJsonArray("uv_size");
                    faces.put(faceEntry.getKey(), new Face(uv.get(0).getAsFloat()/width, uv.get(1).getAsFloat()/height,
                            size.get(0).getAsFloat()/width, size.get(1).getAsFloat()/height));
                }
                float ox=o.get(0).getAsFloat(), oy=o.get(1).getAsFloat(), oz=o.get(2).getAsFloat();
                cubes.add(new Cube(left, name.endsWith("_welt"), body, center-ox-s.get(0).getAsFloat(), center-ox,
                        oy, oy+s.get(1).getAsFloat(), oz, oz+s.get(2).getAsFloat(), Map.copyOf(faces)));
            }
        }
        return List.copyOf(cubes);
    }
    @Override protected void apply(Data data, ResourceManager manager, ProfilerFiller profiler) { CustomTextureCache.clear();FabricTextureCache.clear();current = data; }
}
