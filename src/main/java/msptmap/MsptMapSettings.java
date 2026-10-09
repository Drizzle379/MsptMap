package msptmap;

import net.minecraft.commands.CommandSourceStack;
//? if >=1.21.11 {
import net.minecraft.commands.Commands;
//?}

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * 服务端设置门面：核心代码只依赖本类，不直接依赖地毯（地毯在 carpet/ 包中改写这里的字段）。
 */
public final class MsptMapSettings {
	/** 未指定秒数时的默认值（客户端请求的秒数由客户端决定）。 */
	public static IntSupplier seconds = () -> 2;

	/** 权限判定：地图按钮与服务端命令共用。没装地毯时恒为可用。 */
	public static Predicate<CommandSourceStack> canUse = source -> true;

	/**
	 * 是否原版 OP（管理等级 2）：常态监控是服主的管理项，必须在不装地毯的服务器上也判定得了，
	 * 故与 {@link #canUse}（可被地毯规则改写）分开。
	 *
	 * <p>1.21.11 起原版权限改为 {@code PermissionSet} + {@code PermissionCheck}，等级常量随之从
	 * {@code hasPermission(int)} 换成 {@code Commands.LEVEL_GAMEMASTERS}。
	 */
	public static Predicate<CommandSourceStack> isOperator = source -> {
		//? if >=1.21.11 {
		return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
		//?} else {
		/*return source.hasPermission(2);
		*///?}
	};

	private MsptMapSettings() {
	}
}
