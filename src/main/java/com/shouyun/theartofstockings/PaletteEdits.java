package com.shouyun.theartofstockings;

import java.io.ByteArrayOutputStream;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** A server-owned snapshot and bounded, ordered PNG upload; one edit per online player. */
@EventBusSubscriber(modid=TheArtofStockings.MODID)
public final class PaletteEdits {
    public static final int CHUNK_BYTES=16_384;
    public static final int MAX_CHUNKS=(CustomStockingsTexture.MAX_BYTES+CHUNK_BYTES-1)/CHUNK_BYTES;
    public enum Result { PENDING, SUCCESS, REJECTED }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        final UUID token=UUID.randomUUID(); final int slot; final ItemStack expected;
        Upload upload;
        Session(int slot,ItemStack expected) { this.slot=slot;this.expected=expected.copy(); }
    }
    private static final class Upload {
        final int top,bottom,opacity,depth,total;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        int next;
        Upload(StockingsNetwork.PaletteChunk chunk) { top=chunk.top();bottom=chunk.bottom();opacity=chunk.opacity();depth=chunk.depth();total=chunk.total(); }
        boolean same(StockingsNetwork.PaletteChunk chunk) {
            return top==chunk.top() && bottom==chunk.bottom() && opacity==chunk.opacity() && depth==chunk.depth() && total==chunk.total();
        }
    }
    private PaletteEdits() {}
    public static UUID open(Player player,int slot,ItemStack expected) {
        var session=new Session(slot,expected);SESSIONS.put(player.getUUID(),session);return session.token;
    }
    public static void close(Player player) { SESSIONS.remove(player.getUUID()); }
    public static Result receive(Player player,StockingsNetwork.PaletteChunk chunk) {
        var session=SESSIONS.get(player.getUUID());
        if(session==null || !session.token.equals(chunk.session()))return reply(player,chunk.session(),false);
        if(!player.isAlive() || player.isSpectator() || !PaletteItem.holdingPalette(player) || StockingsActions.busy(player)
                || chunk.total()<1 || chunk.total()>MAX_CHUNKS || chunk.index()<0 || chunk.index()>=chunk.total()
                || chunk.bytes().length>CHUNK_BYTES || chunk.opacity()<0 || chunk.opacity()>100
                || chunk.depth()<0 || chunk.depth()>100
                || (chunk.top()&0xFF000000)!=0 || (chunk.bottom()&0xFF000000)!=0
                || !ItemStack.matches(PaletteItem.target(player,session.slot),session.expected)) {
            close(player);return reply(player,chunk.session(),false);
        }
        if(chunk.index()==0)session.upload=new Upload(chunk);
        var upload=session.upload;
        if(upload==null || !upload.same(chunk) || chunk.index()!=upload.next
                || upload.bytes.size()+chunk.bytes().length>CustomStockingsTexture.MAX_BYTES) {
            close(player);return reply(player,chunk.session(),false);
        }
        upload.bytes.writeBytes(chunk.bytes());upload.next++;
        if(upload.next<upload.total)return Result.PENDING;
        close(player);
        var png=Base64.getEncoder().encodeToString(upload.bytes.toByteArray());
        boolean saved=PaletteItem.apply(player,session.slot,session.expected,upload.top,upload.bottom,upload.opacity,upload.depth,png);
        return reply(player,chunk.session(),saved);
    }
    private static Result reply(Player player,UUID session,boolean success) {
        if(player instanceof ServerPlayer server)PacketDistributor.sendToPlayer(server,new StockingsNetwork.PaletteResult(session,success));
        return success?Result.SUCCESS:Result.REJECTED;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { close(event.getEntity()); }
}
