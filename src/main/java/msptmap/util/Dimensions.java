package msptmap.util;

/**
 * 维度名的语言键：客户端的悬停详情、总览与服务端的自动扫描告警共用。
 *
 * <p>两个口径：本模组的短名（三个原版维度用中文「主世界 / 下界 / 末地」；原版英文
 * 「The Nether / The End」为两个词，此处改用单词 Nether / End）与英文短名回退。原版语言文件不含
 * {@code dimension.*} 译名键（各版本 zh_cn / en_us 均无），告警行不能只给原版键，否则未装本模组的
 * 玩家将看到裸键名，故以 {@link #fallbackName} 兜底。
 */
public final class Dimensions {
	private Dimensions() {
	}

	/**
	 * 本模组的语言键：三个原版维度用短名，其余回退原版键。短名仅在本模组已安装的客户端可渲染。
	 */
	public static String key(String dimensionId) {
		return switch (dimensionId) {
			case "minecraft:overworld" -> "msptmap.dimension.overworld";
			case "minecraft:the_nether" -> "msptmap.dimension.nether";
			case "minecraft:the_end" -> "msptmap.dimension.end";
			default -> vanillaKey(dimensionId);
		};
	}

	/**
	 * 告警中维度名的回退文案：三个原版维度给英文短名，其余给维度 ID。自定义维度的 {@code dimension.*}
	 * 键可由资源包或数据包提供（即原版键的用途），无法翻译时至少不显示裸键名。
	 */
	public static String fallbackName(String dimensionId) {
		return switch (dimensionId) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimensionId;
		};
	}

	/**
	 * 原版语言键 {@code dimension.<命名空间>.<路径>}（点号形式，同 {@code Util.makeDescriptionId}）：
	 * 任何客户端均可渲染。
	 */
	public static String vanillaKey(String dimensionId) {
		return "dimension." + dimensionId.replace(':', '.');
	}
}
