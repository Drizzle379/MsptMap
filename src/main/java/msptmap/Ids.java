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
 * 资源位置工具：贴图 / 包 ID 的构造与维度 ID、注册表键路径的取用。
 *
 * <p>1.21.11 起 {@code ResourceLocation} 更名为 {@code Identifier}，{@code ResourceKey.location()}
 * 更名为 {@code identifier()}；调用点分散在服务端采样、客户端与网络层，故统一由本类收口。
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
	 * 用 {@code var} 接键对象：其类型名两代不同（Identifier / ResourceLocation），而 getPath() 同名。
	 */
	public static <T> String path(Registry<T> registry, T value) {
		var key = registry.getKey(value);
		return key == null ? null : key.getPath();
	}
}
