package io.wifi.signgui;
// MyMod.java

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent.Action;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;

public class signgui implements ModInitializer {
    // 定义一个键绑定
    private static final KeyBinding keyBinding = new KeyBinding("key.signeditorgui.open_gui", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "category.signeditorgui");
    // 定义一个数据包标识符，用于更新告示牌的文本和命令
    public static final Identifier UPDATE_SIGN_PACKET_ID = new Identifier("signeditorgui", "update_sign");

    @Override
    public void onInitialize() {
        // 注册键绑定
        KeyBindingHelper.registerKeyBinding(keyBinding);
        // 注册客户端初始化事件
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_SIGN_PACKET_ID,
                (MinecraftServer, client, ServerPlayNetworkHandler, packetByteBuf, PacketSender) -> {
                    Style style = Text.literal("").getStyle();
                    if (!client.hasPermissionLevel(2)) {
                        style.withColor((TextColor.fromFormatting(Formatting.RED)));
                        client.sendMessage(Text.translatable("msg.signgui.not_op").setStyle(style));
                        return;
                    }
                    PacketByteBuf bufCache = packetByteBuf;
                    BlockPos signPos = bufCache.readBlockPos();
                    ServerPlayerEntity player = (ServerPlayerEntity) client;
                    String[] cmdCache = new String[4];
                    String[] textCache = new String[4];
                    String[] colorCache = new String[4];
                    for (int i = 0; i < 4; ++i) {
                        textCache[i] = bufCache.readString();
                        colorCache[i] = bufCache.readString();
                        cmdCache[i] = bufCache.readString();
                    }
                    MinecraftServer.execute(() -> {
                        ServerWorldAccess world = (ServerWorldAccess) player.getWorld();
                        // 获取方块状态和方块实体
                        BlockEntity be = world.getBlockEntity(signPos);
                        BlockState signState = world.getBlockState(signPos);
                        // 检查方块是否是告示牌
                        if (signState.getBlock() instanceof SignBlock && be instanceof SignBlockEntity) {
                            SignBlockEntity sign = (SignBlockEntity) be;
                            for (int i = 0; i < 4; ++i) {
                                // 获取文本框中输入的内容，并解析颜色代码（如果有的话）
                                String text = textCache[i];
                                MutableText literalText = Text.literal(text);
                                String ColorName = colorCache[i];
                                if (ColorName == null || ColorName == "")
                                    ColorName = "black";
                                TextColor textColor = TextColor.fromFormatting(Formatting.byName(ColorName));

                                String cmd = cmdCache[i];
                                if (cmd != "") {
                                    ClickEvent clickEvent = new ClickEvent(Action.RUN_COMMAND, cmd);
                                    literalText
                                            .setStyle(literalText.getStyle().withColor(textColor).withClickEvent(
                                                    clickEvent));
                                } else {
                                    literalText.setStyle(literalText.getStyle().withColor(textColor));
                                }
                                sign.setTextOnRow(i, literalText); // 设置告示牌方块实体的文本内容
                            }
                            sign.markDirty();
                            // player.openEditSignScreen(sign);
                            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, sign.getPos()));
                            // world.updateNeighbors(signPos, signState.getBlock());
                            // world.syncWorldEvent(client, 0, signPos, 0);
                            // world.getChunk(signPos).markBlockForPostProcessing(signPos);
                            style.withColor((TextColor.fromFormatting(Formatting.GREEN)));
                            client.sendMessage(Text.translatable("msg.signgui.success").setStyle(style));
                        } else {
                            String out = (client == null ? "NULL" : client.getWorld()) + ":" + signPos.getX() + " "
                                    + signPos.getY() + " " + signPos.getZ();
                            style.withColor(TextColor.fromFormatting(Formatting.RED));
                            client.sendMessage(Text.translatable("msg.signgui.unexpected", out).setStyle(style));
                            return;
                        }
                    });
                });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查键盘是否按下

            while (keyBinding.wasPressed()) {
                // 获取玩家当前指向的方块
                Style style = Text.literal("").getStyle();
                style.withColor((TextColor.fromFormatting(Formatting.RED)));
                if (!client.player.hasPermissionLevel(2)) {

                    client.player.sendMessage(Text.translatable("msg.signgui.not_op").setStyle(style));
                    return;
                }
                HitResult hitResult = client.crosshairTarget;
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    BlockState blockState = client.world.getBlockState(blockPos);
                    // 检查方块是否是告示牌
                    if (blockState.getBlock() instanceof SignBlock) {
                        // 打开自定义 GUI
                        SignBlockEntity sign = (SignBlockEntity) client.world.getBlockEntity(blockPos);
                        // client.player.networkHandler.sendPacket(new SignEditorOpenC2SPacket(sign.getPos()));
                        client.setScreen(new MyGuiScreen(sign, client));
                    } else {
                        client.player.sendMessage(Text.translatable("msg.signgui.not_a_sign"));
                    }
                } else {
                    client.player.sendMessage(Text.translatable("msg.signgui.not_a_block"));
                }
            }
        });
    }
}
