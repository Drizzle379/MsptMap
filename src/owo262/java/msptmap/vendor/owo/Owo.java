package msptmap.vendor.owo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置 owo-ui 的常量与工具。原类的模组入口、网络与调试命令已随裁剪移除。
 */
public final class Owo {
    /** 内置源码自己的命名空间，与官方 owo-lib 完全隔离。 */
    public static final String MOD_ID = "msptmap_owo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final boolean DEBUG = Boolean.getBoolean("msptmap.owo.debug");
    public static final Component PREFIX = Component.literal("MsptMap UI > ").withStyle(ChatFormatting.GRAY);

    private Owo() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void debugWarn(Logger logger, String message) {
        if (DEBUG) {
            logger.warn(message);
        }
    }

    public static void debugWarn(Logger logger, String message, Object... params) {
        if (DEBUG) {
            logger.warn(message, params);
        }
    }
}
