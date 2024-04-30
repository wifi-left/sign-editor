package io.wifi.signgui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
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
import net.minecraft.util.math.BlockPos;

public class MyGuiScreen extends Screen {

    // 创建一个文本框数组，用来编辑告示牌的每一行文本
    private final TextFieldWidget[] textFields = new TextFieldWidget[4];
    private final TextFieldWidget[] colorFields = new TextFieldWidget[4];
    private final TextFieldWidget[] commandField = new TextFieldWidget[4];
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
        // 800 x 720
        /*
         * TextFieldWidget textField = new TextFieldWidget(this.textRenderer, this.width
         * / 2 - 80, 44 + i * 40, 186,
         * 16, Text.translatable("gui.wifi.signgui.signtext"));
         * TextFieldWidget colorField = new TextFieldWidget(this.textRenderer,
         * this.width / 2 + 110, 44 + i * 40, 50,
         * 16, Text.translatable("gui.wifi.signgui.signtext"));
         * TextFieldWidget commandField = new TextFieldWidget(this.textRenderer,
         * this.width / 2 - 80, 64 + i * 40,
         * 240, 16, Text.translatable("gui.wifi.signgui.signcmd"));
         */
        // System.out.print(this.width + "x" + this.height);
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

    public MyGuiScreen(SignBlockEntity sign) {
        super(Text.translatable("gui.wifi.signgui.title",
                Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "front" : "back"))));
        this.sign = sign; // 保存告示牌方块实体对象
        this.titleDisplayer = Text.translatable("gui.wifi.signgui.title",
                Text.translatable("gui.wifi.signgui." + (signguiClient.textIsFront ? "front" : "back")));
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
                    command = clickEvent.getValue();
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
            textField.setEditableColor(0xFFFFFF);
            commandField.setMaxLength(32500);
            commandField.setText(command);
            commandField.setEditableColor(0xFFFFFF);
            colorField.setMaxLength(50);
            colorField.setText(color);
            colorField.setEditableColor(0xFFFFFF);
            this.textFields[i] = textField; // 保存文本框对象到数组中
            this.commandField[i] = commandField;
            this.colorFields[i] = colorField;

            this.addDrawableChild(this.textFields[i]); // 添加文本框对象到GUI中
            this.addDrawableChild(this.colorFields[i]); // 添加文本框对象到GUI中
            this.addDrawableChild(this.commandField[i]); // 添加文本框对象到GUI中

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
                        command = clickEvent.getValue();
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
                ;
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
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices, mouseY, mouseY, delta); // 渲染背景
        // this.dra(this.title, this.width / 2, 20, 0xFFFFFFFF, false, , null, null,
        // 0x00FFFFFF, 0x00FFFFFF);

        drawCenteredTextWithShadow(matrices, this.textRenderer, this.titleDisplayer, this.width / 2 - 180, titleTop,
                0xffffff,
                true); // 渲染标题0xAARRGGBB
        drawCenteredTextWithShadow(matrices, this.textRenderer, Text.translatable("gui.wifi.signgui.tip"),
                this.width / 2 - 180, tipTop, 0xffffff, true);
        for (int i = 0; i < 4; ++i) {
            // 20 + i * 48
            drawCenteredTextWithShadow(matrices, this.textRenderer,
                    Text.translatable("gui.wifi.signgui.signtext", i + 1), this.width / 2 - 180,
                    TextTipStartPos + i * LineHeight,
                    0xffffff, true); // 渲染文本标签
            drawCenteredTextWithShadow(matrices, this.textRenderer,
                    Text.translatable("gui.wifi.signgui.signcmd", i + 1), this.width / 2 - 180,
                    CommandTipStartPos + i * LineHeight,
                    0xffffff, true); // 渲染命令标签

        }
        super.render(matrices, mouseX, mouseY, delta); // 渲染其他元素

    }

}