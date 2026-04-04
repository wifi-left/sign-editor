package io.wifi.signgui;

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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public class SignEditorScreen extends Screen {

    // 创建一个文本框数组，用来编辑告示牌的每一行文本
    private EditBox[] textFields = new EditBox[4];
    private EditBox[] colorFields = new EditBox[4];
    private EditBox[] commandField = new EditBox[4];
    private Component titleDisplayer = null;
    private int FiledHeight = 16;
    private int LineHeight = 40;

    private int titleTop = 10;
    private int tipTop = 24;

    private int FiledStartPos = 44;
    private int TextTipStartPos = 48;
    private int CommandTipStartPos = 68;
    // 创建一个告示牌方块实体对象，用来获取和设置告示牌的数据

    private Button confirmButton;
    private Button cancelButton;
    private Button changeSideButton;
    private Button reloadButton;

    // 创建两个按钮
    private final SignBlockEntity sign;

    private void calcPositions() {
        if (this.height <= 380) {
            TextTipStartPos = 48;
            CommandTipStartPos = 68;
            titleTop = 10;
            tipTop = 24;
            FiledHeight = 16;
            LineHeight = 40;
            FiledStartPos = 52;
        } else {
            titleTop = 20;
            tipTop = 36;
            TextTipStartPos = 74;
            FiledHeight = 20;
            LineHeight = 48;
            FiledStartPos = 68;
            CommandTipStartPos = 98;
        }
    }

    public SignEditorScreen(SignBlockEntity sign) {
        super(Component.translatable("gui.wifi.signgui.title",
                Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back"))));
        this.sign = sign; // 保存告示牌方块实体对象
        this.titleDisplayer = Component.translatable("gui.wifi.signgui.title",
                Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back")));
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.getDigit();
        for (int i = 0; i < 4; i++) {
            if (this.commandField[i].isFocused()) {
                if (super.keyPressed(keyInput)) {
                    return true;
                } else if (keyCode != 257 && keyCode != 335) {
                    return false;
                } else {
                    if (i == 3) {
                        this.setFocused(textFields[0]);
                    } else {
                        this.setFocused(this.textFields[i + 1]);
                    }

                    return true;
                }
            } else if (this.textFields[i].isFocused()) {
                if (super.keyPressed(keyInput)) {
                    return true;
                } else if (keyCode != 257 && keyCode != 335) {
                    return false;
                } else {
                    this.setFocused(this.colorFields[i]);

                    return true;
                }
            } else if (this.colorFields[i].isFocused()) {
                if (super.keyPressed(keyInput)) {
                    return true;
                } else if (keyCode != 257 && keyCode != 335) {
                    return false;
                } else {
                    this.setFocused(this.commandField[i]);
                    return true;
                }
            }

        }
        return true;
    }

    @Override
    protected void init() {
        super.init();

        calcPositions();

        // 遍历告示牌的每一行文本
        SignText signText = sign.getText(ClientState.textIsFront);
        for (int i = 0; i < 4; ++i) {
            // 获取告示牌的文本内容
            MutableComponent line = (MutableComponent) signText.getMessage(i, false);
            String text = line.getString().replaceAll("&", "\uff06").replaceAll("§", "&");
            String command = "";

            Style textStyle = line.getStyle();
            if (textStyle != null) {
                ClickEvent clickEvent = textStyle.getClickEvent();
                if (clickEvent != null) {
                    if (clickEvent.action().equals(Action.RUN_COMMAND)) {
                        command = ((ClickEvent.RunCommand) clickEvent).command();
                    }
                }

            }
            String color = "black";
            if (textStyle.isBold()) {
                text = "&l" + text;
            }
            if (textStyle.isItalic()) {
                text = "&o" + text;
            }
            if (textStyle.isObfuscated()) {
                text = "&k" + text;
            }
            if (textStyle.isUnderlined()) {
                text = "&n" + text;
            }
            if (textStyle.isStrikethrough()) {
                text = "&m" + text;
            }
            TextColor ChatColor = textStyle.getColor();
            if (ChatColor != null) {
                try {
                    color = ChatColor.serialize();
                } catch (Exception e) {
                    color = "black";
                    e.printStackTrace();
                }
            }
            // 创建一个文本框对象，并设置其位置、大小、最大长度等属性
            EditBox textField = new EditBox(this.font, this.width / 2 - 72,
                    FiledStartPos + i * LineHeight, 186,
                    FiledHeight, Component.translatable("gui.wifi.signgui.signtext"));

            EditBox colorField = new EditBox(this.font, this.width / 2 + 118,
                    FiledStartPos + i * LineHeight, 50,
                    FiledHeight, Component.translatable("gui.wifi.signgui.signtext"));

            EditBox commandField = new EditBox(this.font, this.width / 2 - 72,
                    FiledStartPos + LineHeight / 2 + i * LineHeight,
                    240, FiledHeight, Component.translatable("gui.wifi.signgui.signcmd"));
            textField.setMaxLength(384);
            textField.setValue(text);
            textField.setTextColor(-1);
            commandField.setMaxLength(32500);
            commandField.setValue(command);
            commandField.setTextColor(-1);
            colorField.setMaxLength(50);
            colorField.setValue(color);
            colorField.setTextColor(-1);
            this.textFields[i] = textField; // 保存文本框对象到数组中
            this.commandField[i] = commandField;
            this.colorFields[i] = colorField;

            this.addWidget(this.textFields[i]); // 添加文本框对象到GUI中
            this.addWidget(this.colorFields[i]); // 添加文本框对象到GUI中
            this.addWidget(this.commandField[i]); // 添加文本框对象到GUI中

        }

        confirmButton = Button.builder(Component.translatable("gui.ok"), button -> {
            // 确认按钮的点击事件，发送数据包给服务器，更新告示牌的文本和命令
            BlockPos pos = sign.getBlockPos();
            String[] texts = new String[4];
            String[] colors = new String[4];
            String[] cmds = new String[4];
            for (int i = 0; i < 4; ++i) {
                texts[i] = textFields[i].getValue().replaceAll("&&", "\uff06").replaceAll("&", "§").replaceAll("＆",
                        "&");
                colors[i] = colorFields[i].getValue();
                if (colors[i] == null || colors[i].isEmpty())
                    colors[i] = "black";
                cmds[i] = commandField[i].getValue();
            }
            ClientPlatformHelper
                    .sendToServer(new SignEditUpdateBlockPayload(pos, texts, colors, cmds, ClientState.textIsFront));
            // 关闭 GUI & 修改文本
            this.onClose();
        }).pos(this.width / 2 + 4, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();

        cancelButton = Button.builder(Component.translatable("gui.cancel"), button -> {
            // 取消按钮的点击事件，关闭 GUI
            this.onClose();
        }).pos(this.width / 2 + 108, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();
        changeSideButton = Button.builder(Component.translatable("gui.wifi.signgui.button.changeside",
                Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "back" : "front"))), button -> {
                    // 切换告示牌方向
                    ClientState.textIsFront = !ClientState.textIsFront;
                    this.titleDisplayer = Component.translatable("gui.wifi.signgui.title",
                            Component.translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "front" : "back")));
                    this.changeSideButton.setMessage(Component.translatable("gui.wifi.signgui.button.changeside",
                            Component
                                    .translatable("gui.wifi.signgui." + (ClientState.textIsFront ? "back" : "front"))));
                }).pos(this.width / 2 - 208, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();
        reloadButton = Button.builder(Component.translatable("gui.wifi.signgui.button.reload"), button -> {
            // 重载文本
            SignText lsignText = sign.getText(ClientState.textIsFront);
            for (int i = 0; i < 4; ++i) {
                // 获取告示牌的文本内容
                MutableComponent line = (MutableComponent) lsignText.getMessage(i, false);
                String text = line.getString().replaceAll("&", "\uff06").replaceAll("§", "&");
                String command = "";

                Style textStyle = line.getStyle();
                if (textStyle != null) {
                    ClickEvent clickEvent = textStyle.getClickEvent();
                    if (clickEvent != null) {
                        if (clickEvent.action().equals(Action.RUN_COMMAND))
                            command = ((ClickEvent.RunCommand) clickEvent).command();
                    }
                }
                String color = "black";
                if (textStyle.isBold()) {
                    text = "&l" + text;
                }
                if (textStyle.isItalic()) {
                    text = "&o" + text;
                }
                if (textStyle.isObfuscated()) {
                    text = "&k" + text;
                }
                if (textStyle.isUnderlined()) {
                    text = "&n" + text;
                }
                if (textStyle.isStrikethrough()) {
                    text = "&m" + text;
                }
                TextColor ChatColor = textStyle.getColor();
                if (ChatColor != null) {
                    try {
                        color = ChatColor.serialize();
                    } catch (Exception e) {
                        color = "black";
                        e.printStackTrace();
                    }
                }
                textFields[i].setValue(text);
                colorFields[i].setValue(color);
                commandField[i].setValue(command);

            }
        }).pos(this.width / 2 - 104, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();
        this.addRenderableWidget(confirmButton); // 添加确认按钮对象到GUI中
        this.addRenderableWidget(cancelButton); // 添加取消按钮对象到GUI中
        this.addRenderableWidget(changeSideButton); // 添加切换方向按钮对象到GUI中
        this.addRenderableWidget(reloadButton); // 添加重新加载按钮对象到GUI中
        this.setInitialFocus(this.textFields[0]); // 设置初始焦点为第一个文本框

    }

    @Override
    public void removed() {
        super.removed();
    }

    private void drawCenteredTextWithShadow(GuiGraphicsExtractor matrices, Font textRenderer, Component text, int x,
            int y,
            int color, boolean shadow) {
        matrices.text(textRenderer, text, x - textRenderer.width(text), y, color, shadow);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.extractBlurredBackground(context);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void resize(int width, int height) {
        String[] commands = new String[4], colors = new String[4], texts = new String[4];
        for (int i = 0; i < 4; i++) {
            commands[i] = this.commandField[i].getValue();
            colors[i] = this.colorFields[i].getValue();
            texts[i] = this.textFields[i].getValue();
        }
        this.init(width, height);
        for (int i = 0; i < 4; i++) {
            this.commandField[i].setValue(commands[i]);
            this.colorFields[i].setValue(colors[i]);
            this.textFields[i].setValue(texts[i]);
        }
    }

    @Override
    protected Component getUsageNarration() {
        return super.getUsageNarration();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        drawCenteredTextWithShadow(context, this.font, this.titleDisplayer, this.width / 2 - 180, titleTop,
                -1,
                true); // 渲染标题
        drawCenteredTextWithShadow(context, this.font,
                Component.translatable("gui.wifi.signgui.tip_line1"),
                this.width / 2 - 180, tipTop, -1, true);
        drawCenteredTextWithShadow(context, this.font,
                Component.translatable("gui.wifi.signgui.tip_line2"),
                this.width / 2 - 180, tipTop + 12, -1, true);
        for (int i = 0; i < 4; ++i) {
            drawCenteredTextWithShadow(context, this.font,
                    Component.translatable("gui.wifi.signgui.signtext", i + 1), this.width / 2 - 180,
                    TextTipStartPos + i * LineHeight,
                    -1, true); // 渲染文本标签
            drawCenteredTextWithShadow(context, this.font,
                    Component.translatable("gui.wifi.signgui.signcmd", i + 1), this.width / 2 - 180,
                    CommandTipStartPos + i * LineHeight,
                    -1, true); // 渲染命令标签
            // this.textFields[i].render(context, mouseX, mouseY, deltaTicks);
            // this.colorFields[i].render(context, mouseX, mouseY, deltaTicks);
            // this.commandField[i].render(context, mouseX, mouseY, deltaTicks);

        }
    }
}
