package msptmap.client;

import msptmap.net.ChunkDetailResultPayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 最近一次区块详情。只保留一条，界面按维度与坐标匹配后才采用。
 *
 * <p>版本号供界面判断是否需要重绘：网络线程写完数据后递增，界面每帧比对一次。
 */
public final class ClientChunkDetail {
	/** 一份详情：请求的区块与两条列表。 */
	public record Data(String dimension, int chunkX, int chunkZ,
	                   List<ChunkDetailResultPayload.TicketInfo> tickets,
	                   List<ChunkDetailResultPayload.EntityInfo> entities) {
	}

	private static @Nullable Data current;
	private static long version;

	private ClientChunkDetail() {
	}

	public static void accept(ChunkDetailResultPayload payload) {
		current = new Data(payload.dimension(), payload.chunkX(), payload.chunkZ(),
				List.copyOf(payload.tickets()), List.copyOf(payload.entities()));
		version++;
	}

	/** 与请求的区块匹配的详情；没有或不匹配时为 null。 */
	public static @Nullable Data dataFor(String dimension, int chunkX, int chunkZ) {
		Data data = current;
		return data != null && data.dimension().equals(dimension)
				&& data.chunkX() == chunkX && data.chunkZ() == chunkZ ? data : null;
	}

	public static long version() {
		return version;
	}

	/** 掉线或重新扫描时丢弃：旧区块的详情不应留给新结果。 */
	public static void clear() {
		current = null;
		version++;
	}
}
