package io.wifi.signgui;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignEditorConstants {
    /**
     * Bumped when the wire format changes; 1.0.3 sends components instead of component JSON, 1.0.4
     * carries the sign's allow_op_features flag alongside them, and 1.0.5 carries its is_waxed flag.
     */
    public static final String helloVersion = "1.0.5";
    public static final Logger LOGGER = LoggerFactory.getLogger("SignEditor");
    public static final Permission perm_2 = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);
}
