package com.shouyun.theartofstockings;

import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.world.item.ItemStack;

public final class StockingsNetwork {
    private StockingsNetwork() {}
    // Installed by client entry point, so dedicated servers never load client classes.
    public static Consumer<Animation> clientReceiver = packet -> {};
    public static Consumer<OpenPalette> paletteReceiver = packet -> {};
    public record Remove() implements CustomPacketPayload {
        public static final Type<Remove> TYPE = new Type<>(id("remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = StreamCodec.unit(new Remove());
        @Override public Type<Remove> type() { return TYPE; }
    }
    public record Animation(int entityId, boolean equipping, ItemStack stack, int elapsed, boolean active)
            implements CustomPacketPayload {
        public static final Type<Animation> TYPE = new Type<>(id("animation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Animation> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeBoolean(p.equipping); ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stack);
                    buf.writeVarInt(p.elapsed); buf.writeBoolean(p.active); },
                buf -> new Animation(buf.readVarInt(), buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean()));
        @Override public Type<Animation> type() { return TYPE; }
    }
    public record OpenPalette(int slot, ItemStack stack) implements CustomPacketPayload {
        public static final Type<OpenPalette> TYPE = new Type<>(id("open_palette"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPalette> CODEC = StreamCodec.of(
                (buf,p) -> { buf.writeVarInt(p.slot); ItemStack.STREAM_CODEC.encode(buf,p.stack); },
                buf -> new OpenPalette(buf.readVarInt(),ItemStack.STREAM_CODEC.decode(buf)));
        @Override public Type<OpenPalette> type() { return TYPE; }
    }
    public record ApplyPalette(int slot, ItemStack expected, int top, int bottom) implements CustomPacketPayload {
        public static final Type<ApplyPalette> TYPE = new Type<>(id("apply_palette"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPalette> CODEC = StreamCodec.of(
                (buf,p) -> { buf.writeVarInt(p.slot); ItemStack.STREAM_CODEC.encode(buf,p.expected); buf.writeInt(p.top);buf.writeInt(p.bottom); },
                buf -> new ApplyPalette(buf.readVarInt(),ItemStack.STREAM_CODEC.decode(buf),buf.readInt(),buf.readInt()));
        @Override public Type<ApplyPalette> type() { return TYPE; }
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(TheArtofStockings.MODID, name); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("5");
        registrar.playToServer(Remove.TYPE, Remove.CODEC,
                (packet, context) -> context.enqueueWork(() -> StockingsActions.requestRemoval(context.player())));
        registrar.playToClient(Animation.TYPE, Animation.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientReceiver.accept(packet)));
        registrar.playToClient(OpenPalette.TYPE, OpenPalette.CODEC,
                (packet, context) -> context.enqueueWork(() -> paletteReceiver.accept(packet)));
        registrar.playToServer(ApplyPalette.TYPE, ApplyPalette.CODEC,
                (packet, context) -> context.enqueueWork(() -> PaletteItem.apply(context.player(), packet.slot, packet.expected, packet.top, packet.bottom)));
    }
}
