package msptmap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * 区块坐标打包成 long。
 *
 * 26.1 起 ChunkPos 的 asLong / toLong 更名为 pack（类也改成了 record）；两代的名字互不相同，
 * 而调用点分散在计时、采样与网络层，故统一从这里走，条件注释只写在这一处。
 */
public final class ChunkKeys {
	private ChunkKeys() {
	}

	/** 区块坐标 → long。 */
	public static long pack(ChunkPos pos) {
		//? if >=26.1 {
		return pos.pack();
		//?} else {
		/*return pos.toLong();
		*///?}
	}

	/** 区块坐标（x, z）→ long。 */
	public static long pack(int x, int z) {
		//? if >=26.1 {
		return ChunkPos.pack(x, z);
		//?} else {
		/*return ChunkPos.asLong(x, z);
		*///?}
	}

	/** 方块坐标 → long。 */
	public static long pack(BlockPos pos) {
		//? if >=26.1 {
		return ChunkPos.pack(pos);
		//?} else {
		/*return ChunkPos.asLong(pos);
		*///?}
	}
}
