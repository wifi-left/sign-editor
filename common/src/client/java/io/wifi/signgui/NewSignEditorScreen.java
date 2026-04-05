package io.wifi.signgui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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

public class NewSignEditorScreen extends Screen {

    // ---- Tab system ----
    private enum Tab { EDIT, NBT_PREVIEW }

    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("^(&[loknm])*");

    private Tab currentTab = Tab.EDIT;

    // ---- Edit-tab widgets ----
    private final EditBox[] textFields = new EditBox[4];
    private final EditBox[] colorFields = new EditBox[4];
    private final EditBox[] commandField = new EditBox[4];
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

    // ---- NBT preview state ----
    private final List<String> nbtLines = new ArrayList<>();
    private int nbtScrollOffset = 0;
    private static final int NBT_LINE_HEIGHT = 10;

    // ---- Action buttons ----
    private Button confirmButton;
    private Button cancelButton;
    private Button changeSideButton;
    private Button reloadButton;

    // ---- Layout vars (recalculated in calcPositions) ----
    private int FiledHeight = 16;
    private int LineHeight = 40;
    private int titleTop = 18;
    private int tipTop = 30;
    private int FiledStartPos = 56;
    private int TextTipStartPos = 52;
    private int CommandTipStartPos = 72;
    private int glowRowTop;
    private int actionButtonTop;
    private static final int TAB_BTN_Y = 2;
    private static final int TAB_BTN_H = 14;

    private Component titleDisplayer;
    private final SignBlockEntity sign;

    // ---------------------------------------------------------------

    public NewSignEditorScreeneen(SignBlockEntity sign) {
        super(Component.translatable("gui.wifi.signgui.title",
                Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back"))));
        this.sign = sign;
        this.titleDisplayer = super.title;
    }

    // ---- Layout helpers ----

    private void calcPositions() {
        if (this.height <= 380) {
            titleTop = 18;
            tipTop = 30;
            TextTipStartPos = 52;
            CommandTipStartPos = 72;
            FiledHeight = 16;
            LineHeight = 40;
            FiledStartPos = 56;
        } else {
            titleTop = 24;
            tipTop = 40;
            TextTipStartPos = 74;
            FiledHeight = 20;
            LineHeight = 48;
            FiledStartPos = 68;
            CommandTipStartPos = 98;
        }
        glowRowTop = FiledStartPos + 4 * LineHeight + 4;
        actionButtonTop = glowRowTop + 24;
    }

    // ---- init ----

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        calcPositions();

        // Tab buttons
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

        int fieldX = this.width / 2 - 72;

        for (int i = 0; i < 4; ++i) {
            MutableComponent line = (MutableComponent) signText.getMessage(i, false);
            String text = lineToEditText(line);
            String command = extractCommand(line);
            String color = extractColorStr(line);

            final int li = i;
            int ty = FiledStartPos + i * LineHeight;
            int cy = FiledStartPos + LineHeight / 2 + i * LineHeight;
            int colorX = fieldX + 188;
            int btnX = colorX + 32;

            EditBox tf = new EditBox(this.font, fieldX, ty, 186, FiledHeight,
                    Component.translatable("gui.wifi.signgui.signtext"));
            tf.setMaxLength(384);
            tf.setValue(text);
            tf.setTextColor(-1);

            EditBox cf = new EditBox(this.font, colorX, ty, 30, FiledHeight,
                    Component.translatable("gui.wifi.signgui.signtext"));
            cf.setMaxLength(50);
            cf.setValue(color);
            cf.setTextColor(-1);

            EditBox cmd = new EditBox(this.font, fieldX, cy, 240, FiledHeight,
                    Component.translatable("gui.wifi.signgui.signcmd"));
            cmd.setMaxLength(32500);
            cmd.setValue(command);
            cmd.setTextColor(-1);

            // Format-toggle buttons: B I U S
            Button bold = Button.builder(Component.literal(fmtBtnLabel("&l", text, "B")),
                    btn -> { toggleFmt(li, "&l"); refreshFmtButtons(); })
                    .pos(btnX, ty).size(14, FiledHeight).build();
            Button italic = Button.builder(Component.literal(fmtBtnLabel("&o", text, "I")),
                    btn -> { toggleFmt(li, "&o"); refreshFmtButtons(); })
                    .pos(btnX + 15, ty).size(14, FiledHeight).build();
            Button underline = Button.builder(Component.literal(fmtBtnLabel("&n", text, "U")),
                    btn -> { toggleFmt(li, "&n"); refreshFmtButtons(); })
                    .pos(btnX + 30, ty).size(14, FiledHeight).build();
            Button strike = Button.builder(Component.literal(fmtBtnLabel("&m", text, "S")),
                    btn -> { toggleFmt(li, "&m"); refreshFmtButtons(); })
                    .pos(btnX + 45, ty).size(14, FiledHeight).build();

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
        glowToggleButton = Button
                .builder(Component.translatable(isGlowing ? "gui.wifi.signgui.glow.on" : "gui.wifi.signgui.glow.off"),
                        btn -> {
                            isGlowing = !isGlowing;
                            glowToggleButton.setMessage(Component.translatable(
                                    isGlowing ? "gui.wifi.signgui.glow.on" : "gui.wifi.signgui.glow.off"));
                        })
                .pos(fieldX + 30, glowRowTop).size(50, 16).build();
        glowColorField = new EditBox(this.font, fieldX + 120, glowRowTop, 80, 16,
                Component.translatable("gui.wifi.signgui.inkcolor"));
        glowColorField.setMaxLength(20);
        glowColorField.setValue(inkColorName);
        glowColorField.setTextColor(-1);

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
                            Component.translatable(
                                    "gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back")));
                    changeSideButton.setMessage(Component.translatable("gui.wifi.signgui.button.changeside",
                            Component.translatable(
                                    "gui.wifi.signgui." + (ClientState.textIsFront ? "back" : "front"))));
                    reloadFromSign();
                })
                .pos(this.width / 2 - 208, actionButtonTop).size(100, 20).build();
        reloadButton = Button.builder(Component.translatable("gui.wifi.signgui.button.reload"),
                btn -> reloadFromSign())
                .pos(this.width / 2 - 104, actionButtonTop).size(100, 20).build();

        this.addRenderableWidget(confirmButton);
        this.addRenderableWidget(cancelButton);
        this.addRenderableWidget(changeSideButton);
        this.addRenderableWidget(reloadButton);

        // NBT-preview buttons (shown only in NBT tab)
        int nbtBtnY = this.height - 28;
        copyNbtCmdButton = Button
                .builder(Component.translatable("gui.wifi.signgui.nbt.copy"),
                        btn -> Minecraft.getInstance().keyboardHandler.setClipboard(buildDataCmd()))
                .pos(this.width / 2 - 102, nbtBtnY).size(100, 20).build();
        executeNbtCmdButton = Button
                .builder(Component.translatable("gui.wifi.signgui.nbt.execute"), btn -> {
                    String cmd = buildDataCmd();
                    String toSend = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.connection.sendCommand(toSend);
                    }
                    this.onClose();
                })
                .pos(this.width / 2 + 2, nbtBtnY).size(100, 20).build();

        this.addRenderableWidget(copyNbtCmdButton);
        this.addRenderableWidget(executeNbtCmdButton);

        this.setInitialFocus(this.textFields[0]);
        updateTabWidgets();
    }

    // ---- Tab switching ----

    private void switchTab(Tab tab) {
        currentTab = tab;
        if (tab == Tab.NBT_PREVIEW) {
            rebuildNbtLines();
            nbtScrollOffset = 0;
        }
        updateTabWidgets();
    }

    private void updateTabWidgets() {
        boolean edit = currentTab == Tab.EDIT;
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
    }

    private static void setVis(net.minecraft.client.gui.components.AbstractWidget w, boolean vis) {
        w.visible = vis;
        w.active = vis;
    }

    // ---- Format-toggle helpers ----

    private String fmtBtnLabel(String code, String text, String label) {
        return hasFmt(text, code) ? "[" + label + "]" : label;
    }

    private boolean hasFmt(String text, String code) {
        Matcher m = FORMAT_CODE_PATTERN.matcher(text);
        if (m.find() && m.end() > 0) {
            return text.substring(0, m.end()).contains(code);
        }
        return false;
    }

    private void toggleFmt(int i, String code) {
        String cur = textFields[i].getValue();
        Matcher m = FORMAT_CODE_PATTERN.matcher(cur);
        String prefix = m.find() ? cur.substring(0, m.end()) : "";
        String rest = cur.substring(prefix.length());
        if (prefix.contains(code)) {
            prefix = prefix.replace(code, "");
        } else {
            prefix = prefix + code;
        }
        textFields[i].setValue(prefix + rest);
    }

    private void refreshFmtButtons() {
        for (int i = 0; i < 4; i++) {
            String t = textFields[i].getValue();
            boldButtons[i].setMessage(Component.literal(fmtBtnLabel("&l", t, "B")));
            italicButtons[i].setMessage(Component.literal(fmtBtnLabel("&o", t, "I")));
            underlineButtons[i].setMessage(Component.literal(fmtBtnLabel("&n", t, "U")));
            strikeButtons[i].setMessage(Component.literal(fmtBtnLabel("&m", t, "S")));
        }
    }

    // ---- Line data extraction ----

    private String lineToEditText(MutableComponent line) {
        String text = line.getString().replaceAll("&", "&&").replaceAll("§", "&");
        Style style = line.getStyle();
        if (style.isBold()) text = "&l" + text;
        if (style.isItalic()) text = "&o" + text;
        if (style.isObfuscated()) text = "&k" + text;
        if (style.isUnderlined()) text = "&n" + text;
        if (style.isStrikethrough()) text = "&m" + text;
        return text;
    }

    private String extractCommand(MutableComponent line) {
        Style style = line.getStyle();
        if (style != null) {
            ClickEvent ce = style.getClickEvent();
            if (ce != null && ce.action().equals(Action.RUN_COMMAND)) {
                return ((ClickEvent.RunCommand) ce).command();
            }
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
        for (int i = 0; i < 4; i++) {
            lineJsons[i] = buildLineJson(i);
        }
        String inkColor = glowColorField.getValue();
        if (inkColor == null || inkColor.isEmpty()) inkColor = "black";
        ClientPlatformHelper.sendToServer(
                new SignEditUpdateBlockPayload(pos, lineJsons, ClientState.textIsFront, isGlowing, inkColor));
        this.onClose();
    }

    // ---- Component JSON building ----

    private String buildLineJson(int i) {
        String rawText = textFields[i].getValue()
                .replaceAll("&&", "\ufffe").replaceAll("&", "§").replaceAll("\ufffe", "&");
        MutableComponent comp = Component.literal(rawText);
        Style style = Style.EMPTY;
        String colorStr = colorFields[i].getValue();
        if (colorStr != null && !colorStr.isEmpty() && !colorStr.equalsIgnoreCase("reset")) {
            TextColor tc = TextColor.parseColor(colorStr).result().orElse(null);
            if (tc != null) style = style.withColor(tc);
        }
        String cmd = commandField[i].getValue();
        if (cmd != null && !cmd.isEmpty()) {
            style = style.withClickEvent(new ClickEvent.RunCommand(cmd));
        }
        comp.setStyle(style);
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, comp)
                .result()
                .map(Object::toString)
                .orElse("{\"text\":\"\"}");
    }

    // ---- NBT / data-command building ----

    private String buildDataCmd() {
        BlockPos pos = sign.getBlockPos();
        String side = ClientState.textIsFront ? "front_text" : "back_text";
        StringBuilder sb = new StringBuilder("/data merge block ");
        sb.append(pos.getX()).append(" ").append(pos.getY()).append(" ").append(pos.getZ());
        sb.append(" {").append(side).append(":{messages:[");
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(",");
            String json = buildLineJson(i).replace("\\", "\\\\").replace("'", "\\'");
            sb.append("'").append(json).append("'");
        }
        String ink = glowColorField != null ? glowColorField.getValue() : "black";
        if (ink == null || ink.isEmpty()) ink = "black";
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

    // ---- Rendering ----

    private void drawText(GuiGraphicsExtractor ctx, Font font, Component text, int x, int y, int color) {
        ctx.text(font, text, x, y, color, true);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.extractBlurredBackground(context);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        context.centeredText(this.font, this.titleDisplayer, this.width / 2, titleTop, -1);

        if (currentTab == Tab.EDIT) {
            context.centeredText(this.font,
                    Component.translatable("gui.wifi.signgui.tip_line1"), this.width / 2, tipTop, -1);
            context.centeredText(this.font,
                    Component.translatable("gui.wifi.signgui.tip_line2"), this.width / 2, tipTop + 12, -1);

            int labelX = this.width / 2 - 180;
            for (int i = 0; i < 4; ++i) {
                drawText(context, this.font,
                        Component.translatable("gui.wifi.signgui.signtext", i + 1),
                        labelX, TextTipStartPos + i * LineHeight, -1);
                drawText(context, this.font,
                        Component.translatable("gui.wifi.signgui.signcmd", i + 1),
                        labelX, CommandTipStartPos + i * LineHeight, -1);
            }

            // Glow row labels
            int fieldX = this.width / 2 - 72;
            int labelY = glowRowTop + (16 - 8) / 2;
            drawText(context, this.font,
                    Component.translatable("gui.wifi.signgui.glow.label"), fieldX, labelY, -1);
            drawText(context, this.font,
                    Component.translatable("gui.wifi.signgui.inkcolor.label"), fieldX + 85, labelY, -1);

        } else {
            // NBT preview
            int startY = TAB_BTN_Y + TAB_BTN_H + 6;
            int endY = this.height - 32;
            int visibleLines = Math.max(1, (endY - startY) / NBT_LINE_HEIGHT);
            int maxOffset = Math.max(0, nbtLines.size() - visibleLines);
            nbtScrollOffset = Math.min(nbtScrollOffset, maxOffset);

            for (int i = 0; i < visibleLines && (i + nbtScrollOffset) < nbtLines.size(); i++) {
                context.text(this.font,
                        Component.literal(nbtLines.get(i + nbtScrollOffset)),
                        20, startY + i * NBT_LINE_HEIGHT, -1, false);
            }

            if (nbtLines.size() > visibleLines) {
                String info = (nbtScrollOffset + 1) + "/" + (maxOffset + 1);
                context.text(this.font, Component.literal(info),
                        this.width - 40, this.height - 44, 0xAAAAAA, false);
            }
        }
    }

    // ---- Input handlers ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == Tab.NBT_PREVIEW) {
            nbtScrollOffset = Math.max(0, nbtScrollOffset - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.getDigit();
        for (int i = 0; i < 4; i++) {
            if (commandField[i].isFocused()) {
                if (super.keyPressed(keyInput)) return true;
                if (keyCode == 257 || keyCode == 335) {
                    setFocused(textFields[i == 3 ? 0 : i + 1]);
                    return true;
                }
                return false;
            } else if (textFields[i].isFocused()) {
                if (super.keyPressed(keyInput)) return true;
                if (keyCode == 257 || keyCode == 335) {
                    setFocused(colorFields[i]);
                    return true;
                }
                return false;
            } else if (colorFields[i].isFocused()) {
                if (super.keyPressed(keyInput)) return true;
                if (keyCode == 257 || keyCode == 335) {
                    setFocused(commandField[i]);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void resize(int width, int height) {
        // Preserve field values across resize
        String[] cmds = new String[4], colors = new String[4], texts = new String[4];
        for (int i = 0; i < 4; i++) {
            cmds[i] = commandField[i].getValue();
            colors[i] = colorFields[i].getValue();
            texts[i] = textFields[i].getValue();
        }
        String glowColor = glowColorField.getValue();
        boolean glow = isGlowing;
        Tab tab = currentTab;

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
        if (currentTab == Tab.NBT_PREVIEW) rebuildNbtLines();
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
