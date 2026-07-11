package io.wifi.signgui;

import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class SignguiHandlerClient implements ClientModInitializer {
    // 定义一个键绑定
    private static KeyMapping keyBinding = new KeyMapping("key.signeditorgui.open_gui",
            GLFW.GLFW_KEY_V, KeyMapping.Category.register(Identifier.parse("signedit:misc")));

    @Override
    public void onInitializeClient() {
        // 注册键绑定
        KeyMappingHelper.registerKeyMapping(keyBinding);
        // 注册平台相关的数据包发送器
        ClientPlatformHelper.register(ClientPlayNetworking::send);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientState.isOn = false;
            ClientPlayNetworking.registerReceiver(signEditablePayload.ID, (payload, content) -> {
                String serverHelloVersion = payload.text;
                if (!serverHelloVersion.equals(SignEditorConstants.helloVersion)) {
                    client.player.sendSystemMessage((Component.translatable("msg.signgui.notsameversion")
                            .append(serverHelloVersion).append(SignEditorConstants.helloVersion).withStyle(ChatFormatting.YELLOW)));
                }
                ClientState.isOn = true;
            });
            ClientPlayNetworking.send(new signEditablePayload(SignEditorConstants.helloVersion));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查键盘是否按下
            while (keyBinding.consumeClick()) {
                // 判断能否打开GUI
                if (!ClientState.isOn) {
                    client.player.sendOverlayMessage(
                            Component.translatable("msg.signgui.unavailable").withStyle(ChatFormatting.YELLOW));
                } else {
                    boolean unable = false;
                    if (client.player == null)
                        unable = true;
                    else if (!client.player.permissions().hasPermission(SignEditorConstants.perm_2)) {
                        unable = true;
                    }
                    if (unable) {
                        client.player.sendOverlayMessage(
                                Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED));
                        return;
                    }
                }

                // 获取玩家当前指向的方块
                HitResult hitResult = client.hitResult;
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    BlockEntity blockEntity = client.level.getBlockEntity(blockPos);
                    // 检查方块是否是告示牌
                    if (blockEntity instanceof SignBlockEntity) {
                        SignBlockEntity sign = (SignBlockEntity) blockEntity;
                        ClientState.textIsFront = sign.isFacingFrontText(client.player);
                        client.setScreenAndShow(new SignEditorScreen(sign));
                    } else {
                        client.player.sendOverlayMessage(
                                Component.translatable("msg.signgui.not_a_sign").withStyle(ChatFormatting.RED));
                    }
                } else {
                    client.player.sendOverlayMessage(
                            Component.translatable("msg.signgui.not_a_block").withStyle(ChatFormatting.RED));
                }
            }
        });
    }
}
