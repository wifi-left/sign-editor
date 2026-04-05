package io.wifi.signgui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.serialization.JsonOps;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public class SignEditorScreen extends Screen {

    // ---- Tab system ----
    private enum Tab {
        EDIT, NBT_PREVIEW
    }

    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("^(&[loknm])*");

    // Row panel colors
    private static final int ROW_BG_COLOR = 0x28FFFFFF; // subtle white panel
    private static final int ROW_BG_HOVER = 0x38FFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int LABEL_HL_COLOR = 0xFFFFFFFF;

    private Tab currentTab = Tab.EDIT;

    // ---- Edit-tab widgets ----
    private final EditBox[] textFields = new EditBox[4];
    private final EditBox[] colorFields = new EditBox[4];
    private final MyEditBox[] commandField = new MyEditBox[4];
    private final Button[] boldButtons = new Button[4];
    private final Button[] italicButtons = new Button[4];
    private final Button[] underlineButtons = new Button[4];
    private final Button[] strikeButtons = new Button[4];

    // ---- Glow controls ----
    private boolean isGlowing;
    private Button glowToggleButton;
    private EditBox glowColorField;

    // ---- Tab buttons ----
    private Button tabEditButton;
    private Button tabNbtButton;

    // ---- NBT-preview widgets ----
    private Button copyNbtCmdButton;
    private Button executeNbtCmdButton;
    private MultiLineEditBox nbtPreviewBox;

    // ---- NBT preview state ----
    private final List<String> nbtLines = new ArrayList<>();

    // ---- Scroll state for Edit tab ----
    private int editScrollOffset = 0;
    private int maxEditScrollOffset = 0;
    private static final int SCROLL_BAR_WIDTH = 5;
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartOffset = 0;

    // ---- Action buttons ----
    private Button confirmButton;
    private Button cancelButton;
    private Button changeSideButton;
    private Button reloadButton;

    // ---- Layout vars ----
    private int fieldHeight = 16;
    private int lineHeight = 42;
    private int titleTop = 18;
    private int tipTop = 30;
    private int fieldStartPos = 56;
    private int textLabelOffsetY; // label Y offset inside row for text field row
    private int cmdLabelOffsetY; // label Y offset inside row for cmd field row
    private int glowRowTop;
    private int actionButtonTop;
    private int editContentHeight;
    private int editViewportHeight;
    private int editViewportTop;
    private static final int TAB_BTN_Y = 2;
    private static final int TAB_BTN_H = 16;

    // ---- Content geometry (set once in init, reused in render) ----
    private int contentX; // left edge of the field area
    private int contentW; // total width of the field group
    private int textFieldW;
    private int colorFieldW = 42;
    private int cmdFieldW;
    private int colorFieldX;
    private int btnBaseX;

    private Component titleDisplayer;
    private final SignBlockEntity sign;

    // ---------------------------------------------------------------

    public SignEditorScreen(SignBlockEntity sign) {
        super(Component.translatable("gui.wifi.signgui.title",
                Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back"))));
        this.sign = sign;
        this.titleDisplayer = super.title;
    }

    // ---- Layout helpers ----

    private void calcPositions() {
        boolean compact = this.height <= 380;
        if (compact) {
            titleTop = 22;
            tipTop = 36;
            fieldHeight = 16;
            lineHeight = 42;
            fieldStartPos = 64;
        } else {
            titleTop = 22;
            tipTop = 36;
            fieldHeight = 18;
            lineHeight = 50;
            fieldStartPos = 64;
        }
        // Label offsets (vertically centered inside their row)
        textLabelOffsetY = (fieldHeight - 8) / 2;
        cmdLabelOffsetY = lineHeight / 2 + (fieldHeight - 8) / 2;

        glowRowTop = fieldStartPos + 4 * lineHeight + 6;
        actionButtonTop = glowRowTop + 26;

        editViewportTop = TAB_BTN_Y + TAB_BTN_H + 8;
        editViewportHeight = this.height - editViewportTop - 8;
        editContentHeight = actionButtonTop + 28 - editViewportTop;
        maxEditScrollOffset = Math.max(0, editContentHeight - editViewportHeight);

        // Content geometry
        // Labels are drawn as short "1." "2." etc. to the left of the field group.
        // Field group is centered on screen.
        int labelW = 20; // space for "1." label
        int btnGroupW = 2 * 15; // B I U S (14px each + 1 gap)
        // Total: labelW + textFieldW + 2 + colorFieldW + 2 + btnGroupW
        // We want the whole group to be centered.
        int totalContentW = Math.min(380, this.width - 40);
        textFieldW = totalContentW - labelW - 2 - colorFieldW - 2 - btnGroupW;
        cmdFieldW = textFieldW + 2 + colorFieldW; // cmd spans text+color columns, no B/I/U/S

        contentX = (this.width - totalContentW) / 2;
        contentW = totalContentW;
        colorFieldX = contentX + labelW + textFieldW + 2;
        btnBaseX = colorFieldX + colorFieldW + 2;
    }

    // ---- init ----

    private void onEdited(String value, int idx) {
        this.commandField[idx].updateCommandInfo();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        calcPositions();
        // Tab buttons — positioned to bracket the center
        tabEditButton = Button
                .builder(Component.translatable("gui.wifi.signgui.tab.edit"), btn -> switchTab(Tab.EDIT))
                .pos(this.width / 2 - 101, TAB_BTN_Y).size(100, TAB_BTN_H).build();
        tabNbtButton = Button
                .builder(Component.translatable("gui.wifi.signgui.tab.nbt"), btn -> switchTab(Tab.NBT_PREVIEW))
                .pos(this.width / 2 + 1, TAB_BTN_Y).size(100, TAB_BTN_H).build();
        this.addRenderableWidget(tabEditButton);
        this.addRenderableWidget(tabNbtButton);

        // Line fields
        SignText signText = sign.getText(ClientState.textIsFront);
        isGlowing = signText.hasGlowingText();
        DyeColor inkDye = signText.getColor();
        String inkColorName = inkDye != null ? inkDye.getSerializedName() : "black";

        int fieldX = contentX + 20; // skip label column (20px)

        for (int i = 0; i < 4; ++i) {
            MutableComponent line = (MutableComponent) signText.getMessage(i, false);
            String text = lineToEditText(line);
            String command = extractCommand(line);
            String color = extractColorStr(line);

            final int li = i;
            int ty = fieldStartPos + i * lineHeight;
            int cy = fieldStartPos + lineHeight / 2 + i * lineHeight;

            // Text field
            EditBox tf = new EditBox(this.font, fieldX, ty, textFieldW, fieldHeight,
                    Component.translatable("gui.wifi.signgui.signtext"));
            tf.setMaxLength(384);
            tf.setValue(text);
            tf.setTextColor(0xFFFFFFFF);
            tf.setBordered(true);

            // Color field
            EditBox cf = new EditBox(this.font, colorFieldX, ty, colorFieldW, fieldHeight,
                    Component.literal("#"));
            cf.setMaxLength(50);
            cf.setValue(color);
            cf.setTextColor(0xFFFFFFFF);
            cf.setBordered(true);

            // Command field — NO setResponder; keyPressed handles suggestions
            MyEditBox cmd = new MyEditBox(this.font, fieldX, cy, cmdFieldW, fieldHeight,
                    Component.translatable("gui.wifi.signgui.signcmd"), minecraft, this);
            cmd.setMaxLength(32500);
            cmd.setValue(command);
            if (!command.isBlank()) {
                cmd.updateCommandInfo();
            }
            cmd.setTextColor(0xFFFFFFFF);
            cmd.setBordered(true);
            final int idx = i;
            cmd.setResponder((value) -> {
                onEdited(value, idx);
            });

            // Format buttons B I U S
            Button bold = Button.builder(fmtBtnLabel("&l", text, "B"),
                    btn -> {
                        toggleFmt(li, "&l");
                        refreshFmtButtons();
                    })
                    .pos(btnBaseX, ty).size(14, fieldHeight).build();
            Button italic = Button.builder(fmtBtnLabel("&o", text, "I"),
                    btn -> {
                        toggleFmt(li, "&o");
                        refreshFmtButtons();
                    })
                    .pos(btnBaseX + 15, ty).size(14, fieldHeight).build();
            Button underline = Button.builder(fmtBtnLabel("&n", text, "U"),
                    btn -> {
                        toggleFmt(li, "&n");
                        refreshFmtButtons();
                    })
                    .pos(btnBaseX, cy).size(14, fieldHeight).build();
            Button strike = Button.builder(fmtBtnLabel("&m", text, "S"),
                    btn -> {
                        toggleFmt(li, "&m");
                        refreshFmtButtons();
                    })
                    .pos(btnBaseX + 15, cy).size(14, fieldHeight).build();

            textFields[i] = tf;
            colorFields[i] = cf;
            commandField[i] = cmd;
            boldButtons[i] = bold;
            italicButtons[i] = italic;
            underlineButtons[i] = underline;
            strikeButtons[i] = strike;

            this.addRenderableWidget(tf);
            this.addRenderableWidget(cf);
            this.addRenderableWidget(cmd);
            this.addRenderableWidget(bold);
            this.addRenderableWidget(italic);
            this.addRenderableWidget(underline);
            this.addRenderableWidget(strike);
        }

        // Glow row
        int glowLabelX = contentX;
        int glowBtnX = glowLabelX + font.width(Component.translatable("gui.wifi.signgui.glow.label")) + 4;
        int glowColorLX = glowBtnX + 56;
        int glowColorFX = glowColorLX + font.width(Component.translatable("gui.wifi.signgui.inkcolor.label")) + 4;

        glowToggleButton = Button
                .builder(Component.translatable(isGlowing ? "gui.wifi.signgui.glow.on" : "gui.wifi.signgui.glow.off"),
                        btn -> {
                            isGlowing = !isGlowing;
                            glowToggleButton.setMessage(Component.translatable(
                                    isGlowing ? "gui.wifi.signgui.glow.on" : "gui.wifi.signgui.glow.off"));
                        })
                .pos(glowBtnX, glowRowTop).size(52, 16).build();

        glowColorField = new EditBox(this.font, glowColorFX, glowRowTop, 80, 16,
                Component.translatable("gui.wifi.signgui.inkcolor"));
        glowColorField.setMaxLength(20);
        glowColorField.setValue(inkColorName);
        glowColorField.setTextColor(0xFFFFFFFF);
        glowColorField.setBordered(true);

        this.addRenderableWidget(glowToggleButton);
        this.addRenderableWidget(glowColorField);

        // Action buttons
        confirmButton = Button.builder(Component.translatable("gui.ok"), btn -> doConfirm())
                .pos(this.width / 2 + 4, actionButtonTop).size(100, 20).build();
        cancelButton = Button.builder(Component.translatable("gui.cancel"), btn -> this.onClose())
                .pos(this.width / 2 + 108, actionButtonTop).size(100, 20).build();
        changeSideButton = Button.builder(
                Component.translatable("gui.wifi.signgui.button.changeside",
                        Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "back" : "front"))),
                btn -> {
                    ClientState.textIsFront = !ClientState.textIsFront;
                    titleDisplayer = Component.translatable("gui.wifi.signgui.title",
                            Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back")));
                    changeSideButton.setMessage(Component.translatable("gui.wifi.signgui.button.changeside",
                            Component
                                    .translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "back" : "front"))));
                    // reloadFromSign();
                })
                .pos(this.width / 2 - 208, actionButtonTop).size(100, 20).build();
        reloadButton = Button.builder(Component.translatable("gui.wifi.signgui.button.reload"),
                btn -> reloadFromSign())
                .pos(this.width / 2 - 104, actionButtonTop).size(100, 20).build();

        this.addRenderableWidget(confirmButton);
        this.addRenderableWidget(cancelButton);
        this.addRenderableWidget(changeSideButton);
        this.addRenderableWidget(reloadButton);

        // NBT preview widgets
        int nbtBtnY = this.height - 28;
        int nbtBoxY = TAB_BTN_Y + TAB_BTN_H + 16;
        int nbtBoxH = nbtBtnY - nbtBoxY - 6;

        copyNbtCmdButton = Button
                .builder(Component.translatable("gui.wifi.signgui.nbt.copy"),
                        btn -> Minecraft.getInstance().keyboardHandler.setClipboard(buildDataCmd()))
                .pos(this.width / 2 - 102, nbtBtnY).size(100, 20).build();
        executeNbtCmdButton = Button
                .builder(Component.translatable("gui.wifi.signgui.nbt.execute"), btn -> {
                    String cmd = buildDataCmd();
                    String toSend = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                    if (Minecraft.getInstance().player != null)
                        Minecraft.getInstance().player.connection.sendCommand(toSend);
                    this.onClose();
                })
                .pos(this.width / 2 + 2, nbtBtnY).size(100, 20).build();

        nbtPreviewBox = MultiLineEditBox.builder().setX(10).setY(nbtBoxY)
                .build(this.font, this.width - 20, nbtBoxH,
                        Component.translatable("gui.wifi.signgui.nbt.preview"));
        nbtPreviewBox.setValue(buildDataCmd());
        nbtPreviewBox.setCharacterLimit(65535);

        this.addRenderableWidget(copyNbtCmdButton);
        this.addRenderableWidget(executeNbtCmdButton);
        this.addRenderableWidget(nbtPreviewBox);

        this.setInitialFocus(this.textFields[0]);
        updateTabWidgets();
    }

    // ---- Tab switching ----

    private void switchTab(Tab tab) {
        currentTab = tab;
        if (tab == Tab.NBT_PREVIEW) {
            rebuildNbtLines();
            nbtPreviewBox.setValue(buildDataCmd());
        } else {
            editScrollOffset = 0;
        }
        updateTabWidgets();
    }

    private void updateTabWidgets() {
        boolean edit = (currentTab == Tab.EDIT);
        for (int i = 0; i < 4; i++) {
            setVis(textFields[i], edit);
            setVis(colorFields[i], edit);
            setVis(commandField[i], edit);
            setVis(boldButtons[i], edit);
            setVis(italicButtons[i], edit);
            setVis(underlineButtons[i], edit);
            setVis(strikeButtons[i], edit);
        }
        setVis(glowToggleButton, edit);
        setVis(glowColorField, edit);
        setVis(confirmButton, edit);
        setVis(cancelButton, edit);
        setVis(changeSideButton, edit);
        setVis(reloadButton, edit);
        setVis(copyNbtCmdButton, !edit);
        setVis(executeNbtCmdButton, !edit);
        setVis(nbtPreviewBox, !edit);
    }

    private static void setVis(net.minecraft.client.gui.components.AbstractWidget w, boolean vis) {
        w.visible = vis;
        w.active = vis;
    }

    // ---- Format-toggle helpers ----

    private MutableComponent fmtBtnLabel(String code, String text, String label) {
        return hasFmt(text, code)
                ? Component.literal(label).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.GOLD)
                : Component.literal(label);
    }

    private boolean hasFmt(String text, String code) {
        Matcher m = FORMAT_CODE_PATTERN.matcher(text);
        if (m.find() && m.end() > 0)
            return text.substring(0, m.end()).contains(code);
        return false;
    }

    private void toggleFmt(int i, String code) {
        String cur = textFields[i].getValue();
        Matcher m = FORMAT_CODE_PATTERN.matcher(cur);
        String prefix = m.find() ? cur.substring(0, m.end()) : "";
        String rest = cur.substring(prefix.length());
        prefix = prefix.contains(code) ? prefix.replace(code, "") : prefix + code;
        textFields[i].setValue(prefix + rest);
    }

    private void refreshFmtButtons() {
        for (int i = 0; i < 4; i++) {
            String t = textFields[i].getValue();
            boldButtons[i].setMessage(fmtBtnLabel("&l", t, "B"));
            italicButtons[i].setMessage(fmtBtnLabel("&o", t, "I"));
            underlineButtons[i].setMessage(fmtBtnLabel("&n", t, "U"));
            strikeButtons[i].setMessage(fmtBtnLabel("&m", t, "S"));
        }
    }

    // ---- Line data extraction ----

    private String lineToEditText(MutableComponent line) {
        String text = line.getString().replaceAll("&", "&&").replaceAll("§", "&");
        Style style = line.getStyle();
        if (style.isBold())
            text = "&l" + text;
        if (style.isItalic())
            text = "&o" + text;
        if (style.isObfuscated())
            text = "&k" + text;
        if (style.isUnderlined())
            text = "&n" + text;
        if (style.isStrikethrough())
            text = "&m" + text;
        return text;
    }

    private String extractCommand(MutableComponent line) {
        Style style = line.getStyle();
        if (style != null) {
            ClickEvent ce = style.getClickEvent();
            if (ce != null && ce.action().equals(Action.RUN_COMMAND))
                return ((ClickEvent.RunCommand) ce).command();
        }
        return "";
    }

    private String extractColorStr(MutableComponent line) {
        TextColor tc = line.getStyle().getColor();
        if (tc != null) {
            try {
                return tc.serialize();
            } catch (Exception ignored) {
            }
        }
        return "reset";
    }

    // ---- Sign reload ----

    private void reloadFromSign() {
        SignText st = sign.getText(ClientState.textIsFront);
        isGlowing = st.hasGlowingText();
        DyeColor inkDye = st.getColor();
        glowColorField.setValue(inkDye != null ? inkDye.getSerializedName() : "black");
        glowToggleButton.setMessage(Component.translatable(
                isGlowing ? "gui.wifi.signgui.glow.on" : "gui.wifi.signgui.glow.off"));
        for (int i = 0; i < 4; ++i) {
            MutableComponent line = (MutableComponent) st.getMessage(i, false);
            textFields[i].setValue(lineToEditText(line));
            colorFields[i].setValue(extractColorStr(line));
            commandField[i].setValue(extractCommand(line));
        }
        refreshFmtButtons();
    }

    // ---- Confirm ----

    private void doConfirm() {
        BlockPos pos = sign.getBlockPos();
        String[] lineJsons = new String[4];
        for (int i = 0; i < 4; i++)
            lineJsons[i] = buildLineJson(i);
        String inkColor = glowColorField.getValue();
        if (inkColor == null || inkColor.isEmpty())
            inkColor = "black";
        ClientPlatformHelper.sendToServer(
                new SignEditUpdateBlockPayload(pos, lineJsons, ClientState.textIsFront, isGlowing, inkColor));
        this.onClose();
    }

    // ---- Component JSON building ----

    private String buildLineJson(int i) {
        String rawText = textFields[i].getValue();
        Matcher m = FORMAT_CODE_PATTERN.matcher(rawText);
        String prefix = m.find() ? rawText.substring(0, m.end()) : "";
        String text = rawText.substring(prefix.length())
                .replaceAll("&&", "\ufffe").replaceAll("&", "§").replaceAll("\ufffe", "&");

        MutableComponent comp = Component.literal(text);
        Style style = Style.EMPTY;

        if (prefix.contains("&l"))
            style = style.withBold(true);
        if (prefix.contains("&o"))
            style = style.withItalic(true);
        if (prefix.contains("&k"))
            style = style.withObfuscated(true);
        if (prefix.contains("&n"))
            style = style.withUnderlined(true);
        if (prefix.contains("&m"))
            style = style.withStrikethrough(true);

        String colorStr = colorFields[i].getValue();
        if (colorStr != null && !colorStr.isEmpty() && !colorStr.equalsIgnoreCase("reset")) {
            TextColor tc = TextColor.parseColor(colorStr).result().orElse(null);
            if (tc != null)
                style = style.withColor(tc);
        }
        String cmd = commandField[i].getValue();
        if (cmd != null && !cmd.isEmpty())
            style = style.withClickEvent(new ClickEvent.RunCommand(cmd));

        comp.setStyle(style);
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, comp)
                .result().map(Object::toString).orElse("{\"text\":\"\"}");
    }

    // ---- NBT / data-command building ----

    private String buildDataCmd() {
        BlockPos pos = sign.getBlockPos();
        String side = ClientState.textIsFront ? "front_text" : "back_text";
        StringBuilder sb = new StringBuilder("/data merge block ");
        sb.append(pos.getX()).append(" ").append(pos.getY()).append(" ").append(pos.getZ());
        sb.append(" {").append(side).append(":{messages:[");
        for (int i = 0; i < 4; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(buildLineJson(i).replaceAll("\u00a7", "\\\\u00a7"));
        }
        String ink = glowColorField != null ? glowColorField.getValue() : "black";
        if (ink == null || ink.isEmpty())
            ink = "black";
        sb.append("],color:\"").append(ink).append("\"");
        sb.append(",has_glowing_text:").append(isGlowing ? "1b" : "0b");
        sb.append("}}");
        return sb.toString();
    }

    private void rebuildNbtLines() {
        nbtLines.clear();
        String cmd = buildDataCmd();
        int wrap = Math.max(60, (this.width - 40) / 6);
        while (cmd.length() > wrap) {
            nbtLines.add(cmd.substring(0, wrap));
            cmd = cmd.substring(wrap);
        }
        nbtLines.add(cmd);
    }

    // ---- Rendering helpers ----

    private void drawText(GuiGraphicsExtractor ctx, Font font, Component text, int x, int y, int color) {
        ctx.text(font, text, x, y, color, true);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.extractBlurredBackground(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {

        // Title
        context.centeredText(this.font, this.titleDisplayer, this.width / 2, titleTop, 0xFFFFFFFF);

        drawTabHighlight(context);

        if (currentTab == Tab.EDIT) {
            // Tip lines
            context.centeredText(this.font,
                    Component.translatable("gui.wifi.signgui.tip_line1"), this.width / 2, tipTop, 0xFFAAAAAA);
            context.centeredText(this.font,
                    Component.translatable("gui.wifi.signgui.tip_line2"), this.width / 2, tipTop + 10, 0xFFAAAAAA);

            // Tab highlight underline
            context.enableScissor(0, fieldStartPos, width, height);

            int labelX = contentX + 2;
            int fieldX = contentX + 20;
            int panelX = contentX - 2;
            int panelW = contentW + 4;

            for (int i = 0; i < 4; ++i) {
                int rowTop = fieldStartPos + i * lineHeight - editScrollOffset;
                int rowBottom = rowTop + lineHeight - 4;

                // Row background panel
                boolean rowFocused = textFields[i].isFocused() || colorFields[i].isFocused()
                        || commandField[i].isFocused();
                int panelColor = rowFocused ? ROW_BG_HOVER : ROW_BG_COLOR;
                context.fill(panelX, rowTop - 2, panelX + panelW, rowBottom + 2, panelColor);
                // Thin left accent bar for the focused row
                if (rowFocused) {
                    context.fill(panelX, rowTop - 2, panelX + 2, rowBottom + 2, 0x88FFDD44);
                }

                // Line number label "1" … "4"
                int lblColor = rowFocused ? LABEL_HL_COLOR : LABEL_COLOR;
                drawText(context, this.font,
                        Component.literal(String.valueOf(i + 1)).withStyle(ChatFormatting.BOLD),
                        labelX, rowTop + textLabelOffsetY, lblColor);

                // "Cmd" small label aligned with command field row
                drawText(context, this.font,
                        Component.literal("» /").withStyle(ChatFormatting.DARK_GRAY),
                        labelX, rowTop + cmdLabelOffsetY, 0xFF666666);
            }

            // Glow row
            int glowY = glowRowTop - editScrollOffset;
            // Thin divider above glow row
            context.fill(panelX, glowY - 4, panelX + panelW, glowY - 3, 0x33FFFFFF);
            drawText(context, this.font,
                    Component.translatable("gui.wifi.signgui.glow.label"),
                    contentX, glowY + (16 - 8) / 2, LABEL_COLOR);
            int colorLabelX = glowToggleButton.getX() + glowToggleButton.getWidth() + 4;
            drawText(context, this.font,
                    Component.translatable("gui.wifi.signgui.inkcolor.label"),
                    colorLabelX, glowY + (16 - 8) / 2, LABEL_COLOR);

            context.disableScissor();
            // Scrollbar
            if (maxEditScrollOffset > 0)
                drawScrollbar(context);
        }
        if (currentTab == Tab.EDIT) {
            context.enableScissor(0, fieldStartPos, width, height);
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        if (currentTab == Tab.EDIT) {
            context.disableScissor();
            this.tabEditButton.extractRenderState(context, mouseX, mouseY, deltaTicks);
            this.tabNbtButton.extractRenderState(context, mouseX, mouseY, deltaTicks);
        }
        for (int i = 0; i < 4; i++) {
            if (commandField[i] != null && commandField[i].isFocused()) {
                commandField[i].renderSuggestions(context, mouseX, mouseY, deltaTicks);
            }
        }
    }

    /** Draw a small underline below the active tab button */
    private void drawTabHighlight(GuiGraphicsExtractor context) {
        Button active = (currentTab == Tab.EDIT) ? tabEditButton : tabNbtButton;
        int x1 = active.getX();
        int x2 = x1 + active.getWidth();
        int y = active.getY() + active.getHeight();
        context.fill(x1, y, x2, y + 2, 0xFFFFDD44);
    }

    private void drawScrollbar(GuiGraphicsExtractor context) {
        int sx = this.width - SCROLL_BAR_WIDTH - 2;
        int sh = editViewportHeight;
        int th = Math.max(20, (int) ((float) editViewportHeight / editContentHeight * sh));
        int ty = editViewportTop
                + (int) ((float) editScrollOffset / maxEditScrollOffset * (sh - th));

        context.fill(sx, editViewportTop, sx + SCROLL_BAR_WIDTH, editViewportTop + sh, 0x22FFFFFF);
        context.fill(sx, ty, sx + SCROLL_BAR_WIDTH, ty + th, 0xBBFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (currentTab == Tab.EDIT && maxEditScrollOffset > 0) {
            editScrollOffset = Math.max(0,
                    Math.min(maxEditScrollOffset, editScrollOffset - (int) (vAmount * 10)));
            updateWidgetPositionsForScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    private void updateWidgetPositionsForScroll() {
        int viewportBottom = editViewportTop + editViewportHeight;
        int fieldX = contentX + 20;

        for (int i = 0; i < 4; ++i) {
            int ty = fieldStartPos + i * lineHeight - editScrollOffset;
            int cy = fieldStartPos + lineHeight / 2 + i * lineHeight - editScrollOffset;

            textFields[i].setY(ty);
            colorFields[i].setY(ty);
            commandField[i].setY(cy);
            boldButtons[i].setY(ty);
            italicButtons[i].setY(ty);
            underlineButtons[i].setY(ty);
            strikeButtons[i].setY(ty);

            boolean rowVis = ty + fieldHeight > editViewportTop && ty < viewportBottom;
            boolean cmdVis = cy + fieldHeight > editViewportTop && cy < viewportBottom;
            setVis(textFields[i], rowVis);
            setVis(colorFields[i], rowVis);
            setVis(boldButtons[i], rowVis);
            setVis(italicButtons[i], rowVis);
            setVis(underlineButtons[i], rowVis);
            setVis(strikeButtons[i], rowVis);
            setVis(commandField[i], cmdVis);
        }

        int glowY = glowRowTop - editScrollOffset;
        int actionY = actionButtonTop - editScrollOffset;

        glowToggleButton.setY(glowY);
        glowColorField.setY(glowY);
        boolean glowVis = glowY + 16 > editViewportTop && glowY < viewportBottom;
        setVis(glowToggleButton, glowVis);
        setVis(glowColorField, glowVis);

        confirmButton.setY(actionY);
        cancelButton.setY(actionY);
        changeSideButton.setY(actionY);
        reloadButton.setY(actionY);
        boolean actionVis = actionY + 20 > editViewportTop && actionY < viewportBottom;
        setVis(confirmButton, actionVis);
        setVis(cancelButton, actionVis);
        setVis(changeSideButton, actionVis);
        setVis(reloadButton, actionVis);
    }

    // ---- keyPressed — Tab/Enter intercepted BEFORE super ----

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.getDigit();

        // ── Per-field routing ─────────────────────────────────────────────────
        for (int i = 0; i < 4; i++) {

            if (commandField[i].isFocused()) {
                if (commandField[i].keyPressed(keyInput)) {
                    return true;
                }
                // Enter moves focus to next line
                if (keyCode == 257 || keyCode == 335) {
                    hideAllSuggestions();
                    setFocused(textFields[i == 3 ? 0 : i + 1]);
                    return true;
                }
                // Let EditBox handle the key (typing, backspace, cursor move, etc.)
                boolean handled = super.keyPressed(keyInput);
                // Refresh suggestions after EVERY key (cursor may have moved)
                return handled;
            }

            if (textFields[i].isFocused()) {
                if (keyCode == 257 || keyCode == 335) {
                    super.keyPressed(keyInput); // let EditBox finish
                    setFocused(colorFields[i]);
                    return true;
                }
                return super.keyPressed(keyInput);
            }

            if (colorFields[i].isFocused()) {
                if (keyCode == 257 || keyCode == 335) {
                    super.keyPressed(keyInput);
                    setFocused(commandField[i]);
                    hideAllSuggestions();
                    commandField[i].activeCommandSuggestions();
                    return true;
                }
                return super.keyPressed(keyInput);
            }
        }
        return super.keyPressed(keyInput);
    }

    // ---- mouseClicked ----

    public void hideAllSuggestions() {
        for (int i = 0; i < 4; i++) {
            commandField[i].hideCommandSuggestions();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        // 补全
        if (currentTab == Tab.EDIT) {
            for (int i = 0; i < 4; i++) {
                if (commandField[i].isFocused()) {
                    if (commandField[i].handleSuggestionMouseClicked(click, doubled)) {
                        return true;
                    }
                }
            }
        }
        // Scrollbar click
        if (currentTab == Tab.EDIT && maxEditScrollOffset > 0) {
            int sx = this.width - SCROLL_BAR_WIDTH - 2;
            if (mouseX >= sx && mouseX < sx + SCROLL_BAR_WIDTH
                    && mouseY >= editViewportTop && mouseY < editViewportTop + editViewportHeight) {
                isDraggingScrollbar = true;
                dragStartY = (int) mouseY;
                dragStartOffset = editScrollOffset;
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isDraggingScrollbar && maxEditScrollOffset > 0) {
            int deltaY = (int) event.y() - dragStartY;
            int sh = editViewportHeight;
            int th = Math.max(20, (int) ((float) editViewportHeight / editContentHeight * sh));
            float scrollRatio = (float) deltaY / (sh - th);
            editScrollOffset = Math.max(0,
                    Math.min(maxEditScrollOffset, dragStartOffset + (int) (scrollRatio * maxEditScrollOffset)));
            updateWidgetPositionsForScroll();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        isDraggingScrollbar = false;
        return super.mouseReleased(event);
    }

    // ---- Resize ----

    @Override
    public void resize(int width, int height) {
        String[] cmds = new String[4], colors = new String[4], texts = new String[4];
        for (int i = 0; i < 4; i++) {
            cmds[i] = commandField[i].getValue();
            colors[i] = colorFields[i].getValue();
            texts[i] = textFields[i].getValue();
        }
        String glowColor = glowColorField.getValue();
        boolean glow = isGlowing;
        Tab tab = currentTab;
        int savedScroll = editScrollOffset;

        this.init(width, height);

        for (int i = 0; i < 4; i++) {
            commandField[i].setValue(cmds[i]);
            colorFields[i].setValue(colors[i]);
            textFields[i].setValue(texts[i]);
        }
        glowColorField.setValue(glowColor);
        isGlowing = glow;
        glowToggleButton.setMessage(Component.translatable(
                isGlowing ? "gui.wifi.signgui.glow.on" : "gui.wifi.signgui.glow.off"));
        refreshFmtButtons();
        currentTab = tab;
        if (currentTab == Tab.NBT_PREVIEW) {
            rebuildNbtLines();
            nbtPreviewBox.setValue(buildDataCmd());
        }
        editScrollOffset = Math.min(savedScroll, maxEditScrollOffset);
        if (editScrollOffset > 0)
            updateWidgetPositionsForScroll();
        updateTabWidgets();
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    protected Component getUsageNarration() {
        return super.getUsageNarration();
    }
}