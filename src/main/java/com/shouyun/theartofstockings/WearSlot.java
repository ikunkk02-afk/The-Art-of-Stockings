package com.shouyun.theartofstockings;

/** Clothing locations are independent of vanilla armor slots. */
public enum WearSlot {
    LEGS, HANDS, HEAD;

    public static WearSlot fromId(int id) { return id >= 0 && id < values().length ? values()[id] : null; }
    public String key() { return "gui.theartofstockings.wear." + name().toLowerCase(java.util.Locale.ROOT); }
}
