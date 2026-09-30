package io.wifi.signgui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Edit box that also hosts the command suggestion popup.
 *
 * <p>{@link CommandSuggestions} computes its popup position from a hardcoded absolute Y
 * ({@code 72 - (bordered ? 1 : 0)} when {@code anchorToBottom} is false) and never consults the edit
 * box's own position. The pose is therefore translated by {@code desiredTop - 71} so the popup lands
 * next to whichever box is focused, and every mouse coordinate handed back to
 * {@code CommandSuggestions} is de-translated by the same amount.
 *
 * <p>Extends {@link MyTextField} so the field's tooltip steps aside while it is focused and typing,
 * which is precisely when the popup needs the space underneath.
 */
public class MyEditBox extends MyTextField {

    /** Popup top as computed by vanilla for {@code anchorToBottom == false} and a bordered box. */
    private static final int VANILLA_POPUP_TOP = 71;
    private static final int POPUP_LINE_H = 12;
    /** Tallest popup vanilla will draw, i.e. the 7 line limit configured below. */
    private static final int POPUP_MAX_H = 7 * POPUP_LINE_H;
    private static final int POPUP_MARGIN = 2;

    public final SignEditorScreen screen;
    public final CommandSuggestions commandSuggestions;
    private final Minecraft minecraft;
    private boolean suggestionOn;

    public MyEditBox(
            Font font,
            int x,
            int y,
            int width,
            int height,
            Component narration,
            Minecraft minecraft,
            SignEditorScreen screen) {
        super(font, x, y, width, height, narration);
        this.minecraft = minecraft;
        this.screen = screen;
        this.commandSuggestions = new CommandSuggestions(
                minecraft, screen, this, font, true, true, 0, 7, false, Integer.MIN_VALUE);
        this.commandSuggestions.setAllowSuggestions(false);
    }

    public boolean isSuggestionVisible() {
        return this.commandSuggestions.isVisible();
    }

    /**
     * Re-parses the current text. This is the only thing that gives the popup a pending suggestion
     * list to show, so the screen calls it from the field's responder on every edit.
     */
    public void updateCommandInfo() {
        if (this.minecraft.player == null) {
            return; // CommandSuggestions dereferences player.connection while parsing
        }
        this.commandSuggestions.updateCommandInfo();
    }

    /** Vertical translation applied to the popup so it hugs this box. */
    private int suggestionOffsetY() {
        if (this.getBottom() + POPUP_MARGIN + POPUP_MAX_H <= this.screen.height - POPUP_MARGIN) {
            return this.getBottom() + POPUP_MARGIN - VANILLA_POPUP_TOP;
        }
        // No room underneath: flip above the box, but never past the top edge of the screen.
        // CommandSuggestions exposes no size, so the tallest possible popup is reserved rather than
        // the real height; the popup can end up a little further from the box than ideal.
        int above = this.getY() - POPUP_MARGIN - POPUP_MAX_H - VANILLA_POPUP_TOP;
        return Math.max(above, POPUP_MARGIN - VANILLA_POPUP_TOP);
    }

    /**
     * Draws the popup, and when there is no popup to draw, the command usage or error line
     * ("Unknown or incomplete command", {@code [<commands>]}). The screen calls this after all
     * widgets so both paint over them.
     *
     * <p>There is deliberately no {@code isVisible()} guard here: that is true only while a
     * suggestion list exists, and returning early on it is what would hide the usage and error
     * feedback, which vanilla draws on the other branch of {@code extractRenderState}.
     */
    public void renderSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int offsetY = suggestionOffsetY();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, (float) offsetY);
        // extractDeferredElements is deliberately not called here: the screen flushes deferred
        // elements once at the end of the frame, and doing it under this translation would displace
        // every tooltip queued earlier in the frame.
        this.commandSuggestions.extractRenderState(graphics, mouseX, mouseY - offsetY);
        graphics.pose().popMatrix();
    }

    /** Hit test for the popup band. */
    private boolean isOverSuggestions(double mouseX, double mouseY) {
        if (!this.commandSuggestions.isVisible()) {
            return false;
        }
        int top = VANILLA_POPUP_TOP + suggestionOffsetY();
        return mouseY >= top && mouseY < top + POPUP_MAX_H;
    }

    public boolean handleSuggestionMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.isFocused() && this.suggestionOn && isOverSuggestions(event.x(), event.y())) {
            return this.commandSuggestions.mouseClicked(
                    new MouseButtonEvent(event.x(), event.y() - suggestionOffsetY(), event.buttonInfo()));
        }
        return false;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event, doubleClick);
        activeCommandSuggestions();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isFocused() && this.commandSuggestions.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isFocused() && this.suggestionOn) {
            hideCommandSuggestions();
        }
        if (this.isFocused() && this.isVisible()) {
            graphics.outline(this.getX() - 1, this.getY() - 1,
                    this.getWidth() + 2, this.getHeight() + 2, SignEditorTheme.ACCENT_BORDER);
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // SuggestionsList.mouseScrolled re-reads the raw mouse position and tests it against the
        // untranslated rect, so it cannot see this popup's real position. Moving the selection
        // instead scrolls the list exactly the way the arrow keys do - cycle() keeps the highlighted
        // entry inside the visible window - and the raw wheel is only used as a direction.
        if (scrollY == 0 || !this.commandSuggestions.isVisible() || !isOverSuggestions(x, y)) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        boolean up = scrollY > 0;
        this.commandSuggestions.keyPressed(new KeyEvent(
                up ? InputConstants.KEY_UP : InputConstants.KEY_DOWN,
                up ? InputConstants.KEYCODE_UP : InputConstants.KEYCODE_DOWN,
                0));
        return true;
    }

    public void hideCommandSuggestions() {
        this.suggestionOn = false;
        this.commandSuggestions.hide();
        this.commandSuggestions.setAllowSuggestions(false);
    }

    public void activeCommandSuggestions() {
        this.commandSuggestions.showSuggestions(false);
        this.commandSuggestions.setAllowSuggestions(true);
        this.suggestionOn = true;
    }
}
