package io.wifi.signgui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;

import io.wifi.signgui.SignEditorLayout.Layout;
import io.wifi.signgui.SignEditorLayout.Metrics;
import io.wifi.signgui.SignEditorLayout.Rect;
import io.wifi.signgui.SignEditorLayout.RowLayout;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.jspecify.annotations.Nullable;

/**
 * Sign editor screen.
 *
 * <p>Every coordinate comes from {@link SignEditorLayout} and all widget state lives in a small
 * model that outlives {@code init()}. That is what makes a window resize cheap: {@code init()}
 * simply rebuilds the widgets from the model, so the active tab, the scroll offset and the focused
 * field all survive without any save/restore dance.
 */
public class SignEditorScreen extends Screen {

    private enum Tab {
        EDIT, NBT_PREVIEW
    }

    /** Which control of a row has focus, used to put focus back after a rebuild. */
    private enum Field {
        TEXT, COLOR, CMD, HEX, SHADOW, RAW
    }

    /** Format toggles, in button order: bold, italic, underline, strikethrough, obfuscated. */
    private static final String[] FMT_CODES = { "&l", "&o", "&n", "&m", "&k" };
    /** Indices into {@link #FMT_CODES} and the per-line format flags. */
    private static final int FMT_BOLD = 0;
    private static final int FMT_ITALIC = 1;
    private static final int FMT_UNDERLINE = 2;
    private static final int FMT_STRIKE = 3;
    private static final int FMT_OBFUSCATED = 4;
    /** Ctrl + these keys toggles the matching format on the line being edited. */
    private static final int[] FORMAT_SHORTCUTS = {
            InputConstants.KEY_B, InputConstants.KEY_I, InputConstants.KEY_U,
            InputConstants.KEY_T, InputConstants.KEY_K,
    };
    /** Button captions: the conventional initials rather than the code letters. */
    private static final String FMT_LABELS = "BIUSO";
    private static final int ROWS = SignEditorLayout.ROWS;

    /**
     * The only length cap left, and it is the protocol's rather than a design choice: every string
     * inside a component is written with the default {@code FriendlyByteBuf.writeUtf} limit of 32767
     * characters. Line lengths are otherwise left to the sign renderer, which is what actually
     * decides how much fits on a sign.
     */
    private static final int PROTOCOL_STRING_LIMIT = 32767;
    /** The colour fields hold a name or a hex value, so a short cap is all they need. */
    private static final int COLOR_FIELD_LIMIT = 16;
    /** The sixteen legacy colour codes, in the order the game documents them. */
    private static final String LEGACY_COLOR_CODES = "0123456789abcdef";
    /** SNBT reader for any root tag; building one per keystroke would be wasteful. */
    private static final TagParser<Tag> SNBT_PARSER = TagParser.create(NbtOps.INSTANCE);

    // ---- model: the source of truth for widget content ----
    private final String[] texts = new String[ROWS];
    private final String[] commands = new String[ROWS];
    private final @Nullable TextColor[] colors = new TextColor[ROWS];
    /** Root-style format flags per line, indexed like {@link #FMT_CODES}. */
    private final boolean[][] formats = new boolean[ROWS][FMT_CODES.length];
    /** Per-line shadow colour as ARGB; {@code null} means the line has no explicit shadow. */
    private final @Nullable Integer[] shadowColors = new Integer[ROWS];
    /** Per-line raw component mode, with the text being edited and the last component it parsed to. */
    private final boolean[] rawModes = new boolean[ROWS];
    private final String[] rawTexts = new String[ROWS];
    private final MutableComponent[] rawComponents = new MutableComponent[ROWS];
    private final boolean[] rawValid = new boolean[ROWS];
    /** Why the fields cannot hold this line; empty when they can. Drives the row warning. */
    private final List<List<Component>> rawReasons = new ArrayList<>(ROWS);
    private boolean isGlowing;
    /** Ink colour of the whole side; a model field so a rebuild cannot revert the user's choice. */
    private TextColor inkColor = TextColor.fromRgb(0);
    private Tab currentTab = Tab.EDIT;
    private int scroll;
    private int focusRow;
    private Field focusField = Field.TEXT;

    // ---- widgets, all rebuilt by init() ----
    private Layout layout;
    private final EditBox[] textFields = new EditBox[ROWS];
    private final ColorSwatchButton[] colorButtons = new ColorSwatchButton[ROWS];
    private final MyTextField[] hexFields = new MyTextField[ROWS];
    private final MyEditBox[] commandFields = new MyEditBox[ROWS];
    private final UiButton[][] fmtButtons = new UiButton[ROWS][FMT_CODES.length];
    private final UiButton[] nbtToggles = new UiButton[ROWS];
    private final ColorSwatchButton[] shadowSwatches = new ColorSwatchButton[ROWS];
    private final MyTextField[] shadowHexFields = new MyTextField[ROWS];
    private final MultiLineEditBox[] rawFields = new MultiLineEditBox[ROWS];
    private final UiButton[] rawFormatButtons = new UiButton[ROWS];
    private final UiButton[] rawMinifyButtons = new UiButton[ROWS];
    private UiButton glowToggle;
    private ColorSwatchButton inkSwatch;
    private UiButton changeSideButton;
    private UiButton reloadButton;
    private UiButton cancelButton;
    private UiButton confirmButton;
    private UiButton tabEditButton;
    private UiButton tabNbtButton;
    private UiButton copyButton;
    private UiButton executeButton;
    private MultiLineEditBox nbtPreview;

    // ---- interaction state ----
    private boolean draggingScrollbar;
    private int scrollGrabDy;
    private @Nullable GuiEventListener lastFocused;
    private boolean restoreFocusPending;
    /** The narrator hotkey setting as it was before this screen held it off, or null when not held. */
    private @Nullable Boolean narratorHotkey;

    private final SignBlockEntity sign;

    public SignEditorScreen(SignBlockEntity sign) {
        super(Component.translatable("gui.wifi.signgui.title.plain"));
        this.sign = sign;
        for (int i = 0; i < ROWS; i++) {
            this.rawComponents[i] = Component.empty();
            this.rawTexts[i] = "";
            this.rawReasons.add(new ArrayList<>());
        }
        reloadFromSign();
    }

    // ------------------------------------------------------------------ layout plumbing

    private Metrics metrics() {
        return new Metrics(
                this.font.width(Component.translatable("gui.wifi.signgui.glow.label")),
                this.font.width(Component.translatable("gui.wifi.signgui.inkcolor.label")),
                this.font.width(Component.translatable("gui.wifi.signgui.color.label")),
                this.font.width(Component.translatable("gui.wifi.signgui.shadow.label")));
    }

    private static void place(AbstractWidget widget, Rect rect) {
        widget.setX(rect.x());
        widget.setY(rect.y());
    }

    private static void setVis(AbstractWidget widget, boolean visible) {
        widget.visible = visible;
        widget.active = visible;
    }

    /** Recomputes the geometry and applies it to every widget. Safe to call at any time. */
    private void applyLayout() {
        this.layout = SignEditorLayout.compute(this.width, this.height, this.scroll, metrics());
        this.scroll = this.layout.scroll;
        Layout l = this.layout;

        place(this.tabEditButton, l.tabEdit);
        place(this.tabNbtButton, l.tabNbt);
        for (int i = 0; i < ROWS; i++) {
            RowLayout row = l.rows.get(i);
            place(this.textFields[i], row.text());
            place(this.colorButtons[i], row.colorSwatch());
            place(this.hexFields[i], row.colorHex());
            place(this.shadowSwatches[i], row.shadowSwatch());
            place(this.shadowHexFields[i], row.shadowHex());
            place(this.nbtToggles[i], row.nbt());
            place(this.rawFields[i], row.rawText());
            place(this.rawFormatButtons[i], row.rawFormat());
            place(this.rawMinifyButtons[i], row.rawMinify());
            place(this.commandFields[i], row.cmd());
            for (int k = 0; k < FMT_CODES.length; k++) {
                place(this.fmtButtons[i][k], row.fmtButtons()[k]);
            }
        }
        place(this.glowToggle, l.glowToggle);
        place(this.inkSwatch, l.inkSwatch);
        place(this.changeSideButton, l.changeSideButton());
        place(this.reloadButton, l.reloadButton());
        place(this.cancelButton, l.cancelButton());
        place(this.confirmButton, l.confirmButton());
        place(this.copyButton, l.nbtCopy);
        place(this.executeButton, l.nbtExecute);
        this.nbtPreview.setX(l.nbtPreview.x());
        this.nbtPreview.setY(l.nbtPreview.y());

        applyVisibility();
        keepFocusVisible();
    }

    /** The single place that decides what is on screen: the active tab *and* the scroll viewport. */
    private void applyVisibility() {
        boolean edit = this.currentTab == Tab.EDIT;
        for (int i = 0; i < ROWS; i++) {
            boolean on = edit && this.layout.rowVisible(i);
            // In raw mode the component governs everything, so every per-field control for that
            // row steps aside and one wide editor takes their place.
            boolean fields = on && !this.rawModes[i];
            setVis(this.textFields[i], fields);
            setVis(this.colorButtons[i], fields);
            setVis(this.hexFields[i], fields);
            setVis(this.commandFields[i], fields);
            setVis(this.shadowSwatches[i], fields);
            setVis(this.shadowHexFields[i], fields && this.layout.shadowHexPresent);
            for (UiButton button : this.fmtButtons[i]) {
                setVis(button, fields);
            }
            boolean rawMode = on && this.rawModes[i];
            setVis(this.rawFields[i], rawMode);
            setVis(this.rawFormatButtons[i], rawMode);
            setVis(this.rawMinifyButtons[i], rawMode);
            // The mode toggle stays available in both modes, otherwise a raw row could never leave.
            setVis(this.nbtToggles[i], on);
        }
        setVis(this.glowToggle, edit);
        setVis(this.inkSwatch, edit);
        setVis(this.changeSideButton, edit);
        setVis(this.reloadButton, edit);
        setVis(this.cancelButton, edit);
        setVis(this.confirmButton, edit);

        setVis(this.copyButton, !edit);
        setVis(this.executeButton, !edit);
        setVis(this.nbtPreview, !edit);

        this.tabEditButton.toggled(edit);
        this.tabNbtButton.toggled(!edit);
        refreshConfirmState();
    }

    /** A line whose raw text does not parse would be sent as its last valid form, so OK is barred. */
    private void refreshConfirmState() {
        if (this.confirmButton == null) {
            return;
        }
        boolean ok = true;
        for (int i = 0; i < ROWS; i++) {
            if (this.rawModes[i] && !this.rawValid[i]) {
                ok = false;
                break;
            }
        }
        this.confirmButton.active = ok;
    }

    /**
     * Hiding a widget makes it inactive, and an inactive widget would keep the focus while going
     * inert. If the focused control scrolled out of view it is therefore handed to the same kind of
     * control on the nearest visible row. Focus that is legitimately somewhere else - a footer
     * button, a tab - is left alone.
     */
    private void keepFocusVisible() {
        if (this.currentTab != Tab.EDIT) {
            return;
        }
        GuiEventListener focused = getFocused();
        if (!(focused instanceof AbstractWidget widget) || widget.active) {
            return;
        }
        int first = this.layout.firstVisibleRow();
        int last = Math.min(first + this.layout.rowsVisible - 1, ROWS - 1);
        int row = SignEditorLayout.clamp(this.focusRow, first, last);
        // The same control on the nearest visible row, or - in raw mode, where most controls are
        // hidden - any control of that row that is actually usable.
        for (Field field : fieldsStartingAt(this.focusField)) {
            GuiEventListener replacement = widgetFor(row, field);
            if (replacement instanceof AbstractWidget candidate && candidate.active && candidate != focused) {
                this.focusRow = row;
                this.focusField = field;
                setFocused(candidate);
                return;
            }
        }
    }

    /** {@code first} followed by the rest of the enum, so a fallback still prefers the same kind. */
    private static List<Field> fieldsStartingAt(Field first) {
        List<Field> order = new ArrayList<>(Field.values().length);
        order.add(first);
        for (Field field : Field.values()) {
            if (field != first) {
                order.add(field);
            }
        }
        return order;
    }

    private @Nullable GuiEventListener widgetFor(int row, Field field) {
        if (row < 0 || row >= ROWS) {
            return null;
        }
        return switch (field) {
            case TEXT -> this.textFields[row];
            case COLOR -> this.colorButtons[row];
            case CMD -> this.commandFields[row];
            case HEX -> this.hexFields[row];
            case SHADOW -> this.shadowSwatches[row];
            case RAW -> this.rawFields[row];
        };
    }

    /** Keeps the focus bookkeeping and the suggestion popup in step with the real focus. */
    private void syncFocus() {
        GuiEventListener focused = getFocused();
        if (focused == this.lastFocused) {
            return;
        }
        this.lastFocused = focused;
        for (int i = 0; i < ROWS; i++) {
            MyEditBox command = this.commandFields[i];
            if (command == focused) {
                command.activeCommandSuggestions();
            } else {
                command.hideCommandSuggestions();
            }
            if (this.textFields[i] == focused) {
                this.focusRow = i;
                this.focusField = Field.TEXT;
            } else if (this.colorButtons[i] == focused) {
                this.focusRow = i;
                this.focusField = Field.COLOR;
            } else if (this.hexFields[i] == focused) {
                this.focusRow = i;
                this.focusField = Field.HEX;
            } else if (this.shadowSwatches[i] == focused || this.shadowHexFields[i] == focused) {
                this.focusRow = i;
                this.focusField = Field.SHADOW;
            } else if (this.rawFields[i] == focused) {
                this.focusRow = i;
                this.focusField = Field.RAW;
            } else if (command == focused) {
                this.focusRow = i;
                this.focusField = Field.CMD;
            }
        }
    }

    /** Moves the scroll offset to {@code desired}, snapped to the row grid by the layout. */
    private void setScroll(int desired) {
        Layout next = SignEditorLayout.compute(this.width, this.height, desired, metrics());
        if (next.scroll == this.scroll) {
            return;
        }
        this.scroll = next.scroll;
        applyLayout();
    }

    // ------------------------------------------------------------------ init

    @Override
    protected void init() {
        super.init();

        this.layout = SignEditorLayout.compute(this.width, this.height, this.scroll, metrics());
        this.scroll = this.layout.scroll;
        Layout l = this.layout;

        this.tabEditButton = UiButton.of(UiButton.Style.TOGGLE,
                Component.translatable("gui.wifi.signgui.tab.edit"), b -> switchTab(Tab.EDIT),
                l.tabEdit.x(), l.tabEdit.y(), l.tabEdit.w(), l.tabEdit.h());
        this.tabNbtButton = UiButton.of(UiButton.Style.TOGGLE,
                Component.translatable("gui.wifi.signgui.tab.nbt"), b -> switchTab(Tab.NBT_PREVIEW),
                l.tabNbt.x(), l.tabNbt.y(), l.tabNbt.w(), l.tabNbt.h());
        this.tabEditButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.tab.edit")));
        this.tabNbtButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.tab.nbt")));
        addRenderableWidget(this.tabEditButton);
        addRenderableWidget(this.tabNbtButton);

        buildRowWidgets(l);

        // Glow row.
        this.glowToggle = UiButton.of(UiButton.Style.TOGGLE, glowLabel(), b -> {
            this.isGlowing = !this.isGlowing;
            this.glowToggle.setMessage(glowLabel());
            this.glowToggle.toggled(this.isGlowing);
            refreshNbt();
        }, l.glowToggle.x(), l.glowToggle.y(), l.glowToggle.w(), l.glowToggle.h()).toggled(this.isGlowing);
        this.glowToggle.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.glow")));

        this.inkSwatch = ColorSwatchButton.of(l.inkSwatch.x(), l.inkSwatch.y(), l.inkSwatch.w(), l.inkSwatch.h())
                .allowAuto(false)
                .autoLabels("gui.wifi.signgui.color.auto", "gui.wifi.signgui.color.hint.ink")
                .onChange(() -> {
                    this.inkColor = textColorOf(this.inkSwatch);
                    refreshNbt();
                });
        this.inkSwatch.setFromTextColor(this.inkColor);
        this.inkSwatch.setTooltip(
                Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.ink")));

        // Action row.
        this.changeSideButton = UiButton.of(UiButton.Style.SECONDARY, changeSideLabel(), b -> changeSide(),
                l.changeSideButton().x(), l.changeSideButton().y(), l.changeSideButton().w(), l.changeSideButton().h());
        this.changeSideButton.setTooltip(
                Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.changeside", otherSideName())));
        this.reloadButton = UiButton.of(UiButton.Style.SECONDARY,
                Component.translatable("gui.wifi.signgui.button.reload"), b -> reloadFromSign(),
                l.reloadButton().x(), l.reloadButton().y(), l.reloadButton().w(), l.reloadButton().h());
        this.reloadButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.reload")));
        this.cancelButton = UiButton.of(UiButton.Style.SECONDARY, Component.translatable("gui.cancel"),
                b -> this.onClose(),
                l.cancelButton().x(), l.cancelButton().y(), l.cancelButton().w(), l.cancelButton().h());
        this.cancelButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.cancel")));
        this.confirmButton = UiButton.of(UiButton.Style.PRIMARY, Component.translatable("gui.ok"),
                b -> doConfirm(),
                l.confirmButton().x(), l.confirmButton().y(), l.confirmButton().w(), l.confirmButton().h());
        this.confirmButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.confirm")));

        addRenderableWidget(this.glowToggle);
        addRenderableWidget(this.inkSwatch);
        addRenderableWidget(this.changeSideButton);
        addRenderableWidget(this.reloadButton);
        addRenderableWidget(this.cancelButton);
        addRenderableWidget(this.confirmButton);

        // NBT tab.
        this.copyButton = UiButton.of(UiButton.Style.SECONDARY,
                Component.translatable("gui.wifi.signgui.nbt.copy"), b -> copyCommand(),
                l.nbtCopy.x(), l.nbtCopy.y(), l.nbtCopy.w(), l.nbtCopy.h());
        this.copyButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.nbt.copy")));
        this.executeButton = UiButton.of(UiButton.Style.PRIMARY,
                Component.translatable("gui.wifi.signgui.nbt.execute"), b -> executeCommand(),
                l.nbtExecute.x(), l.nbtExecute.y(), l.nbtExecute.w(), l.nbtExecute.h());
        this.executeButton.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.nbt.execute")));
        this.nbtPreview = MultiLineEditBox.builder()
                .setX(l.nbtPreview.x())
                .setY(l.nbtPreview.y())
                .setPlaceholder(Component.translatable("gui.wifi.signgui.nbt.preview"))
                .build(this.font, l.nbtPreview.w(), l.nbtPreview.h(),
                        Component.translatable("gui.wifi.signgui.nbt.preview"));
        // No character limit on purpose: setting one makes MultiLineEditBox draw an "n/max" counter
        // at getX() + width, getY() + height + 4, which spills past the box's own frame.
        this.nbtPreview.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.nbt.preview")));
        addRenderableWidget(this.copyButton);
        addRenderableWidget(this.executeButton);
        addRenderableWidget(this.nbtPreview);

        refreshNbt();
        applyLayout();
        this.restoreFocusPending = this.currentTab == Tab.EDIT;
        holdNarratorHotkey();
    }

    private void buildRowWidgets(Layout l) {
        for (int i = 0; i < ROWS; i++) {
            RowLayout row = l.rows.get(i);
            final int index = i;

            MyTextField text = new MyTextField(this.font, row.text().x(), row.text().y(), row.text().w(),
                    row.text().h(), Component.translatable("gui.wifi.signgui.signtext", i + 1));
            text.setMaxLength(PROTOCOL_STRING_LIMIT);
            text.setValue(this.texts[i]);
            text.setTextColor(0xFFFFFFFF);
            text.setResponder(value -> {
                // Read back the widget: setValue truncates to maxLength and hands the responder the
                // untruncated string, which would leave the model holding text the box never showed.
                this.texts[index] = text.getValue();
                refreshFmtButtons(index);
            });
            text.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.textfield")));
            this.textFields[i] = text;

            // The swatch and its hex sit next to each other on the colour line. They reach each
            // other through the arrays rather than through locals, so neither lambda captures the
            // other's variable, which is not yet assigned when the first one is built.
            ColorSwatchButton color = ColorSwatchButton.of(row.colorSwatch().x(), row.colorSwatch().y(),
                    row.colorSwatch().w(), row.colorSwatch().h());
            color.setFromTextColor(this.colors[i]);
            color.onChange(() -> {
                this.colors[index] = textColorOf(this.colorButtons[index]);
                this.hexFields[index].setValue(colorText(this.colors[index]));
                refreshNbtIfPreviewing();
            });
            this.colorButtons[i] = color;

            MyTextField hex = new MyTextField(this.font, row.colorHex().x(), row.colorHex().y(), row.colorHex().w(),
                    row.colorHex().h(), Component.translatable("gui.wifi.signgui.hexcolor"));
            hex.setMaxLength(COLOR_FIELD_LIMIT);
            hex.setTextColor(0xFFFFFFFF);
            hex.setHint(Component.literal("#RRGGBB"));
            hex.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.hex")));
            hex.setValue(colorText(this.colors[i]));
            hex.setResponder(value -> {
                // Read the widget back: setValue truncates and hands the responder the full string.
                TextColor parsed = parseColorInput(hex.getValue());
                boolean valid = isAutoColor(value) || parsed != null;
                // Unparseable entries are flagged in red and simply do not take effect.
                hex.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
                if (valid) {
                    this.colors[index] = parsed;
                    this.colorButtons[index].setFromTextColor(parsed);
                    refreshNbtIfPreviewing();
                }
            });
            this.hexFields[i] = hex;

            ColorSwatchButton shadow = ColorSwatchButton.of(row.shadowSwatch().x(), row.shadowSwatch().y(),
                    row.shadowSwatch().w(), row.shadowSwatch().h());
            shadow.autoLabels("gui.wifi.signgui.shadow.none", "gui.wifi.signgui.shadow.hint");
            shadow.setFromArgb(this.shadowColors[i] == null ? 0 : this.shadowColors[i]);
            shadow.onChange(() -> {
                this.shadowColors[index] = argbOrNull(this.shadowSwatches[index]);
                this.shadowHexFields[index].setValue(shadowText(this.shadowColors[index]));
                refreshNbtIfPreviewing();
            });
            this.shadowSwatches[i] = shadow;

            MyTextField shadowHex = new MyTextField(this.font, row.shadowHex().x(), row.shadowHex().y(),
                    row.shadowHex().w(), row.shadowHex().h(),
                    Component.translatable("gui.wifi.signgui.shadowcolor"));
            shadowHex.setMaxLength(COLOR_FIELD_LIMIT + 2);
            shadowHex.setTextColor(0xFFFFFFFF);
            shadowHex.setHint(Component.literal("#RRGGBB"));
            shadowHex.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.shadow")));
            shadowHex.setValue(shadowText(this.shadowColors[i]));
            shadowHex.setResponder(value -> {
                Integer parsed = parseShadowInput(shadowHex.getValue());
                boolean valid = isNoShadow(shadowHex.getValue()) || parsed != null;
                // As with the colour field, an unparseable entry is flagged and ignored.
                shadowHex.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
                if (valid) {
                    this.shadowColors[index] = parsed;
                    this.shadowSwatches[index].setFromArgb(parsed == null ? 0 : parsed);
                    refreshNbtIfPreviewing();
                }
            });
            this.shadowHexFields[i] = shadowHex;

            // The raw-component mode toggle, always available so a row can always leave the mode.
            UiButton nbtToggle = UiButton.of(UiButton.Style.TOGGLE, Component.literal("NBT"),
                    b -> toggleRaw(index), row.nbt().x(), row.nbt().y(), row.nbt().w(), row.nbt().h())
                    .toggled(this.rawModes[i]);
            this.nbtToggles[i] = nbtToggle;
            refreshRawTooltip(i);

            MultiLineEditBox raw = MultiLineEditBox.builder()
                    .setX(row.rawText().x())
                    .setY(row.rawText().y())
                    .setPlaceholder(Component.translatable("gui.wifi.signgui.rawtext", i + 1))
                    .build(this.font, row.rawText().w(), row.rawText().h(),
                            Component.translatable("gui.wifi.signgui.rawtext", i + 1));
            raw.setValue(this.rawTexts[i]);
            raw.setValueListener(value -> {
                this.rawTexts[index] = raw.getValue();
                MutableComponent parsed = parseRaw(this.rawTexts[index]);
                this.rawValid[index] = parsed != null;
                // Recomputed from what the text now holds, on every edit: the warning clears as soon
                // as the component becomes something the fields could carry, and an unparseable text
                // says so instead of leaving the disabled OK button unexplained.
                List<Component> reasons = this.rawReasons.get(index);
                reasons.clear();
                if (parsed != null) {
                    this.rawComponents[index] = parsed;
                    collectReasons(parsed, Style.EMPTY, null, reasons);
                } else {
                    reasons.add(Component.translatable("msg.signgui.raw_reason.unparseable"));
                }
                refreshRawTooltip(index);
                refreshConfirmState();
                refreshNbtIfPreviewing();
            });
            this.rawFields[i] = raw;
            refreshRawTooltip(i);

            // Re-printing goes through the parsed tag, so it can never change what the text means.
            UiButton rawFormat = UiButton.of(UiButton.Style.SECONDARY,
                    Component.translatable("gui.wifi.signgui.raw.format"),
                    b -> reprintRaw(index, true),
                    row.rawFormat().x(), row.rawFormat().y(), row.rawFormat().w(), row.rawFormat().h());
            rawFormat.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.raw.format")));
            this.rawFormatButtons[i] = rawFormat;

            UiButton rawMinify = UiButton.of(UiButton.Style.SECONDARY,
                    Component.translatable("gui.wifi.signgui.raw.minify"),
                    b -> reprintRaw(index, false),
                    row.rawMinify().x(), row.rawMinify().y(), row.rawMinify().w(), row.rawMinify().h());
            rawMinify.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.raw.minify")));
            this.rawMinifyButtons[i] = rawMinify;

            MyEditBox command = new MyEditBox(this.font, row.cmd().x(), row.cmd().y(), row.cmd().w(), row.cmd().h(),
                    Component.translatable("gui.wifi.signgui.signcmd", i + 1), this.minecraft, this);
            command.setMaxLength(PROTOCOL_STRING_LIMIT);
            command.setValue(this.commands[i]);
            command.setTextColor(0xFFFFFFFF);
            command.setResponder(value -> {
                this.commands[index] = command.getValue();
                // This is what actually produces the suggestion list: CommandSuggestions only has a
                // pending parse to show once updateCommandInfo has run.
                command.updateCommandInfo();
                refreshNbtIfPreviewing();
            });
            command.setTooltip(Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.cmd")));
            this.commandFields[i] = command;

            for (int k = 0; k < FMT_CODES.length; k++) {
                Rect rect = row.fmtButtons()[k];
                final int format = k;
                UiButton button = UiButton.of(UiButton.Style.TOGGLE,
                        Component.literal(String.valueOf(FMT_LABELS.charAt(k))),
                        b -> toggleFormat(index, format), rect.x(), rect.y(), rect.w(), rect.h());
                button.setTooltip(Tooltip.create(tooltipForFormat(k)));
                this.fmtButtons[i][k] = button;
            }

            addRenderableWidget(text);
            addRenderableWidget(color);
            addRenderableWidget(hex);
            addRenderableWidget(shadow);
            addRenderableWidget(shadowHex);
            addRenderableWidget(nbtToggle);
            addRenderableWidget(raw);
            addRenderableWidget(rawFormat);
            addRenderableWidget(rawMinify);
            addRenderableWidget(command);
            for (UiButton button : this.fmtButtons[i]) {
                addRenderableWidget(button);
            }
        }
        refreshFmtButtons(-1);
    }

    private static @Nullable TextColor textColorOf(ColorSwatchButton button) {
        String value = button.styleColor();
        return value == null ? null : TextColor.parseColor(value).result().orElse(null);
    }

    /**
     * How a colour is written into the colour box: the dye name when the value is one of the 16
     * dyes, otherwise whatever the style already had - a legacy chat colour name if it was written
     * that way, and {@code #RRGGBB} otherwise.
     *
     * <p>Dye names are a display and input convenience only. They are deliberately never put into
     * the component JSON: {@code TextColor.CODEC} is {@code TextColor.parseColor}, which rejects
     * everything except the 16 legacy chat names and hex, so a dye name there would make the whole
     * component fail to parse - the server would blank the line, and the {@code /data merge}
     * command shown in the preview would be rejected by vanilla.
     */
    private static String colorText(@Nullable TextColor color) {
        if (color == null) {
            return "";
        }
        DyeColor dye = dyeOf(color.getValue() & 0xFFFFFF);
        return dye != null ? dye.getSerializedName() : color.serialize();
    }

    private static @Nullable DyeColor dyeOf(int rgb) {
        for (DyeColor dye : DyeColor.values()) {
            if ((dye.getTextColor() & 0xFFFFFF) == rgb) {
                return dye;
            }
        }
        return null;
    }

    /** How a shadow colour is written in its field: dye name, hex, or blank for "none". */
    private static String shadowText(@Nullable Integer argb) {
        if (argb == null || argb == 0) {
            return ""; // Style.NO_SHADOW, i.e. the same as having no explicit shadow at all
        }
        int rgb = argb & 0xFFFFFF;
        DyeColor dye = dyeOf(rgb);
        if (dye != null && (argb >>> 24) == 0xFF) {
            return dye.getSerializedName();
        }
        return String.format("#%08X", argb);
    }

    private static boolean isNoShadow(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("auto")
                || trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("reset");
    }

    /**
     * Parses a shadow entry into ARGB. {@code null} means "no shadow" or "not understood"; the
     * caller tells those apart with {@link #isNoShadow}. Six digit values get an opaque alpha.
     */
    private static @Nullable Integer parseShadowInput(String value) {
        String trimmed = value.trim();
        if (isNoShadow(trimmed)) {
            return null;
        }
        String hex = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        if (hex.matches("[0-9a-fA-F]{6}")) {
            return 0xFF000000 | (int) Long.parseLong(hex, 16);
        }
        if (hex.matches("[0-9a-fA-F]{8}")) {
            return (int) Long.parseLong(hex, 16);
        }
        DyeColor dye = DyeColor.byName(trimmed.toLowerCase(Locale.ROOT), null);
        return dye == null ? null : 0xFF000000 | (dye.getTextColor() & 0xFFFFFF);
    }

    private static @Nullable Integer argbOrNull(ColorSwatchButton button) {
        int argb = button.argb();
        return argb == 0 ? null : argb;
    }

    private static boolean isAutoColor(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("auto") || trimmed.equalsIgnoreCase("reset");
    }

    /**
     * Parses a hex box entry. Accepts {@code #RRGGBB}, a bare {@code RRGGBB}, the 16 legacy chat
     * colour names and the 16 dye names; {@code null} means "inherit the ink colour" or the entry
     * could not be understood.
     */
    private static @Nullable TextColor parseColorInput(String value) {
        String trimmed = value.trim();
        if (isAutoColor(trimmed)) {
            return null;
        }
        TextColor direct = TextColor.parseColor(trimmed).result().orElse(null);
        if (direct != null) {
            return direct;
        }
        if (trimmed.matches("[0-9a-fA-F]{6}")) {
            return TextColor.parseColor("#" + trimmed).result().orElse(null);
        }
        DyeColor dye = DyeColor.byName(trimmed.toLowerCase(Locale.ROOT), null);
        return dye == null ? null : TextColor.fromRgb(dye.getTextColor() & 0xFFFFFF);
    }

    private Component glowLabel() {
        return Component.translatable(
                this.isGlowing ? "gui.wifi.signgui.glow.state.on" : "gui.wifi.signgui.glow.state.off");
    }

    private Component changeSideLabel() {
        return Component.translatable("gui.wifi.signgui.button.changeside.to", otherSideName());
    }

    /** The side currently being edited; also what the window title reports. */
    private Component sideName() {
        return Component.translatable(ClientState.textIsFront ? "gui.wifi.signgui.front" : "gui.wifi.signgui.back");
    }

    /** The side the change-side button will switch to, which is what its label must name. */
    private Component otherSideName() {
        return Component.translatable(ClientState.textIsFront ? "gui.wifi.signgui.back" : "gui.wifi.signgui.front");
    }

    private static Component tooltipForFormat(int index) {
        return Component.translatable("gui.wifi.signgui.tooltip.fmt." + switch (index) {
            case 0 -> "bold";
            case 1 -> "italic";
            case 2 -> "underline";
            case 3 -> "strike";
            default -> "obfuscated";
        });
    }

    private SignTextSlot slot() {
        return ClientState.textIsFront ? SignTextSlot.FRONT : SignTextSlot.BACK;
    }

    // ------------------------------------------------------------------ model loading

    /** Re-reads the whole side from the sign block entity into the model. */
    private void reloadFromSign() {
        SignText signText = this.sign.getText(slot());
        this.isGlowing = signText.hasGlowingText();
        // Read into the model *before* the widget guard below: on the constructor path no widgets
        // exist yet, and skipping it would leave the ink colour at its default of black.
        this.inkColor = TextColor.fromRgb(signText.getColor().getTextColor() & 0xFFFFFF);
        List<Component> messages = signText.getMessages(false);
        for (int i = 0; i < ROWS; i++) {
            // Raw state is cleared first: keeping a stale raw component would make the reloaded line
            // (or, after a side switch, the other side's line) unreachable.
            this.rawModes[i] = false;
            this.rawValid[i] = false;
            this.rawTexts[i] = "";
            this.rawComponents[i] = Component.empty();
            Component line = i < messages.size() ? messages.get(i) : Component.empty();
            applyComponentToModel(i, line);
            // A line the fields cannot express opens in raw mode, so nothing is lost by looking at
            // it; the row shows why, and the toggle keeps it there until the user says otherwise.
            List<Component> reasons = this.rawReasons.get(i);
            reasons.clear();
            collectReasons(line, Style.EMPTY, null, reasons);
            if (!reasons.isEmpty()) {
                this.rawModes[i] = true;
                this.rawValid[i] = true;
                this.rawComponents[i] = line instanceof MutableComponent mutable
                        ? mutable
                        : Component.literal("").append(line);
                this.rawTexts[i] = componentToSnbt(this.rawComponents[i]);
            }
        }
        if (this.textFields[0] == null) {
            return; // constructor path: init() will build the widgets from what was just loaded
        }
        for (int i = 0; i < ROWS; i++) {
            this.rawFields[i].setValue(this.rawTexts[i]);
            this.nbtToggles[i].toggled(this.rawModes[i]);
            refreshRawTooltip(i);
            pushModelToWidgets(i);
        }
        if (this.focusField == Field.RAW) {
            this.focusField = Field.TEXT;
            setFocused(this.textFields[this.focusRow]);
        }
        this.layout = SignEditorLayout.compute(this.width, this.height, this.scroll, metrics());
        applyVisibility();
        this.glowToggle.setMessage(glowLabel());
        this.glowToggle.toggled(this.isGlowing);
        this.inkSwatch.setFromTextColor(this.inkColor);
        refreshFmtButtons(-1);
        refreshNbt();
    }

    private void changeSide() {
        ClientState.textIsFront = !ClientState.textIsFront;
        hideAllSuggestions();
        reloadFromSign();
        this.changeSideButton.setMessage(changeSideLabel());
        this.changeSideButton.setTooltip(
                Tooltip.create(Component.translatable("gui.wifi.signgui.tooltip.changeside", otherSideName())));
    }

    /** Splits one component into the field-form model: text, command, colour and shadow colour. */
    private void applyComponentToModel(int row, Component line) {
        MutableComponent mutable = line instanceof MutableComponent existing
                ? existing
                : Component.literal("").append(line);
        Style style = mutable.getStyle();
        this.texts[row] = lineToEditText(mutable);
        this.formats[row][FMT_BOLD] = style.isBold();
        this.formats[row][FMT_ITALIC] = style.isItalic();
        this.formats[row][FMT_UNDERLINE] = style.isUnderlined();
        this.formats[row][FMT_STRIKE] = style.isStrikethrough();
        this.formats[row][FMT_OBFUSCATED] = style.isObfuscated();
        this.commands[row] = extractCommand(mutable);
        this.colors[row] = style.getColor();
        this.shadowColors[row] = style.getShadowColor();
    }

    /** Pushes the model back into the widgets of one row, without firing the model write-back. */
    private void pushModelToWidgets(int row) {
        this.textFields[row].setValue(this.texts[row]);
        this.commandFields[row].setValue(this.commands[row]);
        this.colorButtons[row].setFromTextColor(this.colors[row]);
        this.hexFields[row].setValue(colorText(this.colors[row]));
        this.shadowSwatches[row].setFromArgb(this.shadowColors[row] == null ? 0 : this.shadowColors[row]);
        this.shadowHexFields[row].setValue(shadowText(this.shadowColors[row]));
        refreshFmtButtons(row);
    }

    /** Switches a row between the field form and raw component editing.
     *
     * <p>Entering the mode serialises what the fields currently mean; leaving it parses the raw text
     * back into the fields, so the two views stay consistent and there is only ever one source of
     * truth. Raw text that does not parse is kept in the box, flagged red, and simply not applied.
     */
    private void toggleRaw(int row) {
        boolean raw = !this.rawModes[row];
        if (raw) {
            MutableComponent current = buildLineComponent(row);
            this.rawComponents[row] = current;
            this.rawTexts[row] = componentToSnbt(current);
            this.rawFields[row].setValue(this.rawTexts[row]);
            this.rawValid[row] = true;
        } else {
            MutableComponent parsed = parseRaw(this.rawTexts[row]);
            if (parsed == null) {
                // Unparseable: stay in the mode, the red outline is the only thing worth showing.
                return;
            }
            // Leaving raw mode reduces the line to what the fields hold. That is not announced: the
            // warning lives on the row (the marker and the toggle's tooltip) for as long as it applies.
            this.rawComponents[row] = parsed;
            applyComponentToModel(row, parsed);
            pushModelToWidgets(row);
        }
        this.rawModes[row] = raw;
        this.nbtToggles[row].toggled(raw);
        this.focusRow = row;
        this.focusField = raw ? Field.RAW : Field.TEXT;
        setFocused(raw ? this.rawFields[row] : this.textFields[row]);
        refreshNbtIfPreviewing();
        applyVisibility();
    }

    /**
     * Parses raw component NBT, i.e. SNBT such as {@code {text:"hi"}} or the list form
     * {@code [{color:"aqua",text:"2"}]}. The root may be any tag, because the component codec
     * accepts a compound and a list alike; only the compound-only parser would reject the list.
     */
    private static @Nullable MutableComponent parseRaw(String snbt) {
        String trimmed = snbt.trim();
        if (trimmed.isEmpty()) {
            return Component.empty();
        }
        try {
            Component parsed = ComponentSerialization.CODEC
                    .parse(NbtOps.INSTANCE, SNBT_PARSER.parseFully(trimmed))
                    .result()
                    .orElse(null);
            if (parsed == null) {
                return null;
            }
            return parsed instanceof MutableComponent mutable ? mutable : Component.literal("").append(parsed);
        } catch (Exception e) {
            return null; // includes CommandSyntaxException from malformed SNBT
        }
    }

    /** Collects every reason the fields cannot hold this component, without duplicates. */
    private static void collectReasons(Component component, Style inherited, @Nullable Style lineStyle,
            List<Component> out) {
        Style style = component.getStyle().applyTo(inherited);
        boolean root = lineStyle == null;
        Style line = root ? style : lineStyle;
        if (!(component.getContents() instanceof PlainTextContents)) {
            addReason(out, Component.translatable("msg.signgui.raw_reason.content", contentsName(component)));
        }
        if (style.getHoverEvent() != null) {
            addReason(out, Component.translatable("msg.signgui.raw_reason.hover"));
        }
        if (style.getInsertion() != null) {
            addReason(out, Component.translatable("msg.signgui.raw_reason.insertion"));
        }
        if (!style.getFont().equals(FontDescription.DEFAULT)) {
            addReason(out, Component.translatable("msg.signgui.raw_reason.font"));
        }
        if (!root) {
            TextColor color = style.getColor();
            if (color != null && legacyCodeOf(color) == null) {
                // The inline form only knows the sixteen legacy colours, so a custom hex run cannot be
                // written out at all. A hex colour on the line itself is fine: the colour field has it.
                addReason(out, Component.translatable("msg.signgui.raw_reason.hexrun"));
            }
            if (style.getShadowColor() != null) {
                addReason(out, Component.translatable("msg.signgui.raw_reason.shadowrun"));
            }
            // A colour code is the only thing that switches formats off, so a run that drops one the
            // line sets is spellable only if it has a colour of its own to write with.
            if (dropsAnyFormat(line, style) && legacyCodeOf(style.getColor()) == null) {
                addReason(out, Component.translatable("msg.signgui.raw_reason.formats"));
            }
        }
        ClickEvent click = style.getClickEvent();
        if (click != null && (!root || !(click instanceof ClickEvent.RunCommand))) {
            addReason(out, Component.translatable("msg.signgui.raw_reason.click"));
        }
        for (Component sibling : component.getSiblings()) {
            collectReasons(sibling, style, line, out);
        }
    }

    /** One entry per kind of problem: a row with three unstyled runs has one problem, not three. */
    private static void addReason(List<Component> out, Component reason) {
        String key = reason.getString();
        for (Component existing : out) {
            if (existing.getString().equals(key)) {
                return;
            }
        }
        out.add(reason);
    }

    /**
     * The toggle's tooltip: the plain description when the line is expressible, otherwise a titled
     * list of what the fields would drop, so it reads as a warning rather than a paragraph.
     */
    private void refreshRawTooltip(int row) {
        List<Component> reasons = this.rawReasons.get(row);
        Component tooltip;
        if (reasons.isEmpty()) {
            tooltip = Component.translatable("gui.wifi.signgui.tooltip.raw");
        } else {
            MutableComponent built = Component.translatable("msg.signgui.raw_reason.title").copy();
            built.append(Component.literal("\n"));
            built.append(Component.translatable("msg.signgui.raw_reason.intro"));
            for (Component reason : reasons) {
                built.append(Component.literal("\n"));
                built.append(Component.translatable("msg.signgui.raw_reason.bullet"));
                built.append(reason);
            }
            tooltip = built;
        }
        Tooltip tip = Tooltip.create(tooltip);
        this.nbtToggles[row].setTooltip(tip);
        if (this.rawFields[row] != null) {
            // On the editor too: that is where the text is typed, so the warning is under the cursor.
            this.rawFields[row].setTooltip(tip);
        }
    }

    private static String contentsName(Component component) {
        return component.getContents().getClass().getSimpleName().replace("Contents", "");
    }

    /** Re-prints a row's raw text through its parsed tag: pretty or one line. */
    private void reprintRaw(int row, boolean pretty) {
        MutableComponent parsed = parseRaw(this.rawTexts[row]);
        if (parsed == null) {
            return; // nothing to re-print: the text does not parse, and it stays as the user left it
        }
        String reprinted = pretty
                ? new SnbtPrinterTagVisitor().visit(componentToTag(parsed))
                : componentToSnbt(parsed);
        this.rawTexts[row] = reprinted;
        this.rawFields[row].setValue(reprinted);
        this.rawValid[row] = true;
        this.rawComponents[row] = parsed;
        refreshConfirmState();
        refreshNbtIfPreviewing();
    }

    private static Tag componentToTag(Component component) {
        return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(new CompoundTag());
    }

    private static String componentToSnbt(Component component) {
        return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component)
                .result()
                .map(Object::toString)
                .orElse("{}");
    }

    private static String lineToEditText(MutableComponent line) {
        Style fieldStyle = line.getStyle();
        StringBuilder sb = new StringBuilder();
        // No format prefix: the root's formats are held by the toggles, not spelled out in the text.
        // The text starts out already in the field style, so only later runs need inline codes.
        appendEditText(sb, line, Style.EMPTY, fieldStyle, fieldStyle);
        return sb.toString();
    }

    /**
     * Writes one component and its children as editor text, flattening later runs' styles into inline
     * {@code &x} codes. A line can arrive as a list of styled runs, and the list form makes the first
     * element the parent of the rest, so a run inherits whatever the runs before it set.
     *
     * <p>Only the difference from the style currently in effect is written: a run that merely changes
     * the colour of a loaded line produces a bare colour code, not a reset followed by everything
     * again. Compare {@code [{gold,1},{green,2}]}, which should read {@code 1&a2}.
     *
     * @param inherited the style of the enclosing component
     * @param emitted   the style the text written so far leaves in effect
     * @return the style in effect after writing this component
     */
    private static Style appendEditText(StringBuilder out, Component component, Style inherited, Style emitted,
            Style fieldStyle) {
        // applyTo keeps the receiver's fields and falls back to the argument's: the child must be the
        // receiver, otherwise a run's own colour would be overridden by its parent's.
        Style effective = component.getStyle().applyTo(inherited);
        Style current = emitCodes(out, emitted, effective, fieldStyle);
        out.append(SignTextCodec.escape(ownText(component)));
        for (Component sibling : component.getSiblings()) {
            current = appendEditText(out, sibling, effective, current, fieldStyle);
        }
        return current;
    }

    /**
     * Writes the codes that carry the text from {@code current} to {@code target}, returning the
     * style that is then in effect.
     *
     * <p>Two rules of the game, both from {@code Style.applyLegacyFormat}: a format code only ever
     * switches a format on, while a colour code switches all of them off and sets the colour, and a
     * reset restores the field style. So a colour code doubles as the way to drop a format - which is
     * why it is written even when the colour itself does not change - and everything the target keeps
     * has to be written again after it.
     */
    private static Style emitCodes(StringBuilder out, Style current, Style target, Style fieldStyle) {
        String colourCode = legacyCodeOf(target.getColor());
        boolean canWriteColour = target.getColor() != null && colourCode != null;
        Style base = current;

        // There is no code that removes a colour, only a reset, which also restores the field formats.
        if (target.getColor() == null && current.getColor() != null) {
            out.append("&r");
            base = fieldStyle;
        }

        boolean dropsFormat = dropsAnyFormat(base, target);
        boolean colourDiffers = target.getColor() != null && !target.getColor().equals(base.getColor());
        boolean cleared = false;
        if (canWriteColour && (colourDiffers || dropsFormat)) {
            out.append('&').append(colourCode);
            cleared = true;
            base = base.withColor(target.getColor());
        } else if (dropsFormat) {
            // No colour to write with. The reason check keeps such a line in raw mode, so this is a
            // safety net rather than a normal path: reset and accept the field's formats.
            out.append("&r");
            base = fieldStyle;
        }

        if (target.isObfuscated() && (cleared || !base.isObfuscated())) {
            out.append("&k");
        }
        if (target.isStrikethrough() && (cleared || !base.isStrikethrough())) {
            out.append("&m");
        }
        if (target.isUnderlined() && (cleared || !base.isUnderlined())) {
            out.append("&n");
        }
        if (target.isItalic() && (cleared || !base.isItalic())) {
            out.append("&o");
        }
        if (target.isBold() && (cleared || !base.isBold())) {
            out.append("&l");
        }
        // A colour with no inline spelling leaves the text in whatever colour was already in effect;
        // the reason check normally keeps that out of the fields entirely.
        return target.getColor() != null && !canWriteColour ? target.withColor(base.getColor()) : target;
    }

    /** Whether any format is on in {@code from} and off in {@code to}. */
    private static boolean dropsAnyFormat(Style from, Style to) {
        return (from.isBold() && !to.isBold())
                || (from.isItalic() && !to.isItalic())
                || (from.isUnderlined() && !to.isUnderlined())
                || (from.isStrikethrough() && !to.isStrikethrough())
                || (from.isObfuscated() && !to.isObfuscated());
    }

    private static String ownText(Component component) {
        return component.getContents() instanceof PlainTextContents plain
                ? plain.text()
                : component.getString();
    }

    /**
     * The legacy colour code letter for a colour, or {@code null} when it has none. Uses only the
     * public mapping helpers: {@code ChatFormatting} here exposes the code to formatting direction
     * and {@code TextColor.fromLegacyFormat} the other, which is enough to match by value.
     */
    private static @Nullable String legacyCodeOf(@Nullable TextColor color) {
        if (color == null) {
            return null;
        }
        for (int i = 0; i < LEGACY_COLOR_CODES.length(); i++) {
            char code = LEGACY_COLOR_CODES.charAt(i);
            ChatFormatting formatting = ChatFormatting.getByCode(code);
            TextColor legacy = formatting == null ? null : TextColor.fromLegacyFormat(formatting);
            if (legacy != null && legacy.getValue() == color.getValue()) {
                return String.valueOf(code);
            }
        }
        return null;
    }

    private static String extractCommand(MutableComponent line) {
        ClickEvent click = line.getStyle().getClickEvent();
        if (click instanceof ClickEvent.RunCommand run) {
            return run.command();
        }
        return "";
    }

    /**
     * Flips one format of a line. The flag lives in the model and ends up in the component's style;
     * the text box is left alone, so the two never have to be kept in step by hand.
     */
    private void toggleFormat(int row, int index) {
        this.formats[row][index] = !this.formats[row][index];
        refreshFmtButtons(row);
        refreshNbtIfPreviewing();
    }

    /** Pushes the model's format flags into the toggles of {@code row}, or of every row when negative. */
    private void refreshFmtButtons(int row) {
        for (int i = 0; i < ROWS; i++) {
            if (row >= 0 && i != row) {
                continue;
            }
            if (this.fmtButtons[i][0] == null) {
                continue;
            }
            for (int k = 0; k < FMT_CODES.length; k++) {
                this.fmtButtons[i][k].toggled(this.formats[i][k]);
            }
        }
    }

    // ------------------------------------------------------------------ tabs

    private void switchTab(Tab tab) {
        if (this.currentTab == tab) {
            return;
        }
        this.currentTab = tab;
        hideAllSuggestions();
        clearFocus();
        this.lastFocused = null;
        if (tab == Tab.NBT_PREVIEW) {
            refreshNbt();
        } else {
            this.restoreFocusPending = true;
        }
        applyVisibility();
    }

    public void hideAllSuggestions() {
        for (MyEditBox command : this.commandFields) {
            if (command != null) {
                command.hideCommandSuggestions();
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(graphics);
        graphics.fillGradient(0, 0, this.width, this.height, 0x80000000, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.restoreFocusPending) {
            this.restoreFocusPending = false;
            GuiEventListener target = widgetFor(this.focusRow, this.focusField);
            if (target instanceof AbstractWidget widget && widget.active) {
                setFocused(target);
            }
        }
        syncFocus();

        graphics.centeredText(this.font, title(), this.width / 2, this.layout.titleY, SignEditorTheme.TEXT);
        if (this.layout.tipLines > 0) {
            drawCenteredClipped(graphics, Component.translatable("gui.wifi.signgui.tip_line1"),
                    this.layout.tipY1, SignEditorTheme.TEXT_SECONDARY);
            if (this.layout.tipLines > 1) {
                drawCenteredClipped(graphics, Component.translatable("gui.wifi.signgui.tip_line2"),
                        this.layout.tipY2, SignEditorTheme.TEXT_MUTED);
            }
        }
        drawFooter(graphics);
        if (this.currentTab == Tab.EDIT) {
            drawRows(graphics, mouseX, mouseY);
            drawScrollbar(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (this.currentTab == Tab.EDIT) {
            for (int i = 0; i < ROWS; i++) {
                if (this.rawModes[i] && !this.rawValid[i] && this.layout.rowVisible(i)) {
                    Rect rect = this.layout.rows.get(i).rawText();
                    graphics.outline(rect.x() - 1, rect.y() - 1, rect.w() + 2, rect.h() + 2, 0xFFFF5555);
                }
            }
            for (MyEditBox command : this.commandFields) {
                // isActive() is false for a field that scrolled out of view, and keepFocusVisible
                // normally moves focus away from it; this guard covers the frame in between.
                if (command.isFocused() && command.isActive()) {
                    command.renderSuggestions(graphics, mouseX, mouseY, partialTick);
                }
            }
        }
    }

    private Component title() {
        return Component.translatable("gui.wifi.signgui.title.plain")
                .withStyle(ChatFormatting.BOLD)
                .append("  ")
                .append(sideName().copy().withStyle(Style.EMPTY.withColor(SignEditorTheme.ACCENT_HOVER)));
    }

    /**
     * Centred text truncated to the window. A translated hint line can easily be wider than a narrow
     * window, and nothing else clips centred text.
     */
    private void drawCenteredClipped(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        String label = this.font.plainSubstrByWidth(text.getString(), Math.max(0, this.width - 8));
        graphics.centeredText(this.font, label, this.width / 2, y, color);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < ROWS; i++) {
            if (!this.layout.rowVisible(i)) {
                continue;
            }
            RowLayout row = this.layout.rows.get(i);
            Rect card = row.card();
            boolean focused = hasFocus(i);
            boolean hovered = card.contains(mouseX, mouseY);

            int background = focused ? SignEditorTheme.CARD_BG_FOCUS
                    : (hovered ? SignEditorTheme.CARD_BG_HOVER : SignEditorTheme.CARD_BG);
            graphics.fill(card.x(), card.y(), card.right(), card.bottom(), background);
            graphics.outline(card.x(), card.y(), card.w(), card.h(),
                    focused ? SignEditorTheme.ACCENT_BORDER : SignEditorTheme.CARD_BORDER);
            if (focused) {
                graphics.fill(card.x(), card.y(), card.x() + 2, card.bottom(), SignEditorTheme.ACCENT);
            } else if (hovered) {
                graphics.fill(card.x(), card.y(), card.x() + 2, card.bottom(), SignEditorTheme.ACCENT_DIM);
            }

            Rect label = row.label();
            boolean warned = this.rawModes[i] && !this.rawReasons.get(i).isEmpty();
            graphics.centeredText(this.font, warned ? "!" : String.valueOf(i + 1), label.centerX(),
                    label.y() + (label.h() - 8) / 2,
                    warned ? 0xFFFF5555
                            : (focused ? SignEditorTheme.ACCENT_HOVER : SignEditorTheme.TEXT_MUTED));
            Rect labelCmd = row.labelCmd();
            graphics.centeredText(this.font, "\u00bb", labelCmd.centerX(), labelCmd.y() + (labelCmd.h() - 8) / 2,
                    SignEditorTheme.TEXT_MUTED);
            if (!this.rawModes[i]) {
                drawCaption(graphics, "gui.wifi.signgui.color.label", row.colorLabel());
                drawCaption(graphics, "gui.wifi.signgui.shadow.label", row.shadowLabel());
            }
        }
    }

    private boolean hasFocus(int row) {
        return this.textFields[row].isFocused() || this.colorButtons[row].isFocused()
                || this.commandFields[row].isFocused() || this.hexFields[row].isFocused()
                || this.shadowSwatches[row].isFocused() || this.shadowHexFields[row].isFocused()
                || this.rawFields[row].isFocused();
    }

    private void drawFooter(GuiGraphicsExtractor graphics) {
        Rect footer = this.layout.footer;
        graphics.fill(footer.x(), footer.y(), footer.right(), footer.bottom(), SignEditorTheme.FOOTER_BG);
        graphics.outline(footer.x(), footer.y(), footer.w(), footer.h(), SignEditorTheme.FOOTER_BORDER);
        if (this.currentTab == Tab.EDIT) {
            drawCaption(graphics, "gui.wifi.signgui.glow.label", this.layout.glowLabel);
            drawCaption(graphics, "gui.wifi.signgui.inkcolor.label", this.layout.inkLabel);
        }
    }

    /** Captions are clipped to the width the layout reserved, so they cannot collide with widgets. */
    private void drawCaption(GuiGraphicsExtractor graphics, String key, Rect rect) {
        String caption = this.font.plainSubstrByWidth(Component.translatable(key).getString(), rect.w());
        graphics.text(this.font, caption, rect.x(), rect.y() + (rect.h() - 8) / 2, SignEditorTheme.TEXT_SECONDARY);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.layout.scrollable()) {
            return;
        }
        Rect track = this.layout.scrollTrack;
        Rect thumb = this.layout.scrollThumb;
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), SignEditorTheme.SB_TRACK);
        boolean hot = this.draggingScrollbar || track.contains(mouseX, mouseY);
        graphics.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(),
                hot ? SignEditorTheme.SB_THUMB_HOVER : SignEditorTheme.SB_THUMB);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.currentTab == Tab.EDIT && click.button() == 1) {
            // The popup paints over the footer, so it also gets first refusal on clicks there.
            for (MyEditBox command : this.commandFields) {
                if (command.isFocused() && command.handleSuggestionMouseClicked(click, doubled)) {
                    return true;
                }
            }
            if (this.layout.scrollable() && this.layout.scrollTrack.contains((int) click.x(), (int) click.y())) {
                return beginScrollbarDrag(click.y());
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean beginScrollbarDrag(double mouseY) {
        Rect track = this.layout.scrollTrack;
        Rect thumb = this.layout.scrollThumb;
        if (track.h() - thumb.h() <= 0) {
            return false;
        }
        boolean onThumb = thumb.contains(track.x(), (int) mouseY);
        this.scrollGrabDy = onThumb ? (int) mouseY - thumb.y() : thumb.h() / 2;
        this.draggingScrollbar = true;
        if (!onThumb) {
            dragScrollbarTo(mouseY);
        }
        return true;
    }

    private void dragScrollbarTo(double mouseY) {
        Rect track = this.layout.scrollTrack;
        Rect thumb = this.layout.scrollThumb;
        int travel = track.h() - thumb.h();
        if (travel <= 0 || this.layout.maxScroll <= 0) {
            return;
        }
        int target = (int) Math.round(
                (mouseY - this.scrollGrabDy - track.y()) * (double) this.layout.maxScroll / travel);
        setScroll(target);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            dragScrollbarTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.currentTab == Tab.EDIT) {
            for (MyEditBox command : this.commandFields) {
                if (command.isFocused() && command.isActive()
                        && command.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    return true;
                }
            }
            if (scrollY != 0 && this.layout.scrollable()) {
                setScroll(this.scroll + (scrollY > 0 ? -1 : 1) * this.layout.rowH);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Screen closes on Escape before the focused widget is consulted, so the command field gets
        // first refusal here: one Escape dismisses the suggestion list, a second closes the editor.
        if (event.isEscape()) {
            for (MyEditBox command : this.commandFields) {
                if (command.isFocused() && command.isSuggestionVisible() && command.keyPressed(event)) {
                    return true;
                }
            }
        }
        // Ctrl + B/I/U/T/K toggles that format on the line being edited. The press is consumed even
        // when it cannot do anything here - wrong tab, raw row, nothing focused - because letting it
        // through hands it to whatever else is bound to it.
        if (event.hasControlDown()) {
            for (int k = 0; k < FORMAT_SHORTCUTS.length; k++) {
                if (event.input() == FORMAT_SHORTCUTS[k]) {
                    if (this.currentTab == Tab.EDIT && hasFocus(this.focusRow) && !this.rawModes[this.focusRow]) {
                        toggleFormat(this.focusRow, k);
                    }
                    return true;
                }
            }
        }
        // Page keys only: Home and End belong to the text caret, so they are left to the widget.
        if (this.currentTab == Tab.EDIT && this.layout.scrollable()) {
            int input = event.input();
            if (input == InputConstants.KEY_PAGEUP || input == InputConstants.KEY_PAGEDOWN) {
                int step = this.layout.rowsVisible * this.layout.rowH;
                setScroll(this.scroll + (input == InputConstants.KEY_PAGEUP ? -step : step));
                return true;
            }
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (event.isConfirmation()) {
            return focusNextField();
        }
        return false;
    }

    /**
     * Enter walks text to colour to command and then on to the next line. Tab is deliberately left
     * alone: {@code Screen} already moves focus through the widget list in registration order, which
     * here is row by row.
     */
    private boolean focusNextField() {
        Field[] order = Field.values();
        int row = this.focusRow;
        int field = this.focusField.ordinal();
        for (int step = 0; step < ROWS * order.length; step++) {
            field++;
            if (field >= order.length) {
                field = 0;
                row = (row + 1) % ROWS;
            }
            GuiEventListener target = widgetFor(row, order[field]);
            if (target instanceof AbstractWidget widget && widget.active) {
                this.focusRow = row;
                this.focusField = order[field];
                setFocused(target);
                if (order[field] == Field.CMD) {
                    this.commandFields[row].activeCommandSuggestions();
                }
                return true;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ sign validity

    /**
     * Turns the hardcoded Ctrl+B narrator hotkey off for as long as this screen is open.
     *
     * <p>That hotkey is not a key binding, so no screen can consume it: {@code KeyboardHandler} checks
     * it before the screen is ever given the key, and the only thing that suppresses it is an
     * {@code EditBox} with consumable input being focused. The text and command fields are EditBoxes
     * and already suppress it, but the raw component editor is a MultiLineEditBox and the buttons and
     * swatches are not EditBoxes at all, so Ctrl+B would reach the narrator from those.
     *
     * <p>Disabling the option is the one lever a screen has. Its value is put back on close and is
     * never written to disk from here, so the player's setting is unchanged.
     */
    private void holdNarratorHotkey() {
        if (this.narratorHotkey == null) {
            OptionInstance<Boolean> option = this.minecraft.options.narratorHotkey();
            this.narratorHotkey = option.get();
            option.set(false);
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.narratorHotkey != null) {
            this.minecraft.options.narratorHotkey().set(this.narratorHotkey);
            this.narratorHotkey = null;
        }
    }

    @Override
    public void tick() {
        // The same guard vanilla uses: a sign that is gone, or out of reach, cannot be edited.
        if (this.minecraft.player == null || this.sign.isRemoved()
                || this.sign.playerIsTooFarAwayToEdit(this.minecraft.player.getUUID())) {
            this.onClose();
        }
    }

    // ------------------------------------------------------------------ commands

    private void refreshNbt() {
        if (this.nbtPreview != null) {
            this.nbtPreview.setValue(buildDataCmd());
        }
    }

    private void refreshNbtIfPreviewing() {
        if (this.currentTab == Tab.NBT_PREVIEW) {
            refreshNbt();
        }
    }

    /** The command to copy or run: exactly what the preview box holds, folded onto one line. */
    private String commandFromPreview() {
        String text = this.nbtPreview == null ? buildDataCmd() : this.nbtPreview.getValue();
        return SnbtFormat.oneLine(text.trim());
    }

    private void copyCommand() {
        Minecraft.getInstance().keyboardHandler.setClipboard(commandFromPreview());
    }

    private void executeCommand() {
        String command = commandFromPreview();
        String toSend = command.startsWith("/") ? command.substring(1) : command;
        if (Minecraft.getInstance().player != null && !toSend.isBlank()) {
            Minecraft.getInstance().player.connection.sendCommand(toSend);
        }
        this.onClose();
    }

    private void doConfirm() {
        List<Component> lines = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) {
            lines.add(buildLineComponent(i));
        }
        ClientPlatformHelper.sendToServer(new SignEditUpdateBlockPayload(
                this.sign.getBlockPos(), lines, ClientState.textIsFront, this.isGlowing,
                this.inkSwatch.inkColor()));
        this.onClose();
    }

    /**
     * The line as it is actually sent. Both the payload and the NBT preview come from here, so what
     * the preview shows is by construction what the server receives.
     */
    private MutableComponent buildLineComponent(int row) {
        if (this.rawModes[row]) {
            // The last component the raw text parsed to, so a half-typed edit never blanks the line.
            return this.rawComponents[row];
        }
        return legacyTextToComponent(SignTextCodec.unescape(this.texts[row]), fieldStyle(row));
    }

    /** The style the fields describe: the format toggles, plus colour, shadow and command. */
    private Style fieldStyle(int row) {
        Style style = Style.EMPTY;
        if (this.formats[row][FMT_BOLD]) {
            style = style.withBold(true);
        }
        if (this.formats[row][FMT_ITALIC]) {
            style = style.withItalic(true);
        }
        if (this.formats[row][FMT_UNDERLINE]) {
            style = style.withUnderlined(true);
        }
        if (this.formats[row][FMT_STRIKE]) {
            style = style.withStrikethrough(true);
        }
        if (this.formats[row][FMT_OBFUSCATED]) {
            style = style.withObfuscated(true);
        }
        if (this.colors[row] != null) {
            style = style.withColor(this.colors[row]);
        }
        if (this.shadowColors[row] != null) {
            style = style.withShadowColor(this.shadowColors[row]);
        }
        String command = this.commands[row];
        if (command != null && !command.isBlank()) {
            style = style.withClickEvent(new ClickEvent.RunCommand(command));
        }
        return style;
    }

    /**
     * Turns editor text into a component, converting inline section codes into real style fields.
     *
     * <p>Left alone, "a§cb" would become one literal whose formatting only exists as text and is
     * interpreted while rendering. Splitting it into runs produces
     * {@code {text:"a",extra:[{text:"b",color:"red"}]}} instead, which is what makes the NBT view
     * read and edit like ordinary component data. A reset returns to the field style, which is how
     * the renderer treats a reset found inside component text.
     */
    private static MutableComponent legacyTextToComponent(String text, Style fieldStyle) {
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Style style = fieldStyle;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ChatFormatting.PREFIX_CODE) {
                current.append(c);
                continue;
            }
            if (i + 1 >= text.length()) {
                break; // a trailing section sign is dropped, as the renderer does
            }
            ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(++i));
            if (formatting == null) {
                continue; // unknown code: the renderer ignores it too
            }
            Style next = formatting == ChatFormatting.RESET ? fieldStyle : style.applyLegacyFormat(formatting);
            if (!next.equals(style)) {
                // Only split when the style actually changes, so a run of codes stays one run.
                parts.add(current.toString());
                styles.add(style);
                current.setLength(0);
                style = next;
            }
        }
        parts.add(current.toString());
        styles.add(style);

        // A leading empty run would put the real text in an extra for no reason.
        while (parts.size() > 1 && parts.get(0).isEmpty()) {
            parts.remove(0);
            styles.remove(0);
        }
        MutableComponent root = Component.literal(parts.get(0)).setStyle(styles.get(0));
        for (int i = 1; i < parts.size(); i++) {
            if (!parts.get(i).isEmpty()) {
                root.append(Component.literal(parts.get(i)).setStyle(styles.get(i)));
            }
        }
        return root;
    }

    /** Same component, written as JSON for the {@code /data} preview. */
    private String buildLineJson(int row) {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, buildLineComponent(row))
                .result()
                .map(Object::toString)
                .orElse("{\"text\":\"\"}");
    }

    private String buildDataCmd() {
        BlockPos pos = this.sign.getBlockPos();
        String side = ClientState.textIsFront ? "front_text" : "back_text";
        StringBuilder sb = new StringBuilder("/data merge block ")
                .append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ())
                .append(" {").append(side).append(":{messages:[");
        for (int i = 0; i < ROWS; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(buildLineJson(i).replace("\u00a7", "\\u00a7"));
        }
        sb.append("],color:\"").append(this.inkSwatch.inkColor().getSerializedName()).append('"');
        sb.append(",has_glowing_text:").append(this.isGlowing ? "1b" : "0b").append("}}");
        return sb.toString();
    }
}
