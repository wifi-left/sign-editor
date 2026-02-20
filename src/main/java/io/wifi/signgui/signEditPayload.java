package io.wifi.signgui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class signEditPayload implements CustomPacketPayload {
    public static final String UPDATE_SIGN_PACKET_ID = "signeditorgui.update_sign";

    public static final CustomPacketPayload.Type<signEditPayload> ID = CustomPacketPayload.createType(UPDATE_SIGN_PACKET_ID);
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, signEditPayload> CODEC = StreamCodec.ofMember(signEditPayload::write, signEditPayload::new).cast();

    public BlockPos blockPos = new BlockPos(0,0,0);
    public String signTextLines[] = new String[4];
    public String signTextColors[] = new String[4];
    public String signTextCmds[] = new String[4];
    public Boolean isFront = false;

    public signEditPayload(BlockPos blockPos,String signTextLines[],String signTextColors[],String signTextCmds[],Boolean isFront) {
        this.blockPos = blockPos;
        this.signTextCmds = signTextCmds;
        this.signTextColors = signTextColors;
        this.signTextLines = signTextLines;
        this.isFront = isFront;
    }

    public signEditPayload(FriendlyByteBuf buf) {
        blockPos = buf.readBlockPos();
        for(int i = 0;i<4;i++){
            this.signTextLines[i] = buf.readUtf();
            this.signTextColors[i] = buf.readUtf();
            this.signTextCmds[i] = buf.readUtf();
        }
        this.isFront = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        for(int i = 0;i<4;i++){
            buf.writeUtf(this.signTextLines[i]);
            buf.writeUtf(this.signTextColors[i]);
            buf.writeUtf(this.signTextCmds[i]);
        }
        buf.writeBoolean(isFront);
    }

    @Override
    public Type<signEditPayload> type() {
        return ID;
    }
}