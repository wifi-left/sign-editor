package io.wifi.signgui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class SignEditUpdateBlockPayload implements CustomPacketPayload {
    public static final String UPDATE_SIGN_PACKET_ID = "signeditorgui:update_sign";

    public static final CustomPacketPayload.Type<SignEditUpdateBlockPayload> ID = new Type<>(
            Identifier.tryParse(UPDATE_SIGN_PACKET_ID));
    public static final StreamCodec<RegistryFriendlyByteBuf, SignEditUpdateBlockPayload> CODEC = StreamCodec
            .ofMember(SignEditUpdateBlockPayload::write, SignEditUpdateBlockPayload::new).cast();

    public BlockPos blockPos = new BlockPos(0, 0, 0);
    /** Serialised Component JSON for each of the 4 sign lines. */
    public String[] lineJsons = new String[4];
    public boolean isFront = false;
    public boolean isGlowing = false;
    /** DyeColor serialised name, e.g. "black", "red". */
    public String inkColor = "black";

    public SignEditUpdateBlockPayload(BlockPos blockPos, String[] lineJsons, boolean isFront,
            boolean isGlowing, String inkColor) {
        this.blockPos = blockPos;
        this.lineJsons = lineJsons;
        this.isFront = isFront;
        this.isGlowing = isGlowing;
        this.inkColor = inkColor;
    }

    public SignEditUpdateBlockPayload(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        for (int i = 0; i < 4; i++) {
            this.lineJsons[i] = buf.readUtf();
        }
        this.isFront = buf.readBoolean();
        this.isGlowing = buf.readBoolean();
        this.inkColor = buf.readUtf();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        for (int i = 0; i < 4; i++) {
            buf.writeUtf(this.lineJsons[i]);
        }
        buf.writeBoolean(this.isFront);
        buf.writeBoolean(this.isGlowing);
        buf.writeUtf(this.inkColor);
    }

    @Override
    public Type<SignEditUpdateBlockPayload> type() {
        return ID;
    }
}