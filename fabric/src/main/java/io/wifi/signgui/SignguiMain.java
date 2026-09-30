package io.wifi.signgui;

import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

public class SignguiMain implements ModInitializer {
    @Override
    public void onInitialize() {
        // 注册服务器事件
        PayloadTypeRegistry.serverboundPlay().register(SignEditUpdateBlockPayload.ID, SignEditUpdateBlockPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SignEditUpdateBlockPayload.ID, SignEditUpdateBlockPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(signEditablePayload.ID, signEditablePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(signEditablePayload.ID, signEditablePayload.CODEC);
        // Hello Event
        ServerPlayNetworking.registerGlobalReceiver(signEditablePayload.ID,
                (payload, context) -> {
                    String clientHelloVersion = payload.text;
                    if (!clientHelloVersion.equals(SignEditorConstants.helloVersion)) {
                        SignEditorConstants.LOGGER.info(String.format(
                                "Client logined with SignEditor protocol version %s, while the server is %s.",
                                clientHelloVersion, SignEditorConstants.helloVersion));
                    }
                    ServerPlayNetworking.send(context.player(),
                            new signEditablePayload(SignEditorConstants.helloVersion));
                });
        // 告示牌编辑
        ServerPlayNetworking.registerGlobalReceiver(SignEditUpdateBlockPayload.ID,
                (payload, context) -> {
                    MinecraftServer server = context.server();
                    ServerPlayer client = context.player();

                    if (!client.permissions().hasPermission(SignEditorConstants.perm_2)) {
                        client.sendSystemMessage(
                                Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED));
                        return;
                    }

                    BlockPos signPos = payload.blockPos();
                    ServerPlayer player = (ServerPlayer) client;
                    List<Component> lines = payload.lines();
                    SignTextSlot facing = payload.isFront() ? SignTextSlot.FRONT : SignTextSlot.BACK;
                    boolean glowing = payload.isGlowing();
                    DyeColor inkColor = payload.inkColor();

                    server.execute(() -> {
                        ServerLevelAccessor world = (ServerLevelAccessor) player.level();
                        BlockEntity be = world.getBlockEntity(signPos);
                        if (be instanceof SignBlockEntity) {
                            SignBlockEntity sign = (SignBlockEntity) be;
                            SignText.Mutable signText = sign.getText(facing).asMutable();
                            for (int i = 0; i < 4 && i < lines.size(); ++i) {
                                signText.setLine(i, lines.get(i));
                            }
                            signText = signText.setTextGlowing(glowing);
                            signText = signText.setColor(inkColor);
                            sign.setText(signText.asImmutable(), facing);
                            player.connection.send(sign.getUpdatePacket());
                            {
                                client.sendSystemMessage(
                                        Component.translatable("msg.signgui.success").withStyle(ChatFormatting.GREEN));
                            }
                        }
                    });
                });
    }
}
