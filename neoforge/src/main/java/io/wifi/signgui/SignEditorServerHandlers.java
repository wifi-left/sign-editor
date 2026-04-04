package io.wifi.signgui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
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

        BlockPos signPos = payload.blockPos;
        String[] lineJsons = payload.lineJsons.clone();
        boolean facing = payload.isFront;
        boolean glowing = payload.isGlowing;
        DyeColor inkColor = parseDyeColor(payload.inkColor);

        ctx.enqueueWork(() -> {
            ServerLevelAccessor world = (ServerLevelAccessor) player.level();
            BlockEntity be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText signText = sign.getText(facing);
                for (int i = 0; i < 4; ++i) {
                    Component comp = componentFromJson(lineJsons[i]);
                    signText = signText.setMessage(i, comp);
                }
                signText = signText.setHasGlowingText(glowing);
                signText = signText.setColor(inkColor);
                boolean res = sign.setText(signText, facing);
                sign.setChanged();
                player.connection.send(sign.getUpdatePacket());
                if (res) {
                    player.sendSystemMessage(
                        Component.translatable("msg.signgui.success").withStyle(ChatFormatting.GREEN));
                } else {
                    player.sendSystemMessage(
                        Component.translatable("msg.signgui.unexpected", "Cannot modify the sign block")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        });
    }

    private static Component componentFromJson(String json) {
        try {
            return ComponentSerialization.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result()
                    .orElse(Component.empty());
        } catch (Exception e) {
            return Component.empty();
        }
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
