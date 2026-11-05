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
    public static Consumer<PaletteResult> paletteResultReceiver = packet -> {};
    public static Consumer<OpenWear> wearReceiver = packet -> {};
    public record OpenWear(net.minecraft.world.InteractionHand hand,boolean equip) implements CustomPacketPayload {
        public static final Type<OpenWear> TYPE=new Type<>(id("open_wear"));
        public static final StreamCodec<RegistryFriendlyByteBuf,OpenWear> CODEC=StreamCodec.of(
                (buf,p)->{buf.writeEnum(p.hand);buf.writeBoolean(p.equip);},buf->new OpenWear(buf.readEnum(net.minecraft.world.InteractionHand.class),buf.readBoolean()));
        @Override public Type<OpenWear> type() { return TYPE; }
    }
    public record WearRequest(int slot,net.minecraft.world.InteractionHand hand,boolean equip,ItemStack expected,String artworkHash) implements CustomPacketPayload {
        // Carry only a fingerprint of artwork; a PNG can exceed the serverbound payload limit.
        public WearRequest(int slot,net.minecraft.world.InteractionHand hand,boolean equip,ItemStack expected) {
            this(slot,hand,equip,withoutArtwork(expected),artworkHash(expected));
        }
        private static ItemStack withoutArtwork(ItemStack stack) {
            var copy=stack.copy();copy.remove(TheArtofStockings.CUSTOM_TEXTURE.get());return copy;
        }
        private static String artworkHash(ItemStack stack) {
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(stack.getOrDefault(TheArtofStockings.CUSTOM_TEXTURE.get(),"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        public static final Type<WearRequest> TYPE=new Type<>(id("wear_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,WearRequest> CODEC=StreamCodec.of(
                (buf,p)->{buf.writeVarInt(p.slot);buf.writeEnum(p.hand);buf.writeBoolean(p.equip);ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,p.expected);buf.writeUtf(p.artworkHash,64);},
                buf->new WearRequest(buf.readVarInt(),buf.readEnum(net.minecraft.world.InteractionHand.class),buf.readBoolean(),ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),buf.readUtf(64)));
        @Override public Type<WearRequest> type() { return TYPE; }
        public void apply(net.minecraft.world.entity.player.Player player) {
            var target=WearSlot.fromId(slot);
            if(target==null)return;
            var actual=equip?player.getItemInHand(hand):StockingsWear.get(player,target);
            if(!ItemStack.matches(withoutArtwork(actual),expected) || !artworkHash(actual).equals(artworkHash))return;
            if(equip)StockingsActions.startEquip(player,hand,target);
            else StockingsActions.startRemoval(player,target);
        }
    }
    public record Remove() implements CustomPacketPayload {
        public static final Type<Remove> TYPE = new Type<>(id("remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = StreamCodec.unit(new Remove());
        @Override public Type<Remove> type() { return TYPE; }
    }
    public record Animation(int entityId, boolean equipping, ItemStack stack, int elapsed, boolean active,WearSlot slot)
            implements CustomPacketPayload {
        public Animation(int entityId,boolean equipping,ItemStack stack,int elapsed,boolean active) { this(entityId,equipping,stack,elapsed,active,WearSlot.LEGS); }
        public static final Type<Animation> TYPE = new Type<>(id("animation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Animation> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeBoolean(p.equipping); ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stack);
                    buf.writeVarInt(p.elapsed); buf.writeBoolean(p.active);buf.writeEnum(p.slot); },
                buf -> new Animation(buf.readVarInt(), buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean(),buf.readEnum(WearSlot.class)));
        @Override public Type<Animation> type() { return TYPE; }
    }
    public record OpenPalette(int slot, ItemStack stack, java.util.UUID session) implements CustomPacketPayload {
        public OpenPalette(int slot,ItemStack stack) { this(slot,stack,java.util.UUID.randomUUID()); }
        public static final Type<OpenPalette> TYPE = new Type<>(id("open_palette"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPalette> CODEC = StreamCodec.of(
                (buf,p) -> { buf.writeVarInt(p.slot); ItemStack.STREAM_CODEC.encode(buf,p.stack);buf.writeUUID(p.session); },
                buf -> new OpenPalette(buf.readVarInt(),ItemStack.STREAM_CODEC.decode(buf),buf.readUUID()));
        @Override public Type<OpenPalette> type() { return TYPE; }
    }
    public record PaletteChunk(java.util.UUID session,int top,int bottom,int opacity,int depth,int index,int total,byte[] bytes) implements CustomPacketPayload {
        public PaletteChunk(java.util.UUID session,int top,int bottom,int opacity,int index,int total,byte[] bytes) { this(session,top,bottom,opacity,0,index,total,bytes); }
        public static final Type<PaletteChunk> TYPE=new Type<>(id("palette_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf,PaletteChunk> CODEC=StreamCodec.of(
                (buf,p)->{buf.writeUUID(p.session);buf.writeInt(p.top);buf.writeInt(p.bottom);buf.writeVarInt(p.opacity);buf.writeVarInt(p.depth);buf.writeVarInt(p.index);buf.writeVarInt(p.total);buf.writeByteArray(p.bytes);},
                buf->new PaletteChunk(buf.readUUID(),buf.readInt(),buf.readInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readByteArray(PaletteEdits.CHUNK_BYTES)));
        @Override public Type<PaletteChunk> type() { return TYPE; }
    }
    public record PaletteResult(java.util.UUID session,boolean success) implements CustomPacketPayload {
        public static final Type<PaletteResult> TYPE=new Type<>(id("palette_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf,PaletteResult> CODEC=StreamCodec.of(
                (buf,p)->{buf.writeUUID(p.session);buf.writeBoolean(p.success);},buf->new PaletteResult(buf.readUUID(),buf.readBoolean()));
        @Override public Type<PaletteResult> type() { return TYPE; }
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(TheArtofStockings.MODID, name); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("11");
        registrar.playToClient(OpenWear.TYPE,OpenWear.CODEC,(packet,context)->context.enqueueWork(()->wearReceiver.accept(packet)));
        registrar.playToServer(WearRequest.TYPE,WearRequest.CODEC,(packet,context)->context.enqueueWork(()->packet.apply(context.player())));
        registrar.playToServer(Remove.TYPE, Remove.CODEC,
                (packet, context) -> context.enqueueWork(() -> StockingsActions.requestRemoval(context.player())));
        registrar.playToClient(Animation.TYPE, Animation.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientReceiver.accept(packet)));
        registrar.playToClient(OpenPalette.TYPE, OpenPalette.CODEC,
                (packet, context) -> context.enqueueWork(() -> paletteReceiver.accept(packet)));
        registrar.playToServer(PaletteChunk.TYPE,PaletteChunk.CODEC,
                (packet,context)->context.enqueueWork(()->PaletteEdits.receive(context.player(),packet)));
        registrar.playToClient(PaletteResult.TYPE,PaletteResult.CODEC,
                (packet,context)->context.enqueueWork(()->paletteResultReceiver.accept(packet)));
    }
}
