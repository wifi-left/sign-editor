package io.wifi.signgui;
// MyMod.java

import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class signguiClient implements ClientModInitializer {
    // 定义一个键绑定
    private static KeyMapping keyBinding = new KeyMapping("key.signeditorgui.open_gui",
            GLFW.GLFW_KEY_V, KeyMapping.Category.register(Identifier.tryParse("signedit:misc")));
    // private static final KeyBinding keyBinding2 = new
    // KeyBinding("key.signeditorgui.change_side", InputUtil.Type.KEYSYM,
    // GLFW.GLFW_KEY_G, "category.signeditorgui");
    // 定义一个数据包标识符，用于更新告示牌的文本和命令
    public static boolean textIsFront = true;
    public static boolean isOn = false;

    @SuppressWarnings("null")
    @Override
    public void onInitializeClient() {
        // 注册键绑定
        KeyBindingHelper.registerKeyBinding(keyBinding);
        // KeyBindingHelper.registerKeyBinding(keyBinding2);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            signguiClient.isOn = false;
            ClientPlayNetworking.registerReceiver(signEditablePayload.ID, (payload, content) -> {
                String serverHelloVersion = payload.text;
                if (!serverHelloVersion.equals(signgui.helloVersion)) {
                    client.player.displayClientMessage((Component.translatable("msg.signgui.notsameversion")
                            .append(serverHelloVersion).append(signgui.helloVersion).withStyle(ChatFormatting.YELLOW)),
                            false);
                }
                signguiClient.isOn = true;
            });
            ClientPlayNetworking.send(new signEditablePayload(signgui.helloVersion));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查键盘是否按下
            while (keyBinding.consumeClick()) {
                // 判断能否打开GUI
                if (!signguiClient.isOn) {
                    client.player.displayClientMessage(
                            Component.translatable("msg.signgui.unavailable").withStyle(ChatFormatting.YELLOW), false);
                } else {
                    boolean unable = false;
                    if (client.player == null)
                        unable = true;
                    else if (!client.player.permissions().hasPermission(signgui.perm_2)) {
                        unable = true;
                    }
                    if (unable) {
                        client.player.displayClientMessage(
                                Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                }

                // 获取玩家当前指向的方块
                HitResult hitResult = client.hitResult;
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    // BlockState blockState = client.world.getBlockState(blockPos);
                    BlockEntity blockEntity = client.level.getBlockEntity(blockPos);
                    // 检查方块是否是告示牌
                    if (blockEntity instanceof SignBlockEntity) {
                        // 打开自定义 GUI
                        SignBlockEntity sign = (SignBlockEntity) blockEntity;
                        // client.player.networkHandler.sendPacket(new
                        // SignEditorOpenC2SPacket(sign.getPos()));
                        textIsFront = sign.isFacingFrontText(client.player);
                        client.setScreen(new signEditorScreen(sign));
                    } else {
                        client.player.displayClientMessage(
                                Component.translatable("msg.signgui.not_a_sign").withStyle(ChatFormatting.RED), true);
                    }
                } else {
                    client.player.displayClientMessage(
                            Component.translatable("msg.signgui.not_a_block").withStyle(ChatFormatting.RED), true);
                }
            }
        });
    }
}
