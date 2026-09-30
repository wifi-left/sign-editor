package io.wifi.signgui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Text field whose tooltip is suppressed while it has focus.
 *
 * <p>A widget shows its tooltip whenever it is focused and the last input came from the keyboard,
 * which is exactly while the user is typing. For the plain fields that is noise, and for the command
 * field the tooltip would cover the suggestion popup, so the tooltip is kept for hovering only.
 */
public class MyTextField extends EditBox {

    private @Nullable Tooltip hint;

    public MyTextField(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
    }

    @Override
    public void setTooltip(@Nullable Tooltip tooltip) {
        this.hint = tooltip;
        super.setTooltip(this.isFocused() ? null : tooltip);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        super.setTooltip(focused ? null : this.hint);
    }
}
