package io.wifi.signgui;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public class signEditPayload implements CustomPayload {
    public static final CustomPayload.Id<signEditPayload> ID = new CustomPayload.Id<>(signgui.UPDATE_SIGN_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, signEditPayload> CODEC = PacketCodec.of(signEditPayload::write, signEditPayload::new).cast();

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

    public signEditPayload(PacketByteBuf buf) {
        blockPos = buf.readBlockPos();
        for(int i = 0;i<4;i++){
            this.signTextLines[i] = buf.readString();
            this.signTextColors[i] = buf.readString();
            this.signTextCmds[i] = buf.readString();
        }
        this.isFront = buf.readBoolean();
    }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        for(int i = 0;i<4;i++){
            buf.writeString(this.signTextLines[i]);
            buf.writeString(this.signTextColors[i]);
            buf.writeString(this.signTextCmds[i]);
        }
        buf.writeBoolean(isFront);
    }

    @Override
    public Id<signEditPayload> getId() {
        return ID;
    }
}