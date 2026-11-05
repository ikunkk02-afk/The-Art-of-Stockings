package com.shouyun.theartofstockings.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Vanilla's deprecated helper omits the NeoForge payload handshake required by optional mods. */
final class ModdedTestPlayers {
    private ModdedTestPlayers() {}
    static ServerPlayer create(GameTestHelper helper) {
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"stockings-test"),false);
        var server=helper.getLevel().getServer();
        var player=new ServerPlayer(server,helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection,player,cookie);
        return player;
    }
}
