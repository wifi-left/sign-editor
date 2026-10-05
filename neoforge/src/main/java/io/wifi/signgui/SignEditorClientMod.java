package io.wifi.signgui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class SignEditorClientMod {
    /**
     * Built directly rather than through the deprecated {@code Category.register} factory, and handed
     * to {@link RegisterKeyMappingsEvent#registerCategory} instead.
     */
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("signedit", "misc"));

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping("key.signeditorgui.open_gui",
            InputConstants.KEY_V, CATEGORY);

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_GUI_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        while (OPEN_GUI_KEY.consumeClick()) {
            SignEditorClientLogic.openEditor(client);
        }
    }

    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientState.isOn = false;
        ClientPacketDistributor.sendToServer(new signEditablePayload(SignEditorConstants.helloVersion));
    }
}
