package io.wifi.signgui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class signEditablePayload implements CustomPacketPayload {
    public static final String UPDATE_SIGN_PACKET_ID = "signeditorgui.hello";

    public static final CustomPacketPayload.Type<signEditablePayload> ID = CustomPacketPayload.createType(UPDATE_SIGN_PACKET_ID);
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, signEditablePayload> CODEC = StreamCodec
            .ofMember(signEditablePayload::write, signEditablePayload::new).cast();
    public String text = "Unknown";

    public signEditablePayload(FriendlyByteBuf buf) {
        this.text = buf.readUtf();
    }

    public signEditablePayload(String text) {
        this.text = text;
        
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.text);
    }

    @Override
    public Type<signEditablePayload> type() {
        return ID;
    }
}