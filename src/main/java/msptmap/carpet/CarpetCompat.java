package msptmap.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.RuleCategory;
import carpet.settings.Rule;
import carpet.utils.CommandHelper;
import msptmap.MsptMapSettings;

/**
 * 地毯兼容：谁能用 MsptMap 由地毯规则决定。
 *
 * 该类仅在装了地毯时被加载：调用点是 MsptMapMod 里那道 isModLoaded 守卫。
 */
// 地毯 26.2 已把 carpet.settings 标为待删除，但新版 api 没有 desc 字段，用它则 /carpet 列表中的
// 说明消失（需另配语言文件）。地毯自身的规则也仍在使用这一套。
@SuppressWarnings("removal")
public final class CarpetCompat implements CarpetExtension {
	/**
	 * 谁能点地图按钮、谁能用 /msptmap 命令。
	 *
	 * 分类为 command：地毯会为这一类规则自动接上取值校验（true / false / ops / 0~4）。
	 */
	@Rule(desc = "谁能用 MsptMap 的扫描（地图按钮和 /msptmap 命令）", category = {RuleCategory.COMMAND})
	public static String commandMsptMap = "ops";

	private CarpetCompat() {
	}

	/** 注册扩展。调用点只有 MsptMapMod 里那道 isModLoaded 守卫。 */
	public static void register() {
		CarpetServer.manageExtension(new CarpetCompat());
	}

	@Override
	public void onGameStarted() {
		// 规则注册进地毯规则表：/carpet 列表里可见、可用 /carpet commandMsptMap 修改
		CarpetServer.settingsManager.parseSettingsClass(CarpetCompat.class);
		// 改写门面：每次判定现读规则值，改完立即生效
		MsptMapSettings.canUse = source -> CommandHelper.canUseCommand(source, commandMsptMap);
	}
}
