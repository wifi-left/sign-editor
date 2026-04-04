package io.wifi.signgui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(SignEditorMod.MOD_ID)
public class SignEditorMod {
    public static final String MOD_ID = "signeditgui";

    public SignEditorMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register payload handlers on the mod event bus
        modEventBus.addListener(this::registerPayloads);

        // Register client-side event handlers only on the client dist
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPlatformHelper.register(payload ->
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload));
            modEventBus.addListener(SignEditorClientMod::registerKeyMappings);
            NeoForge.EVENT_BUS.addListener(SignEditorClientMod::onClientTick);
            NeoForge.EVENT_BUS.addListener(SignEditorClientMod::onPlayerLogin);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        // signEditablePayload is bidirectional: client sends hello, server replies
        registrar.playBidirectional(
            signEditablePayload.ID,
            signEditablePayload.CODEC,
            new DirectionalPayloadHandler<>(
                SignEditorClientHandlers::handleHello,
                SignEditorServerHandlers::handleHello
            )
        );
        // signEditPayload is only sent from client to server
        registrar.playToServer(
            signEditPayload.ID,
            signEditPayload.CODEC,
            SignEditorServerHandlers::handleEdit
        );
    }
}
