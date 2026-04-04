package io.wifi.signgui;
import org.lwjgl.glfw.GLFW;

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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class SignEditorClientMod {
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping("key.signeditorgui.open_gui",
            GLFW.GLFW_KEY_V, new KeyMapping.Category(Identifier.fromNamespaceAndPath("signedit","misc")));

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    @SuppressWarnings("null")
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        while (OPEN_GUI_KEY.consumeClick()) {
            if (!ClientState.isOn) {
                client.player.sendSystemMessage(
                    Component.translatable("msg.signgui.unavailable").withStyle(ChatFormatting.YELLOW));
                continue;
            }

            if (!client.player.permissions().hasPermission(SignEditorConstants.perm_2)) {
                client.player.sendOverlayMessage(
                    Component.translatable("msg.signgui.not_op").withStyle(ChatFormatting.RED));
                continue;
            }

            HitResult hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos blockPos = blockHitResult.getBlockPos();
                BlockEntity blockEntity = client.level.getBlockEntity(blockPos);
                if (blockEntity instanceof SignBlockEntity sign) {
                    ClientState.textIsFront = sign.isFacingFrontText(client.player);
                    client.setScreen(new SignEditorScreen(sign));
                } else {
                    client.player.sendOverlayMessage(
                        Component.translatable("msg.signgui.not_a_sign").withStyle(ChatFormatting.RED));
                }
            } else {
                client.player.sendOverlayMessage(
                    Component.translatable("msg.signgui.not_a_block").withStyle(ChatFormatting.RED));
            }
        }
    }

    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientState.isOn = false;
        ClientPacketDistributor.sendToServer(new signEditablePayload(SignEditorConstants.helloVersion));
    }
}
