package com.shouyun.theartofstockings;

/** Independent color depth and fabric coverage; fully transparent pattern holes stay open. */
public final class FabricDepth {
    private FabricDepth() {}
    public static int tint(int pixel,int depth) {
        float shade=1-.65F*Math.clamp(depth,0,100)/100F;
        return (pixel&0xFF000000) | Math.round(((pixel>>>16)&255)*shade)<<16
                | Math.round(((pixel>>>8)&255)*shade)<<8 | Math.round((pixel&255)*shade);
    }
    public static int pixel(int pixel,int depth) {
        return pixel(pixel,depth,75);
    }
    /** The final quarter of the opacity slider removes the texture's built-in sheerness. */
    public static int opacityBoost(int opacity) { return (Math.clamp(opacity,75,100)-75)*4; }
    public static float sheen(int opacity,int depth) {
        return (1-.8F*Math.clamp(depth,0,100)/100F)*(1-.9F*opacityBoost(opacity)/100F);
    }
    public static int pixel(int pixel,int depth,int opacity) {
        int alpha=pixel>>>24;
        if(alpha==0)return pixel;
        float strength=1-(1-Math.clamp(depth,0,100)/100F)*(1-opacityBoost(opacity)/100F);
        int coverage=Math.round(alpha+(255-alpha)*strength);
        return coverage<<24 | (tint(pixel,depth)&0xFFFFFF);
    }
}
