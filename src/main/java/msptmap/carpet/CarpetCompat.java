package msptmap.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.RuleCategory;
import carpet.settings.Rule;
import carpet.utils.CommandHelper;
import msptmap.MsptMapMod;
import msptmap.MsptMapSettings;
import net.minecraft.commands.CommandSourceStack;

/**
 * 地毯兼容层：MsptMap 的使用权限由地毯规则决定。
 *
 * <p>本类仅在装了地毯时被加载，调用点为 {@code MsptMapMod} 中的 isModLoaded 守卫。
 */
// 地毯已将 carpet.settings 标为待删除，但新版 API 没有 desc 字段，改用新版会导致 /carpet 列表中的
// 说明消失（需另配语言文件）；地毯自身的规则也仍在使用这一套。
@SuppressWarnings("removal")
public final class CarpetCompat implements CarpetExtension {
	/** 谁能点地图按钮、谁能用 /msptmap 命令；分类为 command，地毯会自动接上取值校验（true/false/ops/0~4）。 */
	@Rule(desc = "谁能用 MsptMap 的扫描（地图按钮和 /msptmap 命令）", category = {RuleCategory.COMMAND})
	public static String commandMsptMap = "ops";

	private CarpetCompat() {
	}

	/** 注册扩展。调用点为 {@code MsptMapMod} 中的 isModLoaded 守卫。 */
	public static void register() {
		CarpetServer.manageExtension(new CarpetCompat());
	}

	@Override
	public void onGameStarted() {
		try {
			// 注册进地毯规则表：/carpet 列表可见，可用 /carpet commandMsptMap 修改
			CarpetServer.settingsManager.parseSettingsClass(CarpetCompat.class);
			// 改写门面：每次判定现读规则值，改完立即生效（方法引用不在赋值处解析 CommandHelper）
			MsptMapSettings.canUse = CarpetCompat::canUse;
		} catch (LinkageError | RuntimeException e) {
			// 地毯移除 carpet.settings 后规则注册会解析不到字段或注解。此处降级而非崩溃：
			// 规则不注册则 canUse 保持默认值，谁都能用，日志中说明原因。
			MsptMapMod.LOGGER.warn("地毯不含 carpet.settings，权限规则未注册，MsptMap 对所有人开放", e);
		}
	}

	/** 权限判定一旦整体降级（地毯缺 carpet.settings），之后一律放行，不再重试。 */
	private static boolean degraded;

	/**
	 * 权限判定。地毯缺 {@code carpet.settings} 时，{@code CommandHelper} 会在第一次调用时抛出
	 * {@link LinkageError}，此时已离开 onGameStarted 的 try；故在此处捕获，出错一次即降级为放行。
	 */
	private static boolean canUse(CommandSourceStack source) {
		if (degraded) {
			return true;
		}
		try {
			return CommandHelper.canUseCommand(source, commandMsptMap);
		} catch (LinkageError e) {
			degraded = true;
			MsptMapMod.LOGGER.warn("地毯不含 carpet.settings，权限判定降级为对所有人开放", e);
			return true;
		}
	}
}
