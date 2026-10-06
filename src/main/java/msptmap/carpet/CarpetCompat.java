package msptmap.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.RuleCategory;
import carpet.settings.Rule;
import carpet.utils.CommandHelper;
import carpet.utils.Translations;
import msptmap.MsptMapMod;
import msptmap.MsptMapSettings;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 地毯兼容层：MsptMap 的使用权限由地毯规则决定。
 *
 * <p>本类仅在装了地毯时被加载，调用点为 {@code MsptMapMod} 中的 isModLoaded 守卫。
 */
// carpet.settings 已被地毯标记为待移除，但 CarpetServer.settingsManager 仍是旧版类型（地毯源码注明
// 出于二进制兼容不能更改），故继续使用旧注解。desc 写英文：地毯将其注册为回退文案，未提供翻译的
// 语言下显示它；中文经 canHasTranslations 提供（服务端执行 /carpet language zh_cn 后生效）。
@SuppressWarnings("removal")
public final class CarpetCompat implements CarpetExtension {
	/** 谁能点地图按钮、谁能用 /msptmap 命令；分类为 command，地毯会自动接上取值校验（true/false/ops/0~4）。 */
	@Rule(desc = "Who may use MsptMap scans (the map button and the /msptmap command)", category = {RuleCategory.COMMAND})
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

	/**
	 * 规则说明的翻译。地毯在语言切换时调用：返回的键值对经 {@code putIfAbsent} 并入当前语言表；
	 * 键取 {@code carpet.rule.<字段名>.name/.desc}（格式见地毯的 {@code TranslationKeys}）。
	 *
	 * <p>只取 {@code carpet.} 前缀的键：其余键（聊天、界面文案）属于原版语言系统，混入会触发地毯的
	 * 旧格式迁移警告。读取失败则回退为注解里的英文 desc，不影响规则本身。
	 */
	@Override
	public Map<String, String> canHasTranslations(String lang) {
		try {
			Map<String, String> all = Translations.getTranslationFromResourcePath(
					"assets/" + MsptMapMod.MOD_ID + "/lang/" + lang + ".json");
			Map<String, String> carpetKeys = new HashMap<>();
			for (Map.Entry<String, String> entry : all.entrySet()) {
				if (entry.getKey().startsWith("carpet.")) {
					carpetKeys.put(entry.getKey(), entry.getValue());
				}
			}
			return carpetKeys;
		} catch (LinkageError | RuntimeException e) {
			MsptMapMod.LOGGER.warn("地毯规则翻译读取失败（{}），回退为注解中的英文说明", lang, e);
			return Collections.emptyMap();
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
