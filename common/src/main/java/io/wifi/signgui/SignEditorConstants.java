package io.wifi.signgui;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignEditorConstants {
    public static final String helloVersion = "1.0.2";
    public static final Logger LOGGER = LoggerFactory.getLogger("SignEditor");
    public static final Permission perm_2 = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);
}
