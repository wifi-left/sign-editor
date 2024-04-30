package io.wifi.signgui;
// MyMod.java

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.DataResult;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent.Action;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;

public class signgui implements ModInitializer {
    // 常量
    public static String helloVersion = "1.0.SNAPSHOT";
    public static Logger LOGGER = LoggerFactory.getLogger("SignEditor");

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
                    if (!clientHelloVersion.equals(helloVersion)) {
                        LOGGER.info(String.format(
                                "Client logined with SignEditor protocol version %s, while the server is %s.",
                                clientHelloVersion, helloVersion));
                    }
                    ServerPlayNetworking.send(context.player(), new signEditablePayload(helloVersion));
                });
        // 告示牌编辑
        ServerPlayNetworking.registerGlobalReceiver(signEditPayload.ID,
                (payload, context) -> {
                    MinecraftServer server = context.player().getServer();
                    ServerPlayerEntity client = context.player();
                    // Style style = Text.literal("").getStyle();
                    if (!client.hasPermissionLevel(2)) {
                        // style.withColor((TextColor.fromFormatting(Formatting.RED)));
                        client.sendMessage(Text.translatable("msg.signgui.not_op").formatted(Formatting.RED));

                        return;
                    }

                    BlockPos signPos = payload.blockPos;
                    ServerPlayerEntity player = (ServerPlayerEntity) client;
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
                        ServerWorldAccess world = (ServerWorldAccess) player.getWorld();
                        // 获取方块状态和方块实体
                        BlockEntity be = world.getBlockEntity(signPos);
                        // 检查方块是否是告示牌
                        if (be instanceof SignBlockEntity) {
                            SignBlockEntity sign = (SignBlockEntity) be;
                            SignText signText = sign.getText(facing);
                            for (int i = 0; i < 4; ++i) {
                                // 获取文本框中输入的内容，并解析颜色代码（如果有的话）
                                String text = textCache[i];
                                MutableText literalText = Text.literal(text);
                                String ColorName = colorCache[i];
                                if (ColorName == null || ColorName == "")
                                    ColorName = "black";
                                // TextColor textColor = TextColor.fromFormatting(Formatting.byName(ColorName));
                                DataResult<TextColor> dataResultTextColor = TextColor.parse((ColorName));
                                TextColor textColor = TextColor.fromFormatting(Formatting.RESET);
                                try {
                                    textColor = dataResultTextColor.getOrThrow();
                                } catch (RuntimeException e) {

                                }
                                // TextColor.
                                String cmd = cmdCache[i];
                                if (cmd != "") {
                                    ClickEvent clickEvent = new ClickEvent(Action.RUN_COMMAND, cmd);
                                    literalText
                                            .setStyle(literalText.getStyle().withColor(textColor).withClickEvent(
                                                    clickEvent));
                                } else {
                                    literalText.setStyle(literalText.getStyle().withColor(textColor));
                                }
                                // signText.withMessage
                                signText = signText.withMessage(i, literalText); // 设置告示牌方块实体的文本内容
                            }
                            boolean res = sign.setText(signText, facing);
                            // System.out.print("Modify result: "+res);
                            sign.markDirty();
                            // player.openEditSignScreen(sign);
                            player.networkHandler.sendPacket(sign.toUpdatePacket());
                            // world.updateNeighbors(signPos, signState.getBlock());
                            // world.syncWorldEvent(client, 0, signPos, 0);
                            // world.getChunk(signPos).markBlockForPostProcessing(signPos);
                            // style.withColor((TextColor.fromFormatting(Formatting.GREEN)));
                            if (res) {
                                client.sendMessage(
                                        Text.translatable("msg.signgui.success").formatted(Formatting.GREEN));
                            } else {
                                client.sendMessage(
                                        Text.translatable("msg.signgui.unexpected", "Cannot modify the sign block")
                                                .formatted(Formatting.YELLOW));
                            }
                        } else {
                            // String out = (client == null ? "NULL" : client.()) + ":" + signPos.getX() + "
                            // "
                            // + signPos.getY() + " " + signPos.getZ();
                            return;
                        }
                    });
                });
    }
}
