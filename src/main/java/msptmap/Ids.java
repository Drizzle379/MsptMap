package msptmap;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * 资源位置相关的小工具：贴图 / 包 ID 的构造、维度 ID 的取用。
 *
 * 1.21.11 起 ResourceLocation 更名为 Identifier，ResourceKey 的 location() 同时更名为 identifier()；
 * 调用点分散在客户端与网络层，故统一从这里走，条件注释只写在这一处。
 */
public final class Ids {
	private Ids() {
	}

	/** 构造资源位置（贴图、包 ID 用）。 */
	//? if >=1.21.11 {
	public static Identifier of(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}
	//?} else if >=1.21 {
	/*public static ResourceLocation of(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}
	*///?} else {
	/*public static ResourceLocation of(String namespace, String path) {
		return new ResourceLocation(namespace, path);
	}
	*///?}

	/** 维度 ID 的全名（如 {@code minecraft:overworld}）。客户端快照按它分组存储。 */
	public static String id(ResourceKey<Level> dimension) {
		//? if >=1.21.11 {
		return dimension.identifier().toString();
		//?} else {
		/*return dimension.location().toString();
		*///?}
	}

	/**
	 * 注册表键的路径段（如 {@code player_loading}）；该值不在注册表里时为 null。
	 * 用 var 接键对象：它的类型名两代不同（Identifier / ResourceLocation），getPath() 两代同名。
	 */
	public static <T> String path(Registry<T> registry, T value) {
		var key = registry.getKey(value);
		return key == null ? null : key.getPath();
	}
}
