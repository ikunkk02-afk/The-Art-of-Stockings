package com.shouyun.theartofstockings.client;

/** Lift to the crown, pull down toward the neck, then lower the hands. */
final class HeadDressing {
    record Frame(float coverage,float armAngle,float weight) {}
    private static float smooth(float value) { float t=Math.clamp(value,0,1);return t*t*(3-2*t); }
    static Frame sample(boolean equipping,float progress) {
        float pull=smooth((progress-.18F)/.64F);
        float coverage=equipping?pull:1-pull;
        float from=equipping?-3F:-1.65F,to=equipping?-1.65F:-3F;
        float weight=smooth(progress/.18F)*smooth((1-progress)/.18F);
        return new Frame(coverage,from+(to-from)*pull,weight);
    }
}
