package io.wifi.signgui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The two things the client does with the mod: answer the server's handshake, and open the editor on
 * whatever the player is looking at.
 *
 * <p>Both loaders had their own copy. The bodies here follow Fabric's, which is the behaviour the mod
 * is played with - and that matters in one place: when the server does not have the mod installed the
 * warning is drawn and the editor is opened anyway, because the NBT preview tab can still apply the
 * change by running the {@code /data merge} command through the vanilla command path. NeoForge used
 * to refuse to open outright, which left a player who had the mod on their side with no way to use
 * it at all.
 *
 * <p>Fabric's permission check sits in the {@code else} of that test on purpose, so a missing server
 * mod never turns into a second, unrelated complaint: with no server to check against, there is
 * nothing to be refused for.
 */
public final class SignEditorClientLogic {

    private SignEditorClientLogic() {
    }

    /**
     * The server answered the hello: warn on a protocol mismatch and mark the mod as available.
     *
     * <p>Resolves the client here rather than taking one, so that the NeoForge handler - which sits in
     * the same source set as the server handlers, and is therefore on a dedicated server's classpath -
     * can call this without naming {@code net.minecraft.client.Minecraft} anywhere in its own constant
     * pool. Keeping client classes out of the classes a server loads is the whole point of this class
     * existing separately from {@link SignEditorServerLogic}.
     */
    public static void handleHello(String serverVersion) {
        handleHello(serverVersion, Minecraft.getInstance());
    }

    /** As above, for a caller that already holds the client (Fabric's join callback does). */
    public static void handleHello(String serverVersion, Minecraft client) {
        if (client.player != null
                && !serverVersion.equals(SignEditorConstants.helloVersion)) {
            client.player.sendSystemMessage(
                    Component.translatable("msg.signgui.notsameversion")
                            .append(serverVersion)
                            .append(SignEditorConstants.helloVersion)
                            .withStyle(ChatFormatting.YELLOW));
        }
        ClientState.isOn = true;
    }

    /** One press of the open-GUI key: report why it cannot open, or open on the sign being looked at. */
    public static void openEditor(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (!ClientState.isOn) {
            client.player.sendOverlayMessage(
                    Component.translatable("msg.signgui.unavailable").withStyle(ChatFormatting.YELLOW));
        } else if (!client.player.permissions().hasPermission(SignEditorConstants.perm_2)) {
            client.player.sendOverlayMessage(
                    Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED));
            return;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            client.player.sendOverlayMessage(
                    Component.translatable("msg.signgui.not_a_block").withStyle(ChatFormatting.RED));
            return;
        }
        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockEntity blockEntity = client.level.getBlockEntity(blockPos);
        if (blockEntity instanceof SignBlockEntity sign) {
            ClientState.textIsFront = sign.getSlotPlayerIsFacing(client.player).equals(SignTextSlot.FRONT);
            client.setScreenAndShow(new SignEditorScreen(sign));
        } else {
            client.player.sendOverlayMessage(
                    Component.translatable("msg.signgui.not_a_sign").withStyle(ChatFormatting.RED));
        }
    }
}
