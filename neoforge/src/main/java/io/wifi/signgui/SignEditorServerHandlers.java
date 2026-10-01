package io.wifi.signgui;

import java.util.List;

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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SignEditorServerHandlers {

    public static void handleHello(signEditablePayload payload, IPayloadContext ctx) {
        String clientHelloVersion = payload.text;
        if (!clientHelloVersion.equals(SignEditorConstants.helloVersion)) {
            SignEditorConstants.LOGGER.info(String.format(
                "Client logined with SignEditor protocol version %s, while the server is %s.",
                clientHelloVersion, SignEditorConstants.helloVersion));
        }
        PacketDistributor.sendToPlayer((ServerPlayer) ctx.player(),
            new signEditablePayload(SignEditorConstants.helloVersion));
    }

    public static void handleEdit(SignEditUpdateBlockPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();

        if (!player.permissions().hasPermission(SignEditorConstants.perm_2)) {
            ctx.enqueueWork(() ->
                player.sendSystemMessage(Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED)));
            return;
        }

        BlockPos signPos = payload.blockPos();
        List<Component> lines = payload.lines();
        SignTextSlot facing = payload.isFront() ? SignTextSlot.FRONT : SignTextSlot.BACK;
        boolean glowing = payload.isGlowing();
        DyeColor inkColor = payload.inkColor();
        boolean allowOpFeatures = payload.allowOpFeatures();

        ctx.enqueueWork(() -> {
            ServerLevelAccessor world = (ServerLevelAccessor) player.level();
            BlockEntity be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText.Mutable signText = sign.getText(facing).asMutable();
                for (int i = 0; i < 4 && i < lines.size(); ++i) {
                    signText.setLine(i, lines.get(i));
                }
                signText = signText.setTextGlowing(glowing);
                signText = signText.setColor(inkColor);
                // setText(...) already marks the block entity as changed and notifies clients.
                sign.setText(signText.asImmutable(), facing);
                // Last, and never before the lines: writing the flag loads the block entity back, and
                // that load is what parses the lines the flag is meant to apply to. setText above
                // stores them exactly as sent.
                SignOpFeatures.write(sign, player.level().registryAccess(), allowOpFeatures);
                player.connection.send(sign.getUpdatePacket());
                player.sendSystemMessage(
                    Component.translatable("msg.signgui.success").withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(
                    Component.translatable("msg.signgui.unexpected", "Cannot modify the sign block")
                        .withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    private static DyeColor parseDyeColor(String name) {
        if (name != null) {
            for (DyeColor c : DyeColor.values()) {
                if (c.getSerializedName().equals(name)) return c;
            }
        }
        return DyeColor.BLACK;
    }
}
