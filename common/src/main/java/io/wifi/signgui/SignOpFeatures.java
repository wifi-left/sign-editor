package io.wifi.signgui;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The sign's {@code allow_op_features} flag, which is vanilla state rather than anything this mod
 * invents: with it on, the server resolves the text components of the sign and lets their click
 * commands run, and with it off they are kept - and stored - exactly as given.
 *
 * <p>Vanilla keeps the flag in a private field and exposes no accessor for it, so the only way in or
 * out is the block entity's own NBT, which is what {@code /data merge block} does too. Both the
 * editor and the server handlers go through here so the key is spelled in exactly one place.
 *
 * <p>The flag belongs to the sign, not to one of its sides: it is read and written once per block
 * entity, and changing it is not undone by the change-side button.
 */
public final class SignOpFeatures {

    /** The NBT key, as {@code SignBlockEntity} spells it. */
    public static final String NBT_KEY = "allow_op_features";

    private SignOpFeatures() {
    }

    /**
     * The flag as the sign holds it, defaulting to off when the key is absent - which is what vanilla
     * stores for off, since it only writes the key when the flag is set.
     */
    public static boolean read(SignBlockEntity sign, HolderLookup.Provider registries) {
        return sign.getUpdateTag(registries).getBooleanOr(NBT_KEY, false);
    }

    /**
     * Writes the flag back onto the sign, by the same read-modify-load round trip {@code /data merge
     * block} uses: the block entity's current data is read, the key is replaced, and the result is
     * loaded back.
     *
     * <p>This has to be the <em>last</em> thing done to the sign, because loading is also the parse
     * step: {@code loadAdditional} runs every line through {@code SignBlockEntity.resolveLines} when
     * the flag is on, and neither {@code setText} nor anything else does. Writing the flag before the
     * lines would leave the flag set and the lines still unparsed, which is exactly the difference
     * between this and the {@code /data merge} the preview's Execute button runs.
     *
     * <p>The load also changes the text without telling anyone - it is not {@code setText} - so the
     * block update is sent from here rather than left to the caller.
     */
    public static void write(SignBlockEntity sign, HolderLookup.Provider registries, boolean allow) {
        CompoundTag tag = sign.getUpdateTag(registries);
        tag.putBoolean(NBT_KEY, allow);
        sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        sign.setChanged();
        Level level = sign.getLevel();
        if (level != null) {
            level.sendBlockUpdated(sign.getBlockPos(), sign.getBlockState(), sign.getBlockState(), 3);
        }
    }
}
