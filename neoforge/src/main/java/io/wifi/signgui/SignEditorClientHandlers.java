package io.wifi.signgui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SignEditorClientHandlers {

    public static void handleHello(signEditablePayload payload, IPayloadContext ctx) {
        String serverHelloVersion = payload.text;
        ctx.enqueueWork(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && !serverHelloVersion.equals(SignEditorConstants.helloVersion)) {
                client.player.displayClientMessage(
                    Component.translatable("msg.signgui.notsameversion")
                        .append(serverHelloVersion)
                        .append(SignEditorConstants.helloVersion)
                        .withStyle(ChatFormatting.YELLOW),
                    false);
            }
            ClientState.isOn = true;
        });
    }
}
