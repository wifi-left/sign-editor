package io.wifi.signgui;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class MyEditBox extends EditBox {
    public SignEditorScreen screen;
    public CommandSuggestions commandSuggestions;
    private boolean suggestionOn = false;
    private final int mysteryOffset = 54;

    public MyEditBox(Font font, int x, int y, int width, int height, Component narration, Minecraft minecraft,
            SignEditorScreen screen) {
        this(font, x, y, width, height, (EditBox) null, narration, minecraft, screen);
    }

    public MyEditBox(Font font, int width, int height, Component narration, Minecraft minecraft,
            SignEditorScreen screen) {
        this(font, 0, 0, width, height, narration, minecraft, screen);
    }

    public MyEditBox(Font font, int x, int y, int width, int height, @Nullable EditBox oldBox, Component narration,
            Minecraft minecraft, SignEditorScreen screen) {
        super(font, x, y, width, height, oldBox, narration);
        this.commandSuggestions = new CommandSuggestions(minecraft, screen, this, font, true, true,
                0, 7, false, Integer.MIN_VALUE);
        this.commandSuggestions.setAllowSuggestions(false);
        this.screen = screen;
    }

    @Override
    public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
        if (!this.isActive()) {
            this.screen.hideAllSuggestions();
        }
        super.onClick(event, doubleClick);
        this.activeCommandSuggestions();
    }

    public void updateCommandInfo() {
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (this.isFocused()) {
            if (this.commandSuggestions.keyPressed(event)) {
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
            final float a) {
        if (!this.isFocused() && this.suggestionOn) {
            hideCommandSuggestions();
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
    }

    public void renderSuggestions(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
            final float a) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(2, this.getY() - mysteryOffset); // 神秘数字72
        commandSuggestions.extractRenderState(graphics, mouseX - 2, mouseY - (this.getY() - mysteryOffset));
        graphics.extractDeferredElements(mouseX, mouseY, a);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
        if (this.isFocused() && this.suggestionOn) {
            if (commandSuggestions.mouseScrolled(scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    public boolean handleSuggestionMouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        if (this.isFocused() && this.suggestionOn) {
            if (commandSuggestions
                    .mouseClicked(new MouseButtonEvent(event.x() - 2, event.y() - (this.getY() - mysteryOffset),
                            event.buttonInfo()))) {
                return true;
            }
        }
        return false;
    }

    public void hideCommandSuggestions() {
        suggestionOn = false;
        commandSuggestions.hide();
        this.setHint(Component.literal(""));
        commandSuggestions.setAllowSuggestions(false);
    }

    public void activeCommandSuggestions() {
        commandSuggestions.showSuggestions(false);
        commandSuggestions.setAllowSuggestions(true);
        suggestionOn = true;
    }

}
