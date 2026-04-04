package io.wifi.signgui;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ClientPlatformHelper {
    @FunctionalInterface
    public interface PacketSender {
        void send(CustomPacketPayload payload);
    }

    private static PacketSender sender;

    public static void register(PacketSender s) {
        sender = s;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (sender != null) {
            sender.send(payload);
        } else {
            throw new IllegalStateException("[SignEditor] ClientPlatformHelper.sendToServer called before a sender was registered.");
        }
    }
}
