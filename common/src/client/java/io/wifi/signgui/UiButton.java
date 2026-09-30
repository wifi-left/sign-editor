package io.wifi.signgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Flat, square-cornered button used everywhere in the sign editor.
 *
 * <p>Extends {@link AbstractButton} rather than {@code Button}: the {@code Button(Builder)}
 * constructor is a NeoForge patch and does not exist in vanilla, while {@code extractContents} is
 * the platform-independent hook for custom chrome. Labels are truncated to the button width instead
 * of being allowed to bleed over the border.
 */
public class UiButton extends AbstractButton {

    public enum Style {
        /** Filled with the theme colour: the affirmative action. */
        PRIMARY,
        /** Dark panel with a hairline border. */
        SECONDARY,
        /** Dark when off, filled with the theme colour when on. */
        TOGGLE
    }

    @FunctionalInterface
    public interface OnPress {
        void onPress(UiButton button);
    }

    private final Style style;
    private final OnPress onPress;
    private boolean toggled;

    protected UiButton(Style style, int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    public static UiButton of(Style style, Component message, OnPress onPress, int x, int y, int w, int h) {
        return new UiButton(style, x, y, w, h, message, onPress);
    }

    public UiButton toggled(boolean on) {
        this.toggled = on;
        return this;
    }

    public boolean isToggled() {
        return this.toggled;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hot = this.isActive() && this.isHoveredOrFocused();

        int background;
        int border;
        int textColor;
        if (!this.isActive()) {
            background = SignEditorTheme.CARD_BG;
            border = SignEditorTheme.CARD_BORDER;
            textColor = SignEditorTheme.TEXT_MUTED;
        } else if (this.style == Style.PRIMARY) {
            background = hot ? SignEditorTheme.ACCENT_HOVER : SignEditorTheme.ACCENT;
            border = hot ? SignEditorTheme.ACCENT_HOVER : SignEditorTheme.ACCENT;
            textColor = SignEditorTheme.TEXT;
        } else if (this.style == Style.TOGGLE && this.toggled) {
            background = hot ? SignEditorTheme.ACCENT_HOVER : SignEditorTheme.ACCENT_DIM;
            border = SignEditorTheme.ACCENT;
            textColor = SignEditorTheme.TEXT;
        } else {
            background = hot ? SignEditorTheme.BTN_BG_HOVER : SignEditorTheme.BTN_BG;
            border = hot ? SignEditorTheme.ACCENT_BORDER : SignEditorTheme.BTN_BORDER;
            textColor = hot ? SignEditorTheme.TEXT : SignEditorTheme.TEXT_SECONDARY;
        }

        graphics.fill(x, y, x + w, y + h, background);
        graphics.outline(x, y, w, h, border);

        Font font = Minecraft.getInstance().font;
        String label = this.getMessage().getString();
        int available = w - 6;
        if (font.width(label) > available) {
            String ellipsis = "...";
            label = font.plainSubstrByWidth(label, Math.max(0, available - font.width(ellipsis))) + ellipsis;
        }
        graphics.centeredText(font, label, x + w / 2, y + (h - 8) / 2, textColor);
    }
}
