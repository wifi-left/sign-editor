package io.wifi.signgui;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

/**
 * Everything the server does with the editor's two packets, in one place.
 *
 * <p>Both loaders used to carry their own copy of this, which is how they drifted: Fabric left the
 * explicit block-entity update commented out and silently did nothing when the target was not a
 * sign, while NeoForge sent the update twice and said so. The bodies below are Fabric's, which is
 * the behaviour the mod is played with; the loaders keep only the few platform-specific lines that
 * cannot move - how a payload is registered, and how work is handed back to the server thread.
 *
 * <p>That hand-off is the one thing the two platforms express differently, so it is a parameter:
 * NeoForge passes {@code IPayloadContext::enqueueWork}, Fabric passes its server (a
 * {@link Executor}). Both end up running the edit on the main thread, which is where a block entity
 * may be touched at all.
 */
public final class SignEditorServerLogic {

    private SignEditorServerLogic() {
    }

    /** The protocol handshake: log a mismatch, and tell the client which version this server speaks. */
    public static void handleHello(ServerPlayer player, signEditablePayload payload,
            Consumer<signEditablePayload> reply) {
        if (!payload.text.equals(SignEditorConstants.helloVersion)) {
            SignEditorConstants.LOGGER.info(String.format(
                    "Client logined with SignEditor protocol version %s, while the server is %s.",
                    payload.text, SignEditorConstants.helloVersion));
        }
        reply.accept(new signEditablePayload(SignEditorConstants.helloVersion));
    }

    /**
     * Applies one side of a sign, plus the two flags that belong to the sign as a whole.
     *
     * <p>The permission check and its message stay on the calling thread: that is what Fabric does,
     * and a system message is only ever queued onto the connection, so there is nothing here that
     * needs the main thread to be correct.
     */
    public static void handleEdit(ServerPlayer player, SignEditUpdateBlockPayload payload,
            Executor onServerThread) {
        if (!player.permissions().hasPermission(SignEditorConstants.perm_2)) {
            player.sendSystemMessage(
                    Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED));
            return;
        }
        onServerThread.execute(() -> apply(player, payload));
    }

    private static void apply(ServerPlayer player, SignEditUpdateBlockPayload payload) {
        BlockPos signPos = payload.blockPos();
        List<Component> lines = payload.lines();
        SignTextSlot facing = payload.isFront() ? SignTextSlot.FRONT : SignTextSlot.BACK;
        boolean glowing = payload.isGlowing();
        DyeColor inkColor = payload.inkColor();

        // The position comes off the wire, so it is checked the way vanilla checks the same thing in
        // /data merge block instead of being trusted: BlockPosArgument.loadedPos refuses a position
        // whose chunk is not loaded, precisely because Level.getBlockEntity - used below - would
        // otherwise load, and generate, that chunk on demand.
        ServerLevelAccessor world = (ServerLevelAccessor) player.level();
        if (!world.hasChunkAt(signPos)) {
            reject(player, "its chunk is not loaded");
            return;
        }
        BlockEntity be = world.getBlockEntity(signPos);
        if (!(be instanceof SignBlockEntity sign)) {
            player.sendSystemMessage(
                    Component.translatable("msg.signgui.unexpected", "Cannot modify the sign block")
                            .withStyle(ChatFormatting.YELLOW));
            return;
        }
        // Vanilla hands a sign to a player only while they are still at it - see
        // SignBlockEntity.updateSignText - and the editor screen closes itself on this very rule, so
        // every request a real client sends is within reach. Anything else is a hand-made packet.
        if (sign.playerIsTooFarAwayToEdit(player.getUUID())) {
            reject(player, "the player is out of reach of the sign");
            return;
        }

        SignText.Mutable signText = sign.getText(facing).asMutable();
        for (int i = 0; i < 4 && i < lines.size(); ++i) {
            signText.setLine(i, lines.get(i));
        }
        signText = signText.setTextGlowing(glowing);
        signText = signText.setColor(inkColor);
        // setText(...) already marks the block entity as changed and notifies clients.
        sign.setText(signText.asImmutable(), facing);
        // The waxed flag is the sign's own, not one of a side's, and setting it needs neither a
        // blockstate change nor a reload: SignBlockEntity.setWaxed flips its field and sends the
        // block update that carries is_waxed back out. So unlike the op-features flag below it is
        // order-independent, and it goes first only so the tag that write round-trips already holds
        // the new value.
        sign.setWaxed(payload.isWaxed());
        // Last, and never before the lines: writing the flag loads the block entity back, and
        // that load is what parses the lines the flag is meant to apply to. setText above
        // stores them exactly as sent.
        SignOpFeatures.write(sign, player.level().registryAccess(), payload.allowOpFeatures());
        // The update the BE sent above rides the chunk broadcast, which reaches only players being
        // tracked this tick; this makes sure the editor's own client sees the result at once.
        player.connection.send(sign.getUpdatePacket());
        player.sendSystemMessage(
                Component.translatable("msg.signgui.success").withStyle(ChatFormatting.GREEN));
    }

    /**
     * Refuses a request the editor could not have produced. Answered the way vanilla answers the
     * same thing - a sign edit arriving for a sign the player may no longer touch, which
     * {@code SignBlockEntity.updateSignText} logs and drops - rather than with a chat message: the
     * real client already refuses both cases, so a message would only be noise in the player's face.
     */
    private static void reject(ServerPlayer player, String reason) {
        SignEditorConstants.LOGGER.warn("Rejected a sign editor request from {}: {}.",
                player.getName().getString(), reason);
    }
}
