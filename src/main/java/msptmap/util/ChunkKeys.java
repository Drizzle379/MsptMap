package msptmap.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * 区块坐标打包成 long。
 *
 * <p>26.1 起 {@code ChunkPos} 的 {@code asLong}/{@code toLong} 更名为 {@code pack}（类改为 record），
 * 两代名称互不相同，而调用点分布在计时与采样，故统一由本类收口，条件编译只写在这一处。
 */
public final class ChunkKeys {
	private ChunkKeys() {
	}

	public static long pack(ChunkPos pos) {
		//? if >=26.1 {
		return pos.pack();
		//?} else {
		/*return pos.toLong();
		*///?}
	}

	public static long pack(int x, int z) {
		//? if >=26.1 {
		return ChunkPos.pack(x, z);
		//?} else {
		/*return ChunkPos.asLong(x, z);
		*///?}
	}

	public static long pack(BlockPos pos) {
		//? if >=26.1 {
		return ChunkPos.pack(pos);
		//?} else {
		/*return ChunkPos.asLong(pos);
		*///?}
	}
}
