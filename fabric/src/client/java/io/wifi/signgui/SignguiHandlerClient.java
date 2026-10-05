package io.wifi.signgui;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class SignguiHandlerClient implements ClientModInitializer {
    // 定义一个键绑定
    private static KeyMapping keyBinding = new KeyMapping("key.signeditorgui.open_gui",
            InputConstants.KEY_V, KeyMapping.Category.register(Identifier.parse("signedit:misc")));

    @Override
    public void onInitializeClient() {
        // 注册键绑定
        KeyMappingHelper.registerKeyMapping(keyBinding);
        // 注册平台相关的数据包发送器
        ClientPlatformHelper.register(ClientPlayNetworking::send);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientState.isOn = false;
            ClientPlayNetworking.registerReceiver(signEditablePayload.ID,
                    (payload, content) -> SignEditorClientLogic.handleHello(payload.text, client));
            ClientPlayNetworking.send(new signEditablePayload(SignEditorConstants.helloVersion));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查键盘是否按下
            while (keyBinding.consumeClick()) {
                SignEditorClientLogic.openEditor(client);
            }
        });
    }
}
