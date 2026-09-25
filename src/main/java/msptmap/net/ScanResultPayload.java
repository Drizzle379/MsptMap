package msptmap.net;

import msptmap.MsptMapMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 服务端 → 客户端：扫描的进展与结果。
 *
 * 五种状态共用这一个包，客户端因此只需一个接收器、状态判断只写一遍。
 * 除 DONE 外都不带区块数据（空列表）；PROGRESS 复用 DONE 的 {@code windowTicks} 表示窗口已过的刻数。
 */
public record ScanResultPayload(Status status, int seconds, int windowTicks,
                                List<SnapshotCodec.DimensionData> dimensions) implements CustomPacketPayload {

	/**
	 * START = 窗口已开，秒数为服务端最终采用的秒数（客户端据此定进度圈的总刻数）；
	 * PROGRESS = 窗口已过的刻数（每 0.1 秒一次，客户端只画服务端报过的数）；
	 * DENIED / BUSY = 本次未扫描，客户端取消读条并在聊天栏提示。
	 */
	public enum Status {
		START,
		DONE,
		DENIED,
		BUSY,
		/** 只能追加在末尾：状态按 ordinal 上线，插入中间会使版本不一致的另一端读错状态。 */
		PROGRESS
	}

	public static final Type<ScanResultPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(MsptMapMod.MOD_ID, "scan_result"));

	public static final StreamCodec<FriendlyByteBuf, ScanResultPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.status().ordinal());
				buf.writeVarInt(payload.seconds());
				buf.writeVarInt(payload.windowTicks());
				SnapshotCodec.DIMENSIONS.encode(buf, payload.dimensions());
			},
			buf -> new ScanResultPayload(
					Status.values()[buf.readVarInt()],
					buf.readVarInt(),
					buf.readVarInt(),
					SnapshotCodec.DIMENSIONS.decode(buf)));

	public static ScanResultPayload start(int seconds) {
		return new ScanResultPayload(Status.START, seconds, 0, List.of());
	}

	public static ScanResultPayload done(int seconds, int windowTicks, List<SnapshotCodec.DimensionData> dimensions) {
		return new ScanResultPayload(Status.DONE, seconds, windowTicks, dimensions);
	}

	/** 进度：窗口已过的刻数。客户端进度圈按 {@code windowTicks / (秒数 × 20)} 绘制。 */
	public static ScanResultPayload progress(int seconds, int windowTicks) {
		return new ScanResultPayload(Status.PROGRESS, seconds, windowTicks, List.of());
	}

	/** 没权限：由 MsptMapMod 收包那道闸发。 */
	public static ScanResultPayload denied() {
		return new ScanResultPayload(Status.DENIED, 0, 0, List.of());
	}

	public static ScanResultPayload busy() {
		return new ScanResultPayload(Status.BUSY, 0, 0, List.of());
	}

	@Override
	public Type<ScanResultPayload> type() {
		return TYPE;
	}
}
