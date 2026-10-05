package io.wifi.signgui;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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
                (payload, context) -> SignEditorServerLogic.handleHello(context.player(), payload,
                        reply -> ServerPlayNetworking.send(context.player(), reply)));
        // 告示牌编辑; the server itself is the executor that puts the work back on the main thread.
        ServerPlayNetworking.registerGlobalReceiver(SignEditUpdateBlockPayload.ID,
                (payload, context) -> SignEditorServerLogic.handleEdit(context.player(), payload,
                        context.server()));
    }
}
