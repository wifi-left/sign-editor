package io.wifi.signgui;
// MyMod.java

import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class signgui implements ClientModInitializer {
    // 定义一个键绑定
    private static final KeyBinding keyBinding = new KeyBinding("key.signeditorgui.open_gui", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "category.signeditorgui");
    private static final KeyBinding keyBinding2 = new KeyBinding("key.signeditorgui.change_side", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "category.signeditorgui");
    // 定义一个数据包标识符，用于更新告示牌的文本和命令
    public static boolean textIsFront = true;

    @Override
    public void onInitializeClient() {
        // 注册键绑定
        KeyBindingHelper.registerKeyBinding(keyBinding);
        KeyBindingHelper.registerKeyBinding(keyBinding2);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查键盘是否按下
            while (keyBinding2.wasPressed()) {
                textIsFront = !textIsFront;
                // client.player.sendMessage();
                client.inGameHud.setOverlayMessage(Text.translatable("msg.signgui.changefacing",
                Text.translatable("gui.wifi.signgui." + (signgui.textIsFront ? "front" : "back")).formatted(Formatting.GOLD)), false);
            }
            while (keyBinding.wasPressed()) {
                // 获取玩家当前指向的方块
                // Style style = Text.literal("").getStyle();
                // style.withColor();
                // formatted
                if (!client.player.hasPermissionLevel(2)) {

                    client.inGameHud.setOverlayMessage(Text.translatable("msg.signgui.not_op").formatted(Formatting.RED),false);
                    return;
                }
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
                        client.setScreen(new MyGuiScreen(sign));
                    } else {
                        client.inGameHud.setOverlayMessage(Text.translatable("msg.signgui.not_a_sign").formatted(Formatting.RED),false);
                    }
                } else {
                    client.inGameHud.setOverlayMessage(Text.translatable("msg.signgui.not_a_block").formatted(Formatting.RED),false);
                }
            }
        });
    }
}
