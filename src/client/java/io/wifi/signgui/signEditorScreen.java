package io.wifi.signgui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent.Action;
import net.minecraft.util.math.BlockPos;

public class signEditorScreen extends Screen {

    // 创建一个文本框数组，用来编辑告示牌的每一行文本
    private TextFieldWidget[] textFields = new TextFieldWidget[4];
    private TextFieldWidget[] colorFields = new TextFieldWidget[4];
    private TextFieldWidget[] commandField = new TextFieldWidget[4];
    private Text titleDisplayer = null;
    private int FiledHeight = 16;
    private int LineHeight = 40;

    private int titleTop = 10;
    private int tipTop = 24;

    private int FiledStartPos = 44;
    private int TextTipStartPos = 48;
    private int CommandTipStartPos = 68;
    // 创建一个文本框，用来编辑告示牌绑定的命令
    // 创建一个告示牌方块实体对象，用来获取和设置告示牌的数据

    private ButtonWidget confirmButton;
    private ButtonWidget cancelButton;
    private ButtonWidget changeSideButton;
    private ButtonWidget reloadButton;

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
            FiledStartPos = 44;
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

    public signEditorScreen(SignBlockEntity sign) {
        super(Text.translatable("gui.wifi.signgui.title",
                Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "front" : "back"))));
        this.sign = sign; // 保存告示牌方块实体对象
        this.titleDisplayer = Text.translatable("gui.wifi.signgui.title",
                Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "front" : "back")));
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = 0; i < 4; i++) {
            if (this.commandField[i].isFocused()) {
                if (super.keyPressed(keyCode, scanCode, modifiers)) {
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
                if (super.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                } else if (keyCode != 257 && keyCode != 335) {
                    return false;
                } else {
                    this.setFocused(this.colorFields[i]);

                    return true;
                }
            } else if (this.colorFields[i].isFocused()) {
                if (super.keyPressed(keyCode, scanCode, modifiers)) {
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

        // this.client.setRep(true); // 设置键盘重复事件
        // 遍历告示牌的每一行文本
        SignText signText = sign.getText(signguiClient.textIsFront);
        for (int i = 0; i < 4; ++i) {
            // 获取告示牌的文本内容
            MutableText line = (MutableText) signText.getMessage(i, false);
            String text = line.getString().replaceAll("&", "\uff06").replaceAll("§", "&");
            String command = "";

            Style textStyle = line.getStyle();
            if (textStyle != null) {
                ClickEvent clickEvent = textStyle.getClickEvent();
                if (clickEvent != null) {
                    if (clickEvent.getAction().equals(Action.RUN_COMMAND)) {
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
                    color = ChatColor.getName();
                } catch (Exception e) {
                    color = "black";
                    e.printStackTrace();
                }
            }
            // 创建一个文本框对象，并设置其位置、大小、最大长度等属性
            TextFieldWidget textField = new TextFieldWidget(this.textRenderer, this.width / 2 - 72,
                    FiledStartPos + i * LineHeight, 186,
                    FiledHeight, Text.translatable("gui.wifi.signgui.signtext"));

            TextFieldWidget colorField = new TextFieldWidget(this.textRenderer, this.width / 2 + 118,
                    FiledStartPos + i * LineHeight, 50,
                    FiledHeight, Text.translatable("gui.wifi.signgui.signtext"));

            TextFieldWidget commandField = new TextFieldWidget(this.textRenderer, this.width / 2 - 72,
                    FiledStartPos + LineHeight / 2 + i * LineHeight,
                    240, FiledHeight, Text.translatable("gui.wifi.signgui.signcmd"));
            // commandField.setChangedListener(this::onCommandChanged);
            textField.setMaxLength(384);
            textField.setText(text);
            textField.setEditableColor(-1);
            commandField.setMaxLength(32500);
            commandField.setText(command);
            commandField.setEditableColor(-1);
            colorField.setMaxLength(50);
            colorField.setText(color);
            colorField.setEditableColor(-1);
            this.textFields[i] = textField; // 保存文本框对象到数组中
            this.commandField[i] = commandField;
            this.colorFields[i] = colorField;

            this.addSelectableChild(this.textFields[i]); // 添加文本框对象到GUI中
            this.addSelectableChild(this.colorFields[i]); // 添加文本框对象到GUI中
            this.addSelectableChild(this.commandField[i]); // 添加文本框对象到GUI中

        }

        confirmButton = ButtonWidget.builder(Text.translatable("gui.ok"), button -> {
            // 确认按钮的点击事件，发送数据包给服务器，更新告示牌的文本和命令
            BlockPos pos = sign.getPos();

            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeBlockPos(pos);
            for (int i = 0; i < 4; ++i) {
                // 获取文本框中输入的内容，并解析颜色代码（如果有的话）
                String text = textFields[i].getText().replaceAll("&&", "\uff06").replaceAll("&", "§").replaceAll("＆",
                        "&");
                String ColorName = colorFields[i].getText();
                if (ColorName == null || ColorName == "")
                    ColorName = "black";
                String cmd = commandField[i].getText();
                buf.writeString(text);
                buf.writeString(ColorName);
                buf.writeString(cmd);
            }
            buf.writeBoolean(signguiClient.textIsFront);
            ClientPlayNetworking.send(new signEditPayload(buf));
            // 关闭 GUI & 修改文本
            this.close();
        }).position(this.width / 2 + 4, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();

        cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> {
            // 取消按钮的点击事件，关闭 GUI
            this.close();
        }).position(this.width / 2 + 108, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();
        changeSideButton = ButtonWidget.builder(Text.translatable("gui.wifi.signgui.button.changeside",
                Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "back" : "front"))), button -> {
                    // 取消按钮的点击事件，关闭 GUI
                    signguiClient.textIsFront = !signguiClient.textIsFront;
                    this.titleDisplayer = Text.translatable("gui.wifi.signgui.title",
                            Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "front" : "back")));
                    this.changeSideButton.setMessage(Text.translatable("gui.wifi.signgui.button.changeside",
                            Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "back" : "front"))));
                }).position(this.width / 2 - 208, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();
        reloadButton = ButtonWidget.builder(Text.translatable("gui.wifi.signgui.button.reload"), button -> {
            // 重载文本
            SignText lsignText = sign.getText(signguiClient.textIsFront);
            for (int i = 0; i < 4; ++i) {
                // 获取告示牌的文本内容
                MutableText line = (MutableText) lsignText.getMessage(i, false);
                String text = line.getString().replaceAll("&", "\uff06").replaceAll("§", "&");
                String command = "";

                Style textStyle = line.getStyle();
                if (textStyle != null) {
                    ClickEvent clickEvent = textStyle.getClickEvent();
                    if (clickEvent != null) {
                        if (clickEvent.getAction().equals(Action.RUN_COMMAND))
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
                        color = ChatColor.getName();
                    } catch (Exception e) {
                        color = "black";
                        e.printStackTrace();
                    }
                }
                // 创建一个文本框对象，并设置其位置、大小、最大长度等属性
                textFields[i].setText(text);
                colorFields[i].setText(color);
                commandField[i].setText(command);

            }
        }).position(this.width / 2 - 104, 4 * LineHeight + FiledStartPos + 8).size(100, 20).build();
        this.addDrawableChild(confirmButton); // 添加确认按钮对象到GUI中
        this.addDrawableChild(cancelButton); // 添加取消按钮对象到GUI中
        this.addDrawableChild(changeSideButton); // 添加切换方向按钮对象到GUI中
        this.addDrawableChild(reloadButton); // 添加重新加载按钮对象到GUI中
        // this.addSelectableChild(commandField); // 添加文本框对象到GUI中
        this.setInitialFocus(this.textFields[0]); // 设置初始焦点为第一个文本框

    }

    @Override
    public void removed() {
        super.removed();
    }

    private void drawCenteredTextWithShadow(DrawContext matrices, TextRenderer textRenderer, Text text, int x, int y,
            int color, boolean shadow) {
        matrices.drawText(textRenderer, text, x, y, color, shadow);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.renderInGameBackground(context);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);

    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String[] commands = new String[4], colors = new String[4], texts = new String[4];
        for (int i = 0; i < 4; i++) {
            commands[i] = this.commandField[i].getText();
            colors[i] = this.colorFields[i].getText();
            texts[i] = this.textFields[i].getText();
        }
        this.init(client, width, height);
        for (int i = 0; i < 4; i++) {
            this.commandField[i].setText(commands[i]);
            this.colorFields[i].setText(colors[i]);
            this.textFields[i].setText(texts[i]);
        }
    }

    @Override
    protected Text getUsageNarrationText() {
        return super.getUsageNarrationText();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // this.renderBackground(context, mouseX, mouseY, delta); // 渲染背景
        // 先渲染其他元素
        super.render(context, mouseX, mouseY, deltaTicks);
        drawCenteredTextWithShadow(context, this.textRenderer, this.titleDisplayer, this.width / 2 - 180, titleTop,
                -1,
                true); // 渲染标题0xAARRGGBB
        drawCenteredTextWithShadow(context, this.textRenderer, Text.translatable("gui.wifi.signgui.tip"),
                this.width / 2 - 180, tipTop, -1, true);
        for (int i = 0; i < 4; ++i) {
            // 20 + i * 48
            drawCenteredTextWithShadow(context, this.textRenderer,
                    Text.translatable("gui.wifi.signgui.signtext", i + 1), this.width / 2 - 180,
                    TextTipStartPos + i * LineHeight,
                    -1, true); // 渲染文本标签
            drawCenteredTextWithShadow(context, this.textRenderer,
                    Text.translatable("gui.wifi.signgui.signcmd", i + 1), this.width / 2 - 180,
                    CommandTipStartPos + i * LineHeight,
                    -1, true); // 渲染命令标签
            this.textFields[i].render(context, mouseX, mouseY, deltaTicks);
            this.colorFields[i].render(context, mouseX, mouseY, deltaTicks);
            this.commandField[i].render(context, mouseX, mouseY, deltaTicks);

        }
    }
}