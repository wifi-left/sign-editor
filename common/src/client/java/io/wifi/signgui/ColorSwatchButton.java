package io.wifi.signgui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Colour picker button: left click cycles through the 16 dye colours plus an "auto" state,
 * shift+click returns to auto.
 *
 * <p>Two things this deliberately avoids. First, {@code auto} means the line carries no explicit
 * colour style, so it inherits the sign's ink colour - which is what an untouched sign line does.
 * Second, every dye is written out as {@code #RRGGBB} rather than by name, because
 * {@link TextColor#parseColor} only understands the 16 legacy chat colour names plus hex; dye names
 * such as {@code light_blue} or {@code magenta} are rejected outright and would silently do nothing.
 */
public class ColorSwatchButton extends UiButton {

    /** No explicit colour: inherit the sign's ink colour. */
    private static final int STATE_AUTO = -1;
    /** A colour loaded from an existing sign that matches none of the 16 dyes. */
    private static final int STATE_CUSTOM = -2;

    private static final DyeColor[] DYES = DyeColor.values();

    private int state = STATE_AUTO;
    private int customRgb;
    /** When false the {@code auto} state is skipped, which is what the ink colour needs. */
    private boolean allowAuto = true;
    private @Nullable Runnable onChange;
    private String autoNameKey = "gui.wifi.signgui.color.auto";
    private String autoHintKey = "gui.wifi.signgui.color.hint";

    private ColorSwatchButton(int x, int y, int w, int h) {
        super(Style.SECONDARY, x, y, w, h, Component.empty(), button -> {
        });
    }

    public static ColorSwatchButton of(int x, int y, int w, int h) {
        return new ColorSwatchButton(x, y, w, h);
    }

    /**
     * Disables the {@code auto} state. Used for the sign's ink colour, which always has to resolve
     * to a concrete dye for the server payload.
     */
    public ColorSwatchButton allowAuto(boolean allow) {
        this.allowAuto = allow;
        if (!allow && this.state == STATE_AUTO) {
            this.state = DyeColor.BLACK.ordinal();
        }
        sync();
        return this;
    }

    /** Fired after the user changes the colour, never while loading one from a sign. */
    public ColorSwatchButton onChange(Runnable listener) {
        this.onChange = listener;
        return this;
    }

    /**
     * Renames the {@code auto} state and its hint. The shadow colour uses this: to a shadow, "auto"
     * means "no explicit shadow", which reads better as "none".
     */
    public ColorSwatchButton autoLabels(String nameKey, String hintKey) {
        this.autoNameKey = nameKey;
        this.autoHintKey = hintKey;
        sync();
        return this;
    }

    /** The full ARGB value, or 0 when the state means "no explicit colour". */
    public int argb() {
        int rgb = currentRgb();
        return rgb < 0 ? 0 : 0xFF000000 | rgb;
    }

    /** Loads an ARGB value; 0 means "no explicit colour". */
    public void setFromArgb(int argb) {
        if (argb == 0) {
            this.state = STATE_AUTO;
        } else {
            int rgb = argb & 0xFFFFFF;
            this.state = STATE_AUTO;
            for (DyeColor dye : DYES) {
                if ((dye.getTextColor() & 0xFFFFFF) == rgb) {
                    this.state = dye.ordinal();
                    break;
                }
            }
            if (this.state == STATE_AUTO) {
                this.customRgb = rgb;
                this.state = STATE_CUSTOM;
            }
        }
        if (!this.allowAuto && this.state == STATE_AUTO) {
            this.state = DyeColor.BLACK.ordinal();
        }
        sync();
    }

    /** Loads the state from a component style; {@code null} means "no explicit colour". */
    public void setFromTextColor(@Nullable TextColor color) {
        if (color == null) {
            this.state = STATE_AUTO;
        } else {
            int rgb = color.getValue() & 0xFFFFFF;
            this.state = STATE_AUTO;
            for (DyeColor dye : DYES) {
                if ((dye.getTextColor() & 0xFFFFFF) == rgb) {
                    this.state = dye.ordinal();
                    break;
                }
            }
            if (this.state == STATE_AUTO) {
                this.customRgb = rgb;
                this.state = STATE_CUSTOM;
            }
        }
        if (!this.allowAuto && this.state == STATE_AUTO) {
            this.state = DyeColor.BLACK.ordinal();
        }
        sync();
    }

    public boolean isAuto() {
        return this.state == STATE_AUTO;
    }

    /**
     * The colour to embed in a component style, as {@code #RRGGBB}, or {@code null} when the line
     * should inherit the sign's ink colour.
     */
    @Nullable
    public String styleColor() {
        int rgb = currentRgb();
        return rgb < 0 ? null : String.format("#%06X", rgb);
    }

    /** The dye to send for the sign's ink colour. */
    public DyeColor inkColor() {
        int rgb = currentRgb();
        if (rgb >= 0) {
            for (DyeColor dye : DYES) {
                if ((dye.getTextColor() & 0xFFFFFF) == rgb) {
                    return dye;
                }
            }
        }
        return DyeColor.BLACK;
    }

    /** {@code -1} for the auto state, otherwise a 0xRRGGBB value. */
    private int currentRgb() {
        if (this.state == STATE_AUTO) {
            return -1;
        }
        if (this.state == STATE_CUSTOM) {
            return this.customRgb;
        }
        return DYES[this.state].getTextColor() & 0xFFFFFF;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!this.allowAuto) {
            if (input.hasShiftDown()) {
                this.state = DyeColor.BLACK.ordinal();
            } else if (this.state < 0 || this.state >= DYES.length - 1) {
                this.state = 0;
            } else {
                this.state += 1;
            }
        } else if (input.hasShiftDown()) {
            this.state = STATE_AUTO;
        } else if (this.state == STATE_AUTO) {
            this.state = 0;
        } else if (this.state >= DYES.length - 1) {
            this.state = STATE_AUTO;
        } else {
            // Covers STATE_CUSTOM (-2) too: the next press lands on STATE_AUTO.
            this.state += 1;
        }
        sync();
        if (this.onChange != null) {
            this.onChange.run();
        }
    }

    private void sync() {
        Component name = currentName();
        // Doubles as the narration text, which is why it is the colour name and not "swatch".
        this.setMessage(name);
        this.setTooltip(Tooltip.create(name.copy()
                .append("\n")
                .append(Component.translatable(this.autoHintKey).withStyle(ChatFormatting.GRAY))));
    }

    private Component currentName() {
        if (this.state == STATE_AUTO) {
            return Component.translatable(this.autoNameKey);
        }
        if (this.state == STATE_CUSTOM) {
            return Component.literal(String.format("#%06X", this.customRgb));
        }
        return Component.translatable("color.minecraft." + DYES[this.state].getSerializedName());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hot = this.isActive() && this.isHoveredOrFocused();

        graphics.fill(x, y, x + w, y + h, hot ? SignEditorTheme.BTN_BG_HOVER : SignEditorTheme.BTN_BG);
        graphics.outline(x, y, w, h, hot ? SignEditorTheme.ACCENT_BORDER : SignEditorTheme.BTN_BORDER);

        int rgb = currentRgb();
        if (rgb < 0) {
            Font font = Minecraft.getInstance().font;
            graphics.centeredText(font, Component.translatable("gui.wifi.signgui.color.auto"),
                    x + w / 2, y + (h - 8) / 2, SignEditorTheme.TEXT_MUTED);
        } else {
            graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF000000 | rgb);
            graphics.outline(x + 2, y + 2, w - 4, h - 4, SignEditorTheme.SWATCH_BORDER);
        }
    }
}
