package io.wifi.signgui;

import com.mojang.serialization.DataResult;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
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
        // Reply with the server's protocol version
        PacketDistributor.sendToPlayer((ServerPlayer) ctx.player(),
            new signEditablePayload(SignEditorConstants.helloVersion));
    }

    @SuppressWarnings("null")
    public static void handleEdit(signEditPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();

        if (!player.permissions().hasPermission(SignEditorConstants.perm_2)) {
            ctx.enqueueWork(() ->
                player.sendSystemMessage(Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED)));
            return;
        }

        BlockPos signPos = payload.blockPos;
        String[] cmdCache = payload.signTextCmds.clone();
        String[] textCache = payload.signTextLines.clone();
        String[] colorCache = payload.signTextColors.clone();
        boolean facing = payload.isFront;

        ctx.enqueueWork(() -> {
            ServerLevelAccessor world = (ServerLevelAccessor) player.level();
            BlockEntity be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText signText = sign.getText(facing);
                for (int i = 0; i < 4; ++i) {
                    String text = textCache[i];
                    MutableComponent literalText = Component.literal(text);
                    String colorName = colorCache[i];
                    if (colorName == null || colorName.isEmpty()) colorName = "black";
                    DataResult<TextColor> dataResultTextColor = TextColor.parseColor(colorName);
                    TextColor textColor = TextColor.fromLegacyFormat(ChatFormatting.RESET);
                    try {
                        textColor = dataResultTextColor.getOrThrow();
                    } catch (RuntimeException e) {
                        // Keep default color
                    }
                    String cmd = cmdCache[i];
                    if (!cmd.isEmpty()) {
                        ClickEvent clickEvent = new RunCommand(cmd);
                        literalText.setStyle(literalText.getStyle().withColor(textColor).withClickEvent(clickEvent));
                    } else {
                        literalText.setStyle(literalText.getStyle().withColor(textColor));
                    }
                    signText = signText.setMessage(i, literalText);
                }
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
}
