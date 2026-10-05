package io.wifi.signgui;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The NeoForge end of the handshake reply.
 *
 * <p>The work is in {@link SignEditorClientLogic}, and nothing here names a client class - not even
 * to pass one in. That is deliberate: this file sits in the same source set as
 * {@link SignEditorServerHandlers}, so it is on a dedicated server's classpath and is loaded there
 * (the payload registration mentions it), and a client class in its constant pool is exactly what
 * that arrangement must avoid.
 */
public class SignEditorClientHandlers {

    public static void handleHello(signEditablePayload payload, IPayloadContext ctx) {
        String serverHelloVersion = payload.text;
        ctx.enqueueWork(() -> SignEditorClientLogic.handleHello(serverHelloVersion));
    }
}
