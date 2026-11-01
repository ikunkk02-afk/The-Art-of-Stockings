package com.shouyun.theartofstockings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Numeric subset of the Blockbench export used by our two authored clips. No Molang evaluation. */
public final class AnimationClip {
    public record Vector(float x, float y, float z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
        public static final Vector ONE = new Vector(1, 1, 1);
        Vector mix(Vector b, float t) { return new Vector(x + (b.x-x)*t, y + (b.y-y)*t, z + (b.z-z)*t); }
    }
    private record Key(float time, Vector value) {}
    private final Map<String, List<Key>> tracks = new HashMap<>();
    public final float length;

    public AnimationClip(JsonObject json) {
        length = json.get("animation_length").getAsFloat();
        for (var bone : json.getAsJsonObject("bones").entrySet()) {
            for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                List<Key> keys = new ArrayList<>();
                if (channel.getValue().isJsonArray()) keys.add(new Key(0, vector(channel.getValue())));
                else for (var entry : channel.getValue().getAsJsonObject().entrySet())
                    keys.add(new Key(Float.parseFloat(entry.getKey()), vector(entry.getValue())));
                keys.sort(Comparator.comparing(Key::time));
                tracks.put(bone.getKey() + "/" + channel.getKey(), List.copyOf(keys));
            }
        }
    }
    private static Vector vector(JsonElement element) {
        if (element.isJsonObject()) {
            var o = element.getAsJsonObject();
            element = o.has("vector") ? o.get("vector") : o.get("post");
        }
        var a = element.getAsJsonArray();
        return new Vector(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
    public Vector sample(String bone, String channel, float time) {
        var keys = tracks.get(bone + "/" + channel);
        if (keys == null || keys.isEmpty()) return channel.equals("scale") ? Vector.ONE : Vector.ZERO;
        if (time <= keys.getFirst().time) return keys.getFirst().value;
        for (int i = 1; i < keys.size(); i++) {
            Key a = keys.get(i-1), b = keys.get(i);
            if (time <= b.time) {
                float t = (time-a.time)/(b.time-a.time);
                // Ease each hand-authored stage without overshoot or sine-wave oscillation.
                t = t*t*(3-2*t);
                return a.value.mix(b.value, t);
            }
        }
        return keys.getLast().value;
    }
}
