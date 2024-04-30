package io.wifi.signgui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public class signEditablePayload implements CustomPayload {
    public static final String UPDATE_SIGN_PACKET_ID = "signeditorgui:hello";

    public static final CustomPayload.Id<signEditablePayload> ID = CustomPayload.id(UPDATE_SIGN_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, signEditablePayload> CODEC = PacketCodec
            .of(signEditablePayload::write, signEditablePayload::new).cast();
    public String text = "Unknown";

    public signEditablePayload(PacketByteBuf buf) {
        this.text = buf.readString();
    }

    public signEditablePayload(String text) {
        this.text = text;
        
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(this.text);
    }

    @Override
    public Id<signEditablePayload> getId() {
        return ID;
    }
}