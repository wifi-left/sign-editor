package io.wifi.signgui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The NeoForge end of the server side. The work itself lives in {@link SignEditorServerLogic}; what
 * is left here is the two things that are actually NeoForge's: its payload context as the way back
 * onto the server thread, and {@code PacketDistributor} as the way to answer one player.
 */
public class SignEditorServerHandlers {

    public static void handleHello(signEditablePayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        SignEditorServerLogic.handleHello(player, payload, reply -> PacketDistributor.sendToPlayer(player, reply));
    }

    public static void handleEdit(SignEditUpdateBlockPayload payload, IPayloadContext ctx) {
        SignEditorServerLogic.handleEdit((ServerPlayer) ctx.player(), payload, ctx::enqueueWork);
    }
}
