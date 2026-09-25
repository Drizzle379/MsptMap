package msptmap;

import net.minecraft.commands.CommandSourceStack;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * 门面：核心代码只认这个类，不依赖地毯（地毯在 carpet/ 包里改写这里的字段）。
 */
public final class MsptMapSettings {
	/** 未指定秒数时的默认值（客户端请求的秒数由客户端决定）。 */
	public static IntSupplier seconds = () -> 5;

	/** 权限判定：地图按钮与服务端命令共用。没装地毯时恒为可用。 */
	public static Predicate<CommandSourceStack> canUse = source -> true;

	private MsptMapSettings() {
	}
}
