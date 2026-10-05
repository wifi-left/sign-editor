package io.wifi.signgui;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/**
 * Client to server: replace all four lines of one side of a sign.
 *
 * <p>The lines travel as {@link Component}s over {@code ComponentSerialization.STREAM_CODEC} rather
 * than as JSON strings, the same way {@code TitleCommand} hands a component to
 * {@code ClientboundSetTitleTextPacket}. That removes a whole failure mode: the server used to
 * re-parse a JSON string with the strict component codec, and anything the codec rejected - a dye
 * name in a colour field, for instance - silently collapsed the line to an empty component. It also
 * drops the JSON escaping, which used to roughly double the size of a long click command.
 *
 * <p>{@code allowOpFeatures} rides along because it is stored on the sign: see
 * {@link SignOpFeatures}.
 *
 * <p>{@code waxed} rides along for the same reason, but needs none of that machinery: the sign
 * keeps it in {@code is_waxed} with a public {@code isWaxed()} / {@code setWaxed(boolean)} pair, so
 * the server just calls the setter and the block update it sends carries the value back out.
 */
public class SignEditUpdateBlockPayload implements CustomPacketPayload {
    public static final String UPDATE_SIGN_PACKET_ID = "signeditorgui:update_sign";

    public static final CustomPacketPayload.Type<SignEditUpdateBlockPayload> ID = new Type<>(
            Identifier.tryParse(UPDATE_SIGN_PACKET_ID));
    public static final StreamCodec<RegistryFriendlyByteBuf, SignEditUpdateBlockPayload> CODEC = StreamCodec
            .composite(
                    BlockPos.STREAM_CODEC, SignEditUpdateBlockPayload::blockPos,
                    ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.fixedSizeList(4)),
                    SignEditUpdateBlockPayload::lines,
                    ByteBufCodecs.BOOL, SignEditUpdateBlockPayload::isFront,
                    ByteBufCodecs.BOOL, SignEditUpdateBlockPayload::isGlowing,
                    DyeColor.STREAM_CODEC, SignEditUpdateBlockPayload::inkColor,
                    ByteBufCodecs.BOOL, SignEditUpdateBlockPayload::allowOpFeatures,
                    ByteBufCodecs.BOOL, SignEditUpdateBlockPayload::isWaxed,
                    SignEditUpdateBlockPayload::new);

    private final BlockPos blockPos;
    /** Exactly four components, one per sign line, in order. */
    private final List<Component> lines;
    private final boolean isFront;
    private final boolean isGlowing;
    private final DyeColor inkColor;
    /** The sign's own {@code allow_op_features} flag, as the editor's switch left it. */
    private final boolean allowOpFeatures;
    /** The sign's own {@code is_waxed} flag, as the editor's switch left it. */
    private final boolean waxed;

    public SignEditUpdateBlockPayload(BlockPos blockPos, List<Component> lines, boolean isFront,
            boolean isGlowing, DyeColor inkColor, boolean allowOpFeatures, boolean waxed) {
        this.blockPos = blockPos;
        this.lines = List.copyOf(lines);
        this.isFront = isFront;
        this.isGlowing = isGlowing;
        this.inkColor = inkColor;
        this.allowOpFeatures = allowOpFeatures;
        this.waxed = waxed;
    }

    public BlockPos blockPos() {
        return this.blockPos;
    }

    public List<Component> lines() {
        return this.lines;
    }

    public boolean isFront() {
        return this.isFront;
    }

    public boolean isGlowing() {
        return this.isGlowing;
    }

    public DyeColor inkColor() {
        return this.inkColor;
    }

    public boolean allowOpFeatures() {
        return this.allowOpFeatures;
    }

    public boolean isWaxed() {
        return this.waxed;
    }

    @Override
    public Type<SignEditUpdateBlockPayload> type() {
        return ID;
    }
}
