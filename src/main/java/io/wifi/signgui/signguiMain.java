package io.wifi.signgui;
// MyMod.java

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent.Action;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;

public class signguiMain implements ModInitializer {
    // 定义一个数据包标识符，用于更新告示牌的文本和命令
    public static final Identifier UPDATE_SIGN_PACKET_ID = new Identifier("signeditorgui", "update_sign");

    @Override
    public void onInitialize() {
        // 注册服务器事件
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_SIGN_PACKET_ID,
                (MinecraftServer, client, ServerPlayNetworkHandler, packetByteBuf, PacketSender) -> {
                    // Style style = Text.literal("").getStyle();
                    if (!client.hasPermissionLevel(2)) {
                        // style.withColor((TextColor.fromFormatting(Formatting.RED)));
                        client.sendMessage(Text.translatable("msg.signgui.not_op").formatted(Formatting.RED));
                        return;
                    }
                    PacketByteBuf bufCache = packetByteBuf;
                    BlockPos signPos = bufCache.readBlockPos();
                    ServerPlayerEntity player = (ServerPlayerEntity) client;
                    String[] cmdCache = new String[4];
                    String[] textCache = new String[4];
                    String[] colorCache = new String[4];
                    for (int i = 0; i < 4; ++i) {
                        textCache[i] = bufCache.readString();
                        colorCache[i] = bufCache.readString();
                        cmdCache[i] = bufCache.readString();
                    }
                    boolean facing = bufCache.readBoolean();
                    MinecraftServer.execute(() -> {
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
                                TextColor textColor = TextColor.fromFormatting(Formatting.byName(ColorName));

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
                                client.sendMessage(Text.translatable("msg.signgui.success").formatted(Formatting.GREEN));
                            } else {
                                client.sendMessage(
                                        Text.translatable("msg.signgui.unexpected", "Cannot modify the sign block").formatted(Formatting.YELLOW));
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
