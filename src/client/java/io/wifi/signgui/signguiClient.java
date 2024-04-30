package io.wifi.signgui;
// MyMod.java

import org.lwjgl.glfw.GLFW;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class signguiClient implements ClientModInitializer {
    // 定义一个键绑定

    private static final KeyBinding keyBinding = new KeyBinding("key.signeditorgui.open_gui", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "category.signeditorgui");
    // private static final KeyBinding keyBinding2 = new
    // KeyBinding("key.signeditorgui.change_side", InputUtil.Type.KEYSYM,
    // GLFW.GLFW_KEY_G, "category.signeditorgui");
    // 定义一个数据包标识符，用于更新告示牌的文本和命令
    public static boolean textIsFront = true;
    public static boolean isOn = false;

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
                    client.getServer().sendMessage(Text.translatable("msg.signgui.notsameversion")
                            .append(serverHelloVersion).append(signgui.helloVersion).formatted(Formatting.YELLOW));
                }
                signguiClient.isOn = true;
            });
            ClientPlayNetworking.send(new signEditablePayload(signgui.helloVersion));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查键盘是否按下
            while (keyBinding.wasPressed()) {
                // 判断能否打开GUI
                if (!isOn) {
                    client.getServer()
                            .sendMessage(Text.translatable("msg.signgui.unavailable").formatted(Formatting.YELLOW));
                } else if (!client.player.hasPermissionLevel(2)) {

                    client.inGameHud.setOverlayMessage(
                            Text.translatable("msg.signgui.not_op").formatted(Formatting.RED), false);
                    return;
                }
                // 获取玩家当前指向的方块
                HitResult hitResult = client.crosshairTarget;
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    // BlockState blockState = client.world.getBlockState(blockPos);
                    BlockEntity blockEntity = client.world.getBlockEntity(blockPos);
                    // 检查方块是否是告示牌
                    if (blockEntity instanceof SignBlockEntity) {
                        // 打开自定义 GUI
                        SignBlockEntity sign = (SignBlockEntity) blockEntity;
                        // client.player.networkHandler.sendPacket(new
                        // SignEditorOpenC2SPacket(sign.getPos()));
                        textIsFront = sign.isPlayerFacingFront(client.player);
                        client.setScreen(new signEditorScreen(sign));
                    } else {
                        client.inGameHud.setOverlayMessage(
                                Text.translatable("msg.signgui.not_a_sign").formatted(Formatting.RED), false);
                    }
                } else {
                    client.inGameHud.setOverlayMessage(
                            Text.translatable("msg.signgui.not_a_block").formatted(Formatting.RED), false);
                }
            }
        });
    }
}
