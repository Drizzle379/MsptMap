package msptmap;

import net.minecraft.commands.CommandSourceStack;
//? if >=1.21.11 {
import net.minecraft.commands.Commands;
//?}

/**
 * 使用权限判定：默认仅原版 OP（管理等级 2），服主可用 {@code /msptmap access all} 放开给所有玩家。
 *
 * <p>扫描命令、地图按钮发出的请求与常态监控的管理项共用这里的判定。
 */
public final class Permissions {
	private Permissions() {
	}

	/** 能否发起扫描：{@link ServerConfig.Access#ALL} 时人人可用，否则限 OP。 */
	public static boolean canUse(CommandSourceStack source) {
		return ServerConfig.access == ServerConfig.Access.ALL || isOperator(source);
	}

	/**
	 * 是否原版 OP（管理等级 2）。
	 *
	 * <p>1.21.11 起原版权限改为 {@code PermissionSet} 与 {@code PermissionCheck}，等级常量随之由
	 * {@code hasPermission(int)} 改为 {@code Commands.LEVEL_GAMEMASTERS}。
	 */
	public static boolean isOperator(CommandSourceStack source) {
		//? if >=1.21.11 {
		return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
		//?} else {
		/*return source.hasPermission(2);
		*///?}
	}
}
