package io.wifi.signgui;

import com.mojang.serialization.DataResult;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public class signgui implements ModInitializer {
    @SuppressWarnings("null")
    @Override
    public void onInitialize() {
        // 注册服务器事件
        PayloadTypeRegistry.playS2C().register(signEditPayload.ID, signEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(signEditPayload.ID, signEditPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(signEditablePayload.ID, signEditablePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(signEditablePayload.ID, signEditablePayload.CODEC);
        // Hello Event
        ServerPlayNetworking.registerGlobalReceiver(signEditablePayload.ID,
                (payload, context) -> {
                    String clientHelloVersion = payload.text;
                    if (!clientHelloVersion.equals(SignEditorConstants.helloVersion)) {
                        SignEditorConstants.LOGGER.info(String.format(
                                "Client logined with SignEditor protocol version %s, while the server is %s.",
                                clientHelloVersion, SignEditorConstants.helloVersion));
                    }
                    ServerPlayNetworking.send(context.player(), new signEditablePayload(SignEditorConstants.helloVersion));
                });
        // 告示牌编辑
        ServerPlayNetworking.registerGlobalReceiver(signEditPayload.ID,
                (payload, context) -> {
                    MinecraftServer server = context.server();
                    ServerPlayer client = context.player();

                    if (!client.permissions().hasPermission(SignEditorConstants.perm_2)) {
                        client.sendSystemMessage(Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED));
                        return;
                    }

                    BlockPos signPos = payload.blockPos;
                    ServerPlayer player = (ServerPlayer) client;
                    String[] cmdCache = new String[4];
                    String[] textCache = new String[4];
                    String[] colorCache = new String[4];
                    for (int i = 0; i < 4; i++) {
                        textCache[i] = payload.signTextLines[i];
                        colorCache[i] = payload.signTextColors[i];
                        cmdCache[i] = payload.signTextCmds[i];
                    }

                    boolean facing = payload.isFront;
                    server.execute(() -> {
                        ServerLevelAccessor world = (ServerLevelAccessor) player.level();
                        BlockEntity be = world.getBlockEntity(signPos);
                        if (be instanceof SignBlockEntity) {
                            SignBlockEntity sign = (SignBlockEntity) be;
                            SignText signText = sign.getText(facing);
                            for (int i = 0; i < 4; ++i) {
                                String text = textCache[i];
                                MutableComponent literalText = Component.literal(text);
                                String ColorName = colorCache[i];
                                if (ColorName == null || ColorName == "")
                                    ColorName = "black";
                                DataResult<TextColor> dataResultTextColor = TextColor.parseColor((ColorName));
                                TextColor textColor = TextColor.fromLegacyFormat(ChatFormatting.RESET);
                                try {
                                    textColor = dataResultTextColor.getOrThrow();
                                } catch (RuntimeException e) {

                                }
                                String cmd = cmdCache[i];
                                if (cmd != "") {
                                    ClickEvent clickEvent = new RunCommand(cmd);
                                    literalText
                                            .setStyle(literalText.getStyle().withColor(textColor).withClickEvent(
                                                    clickEvent));
                                } else {
                                    literalText.setStyle(literalText.getStyle().withColor(textColor));
                                }
                                signText = signText.setMessage(i, literalText);
                            }
                            boolean res = sign.setText(signText, facing);
                            sign.setChanged();
                            player.connection.send(sign.getUpdatePacket());
                            if (res) {
                                client.sendSystemMessage(
                                        Component.translatable("msg.signgui.success").withStyle(ChatFormatting.GREEN));
                            } else {
                                client.sendSystemMessage(
                                        Component.translatable("msg.signgui.unexpected", "Cannot modify the sign block")
                                                .withStyle(ChatFormatting.YELLOW));
                            }
                        }
                    });
                });
    }
}
