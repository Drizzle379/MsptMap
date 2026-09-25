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
 *
 * 版本一致性有两道闸：包 ID 带协议后缀（两端协议不同则 Fabric 视为「不认识的包」，
 * 发送方的 canSend 为假、一个字节都不发出），以及包体开头的魔数（防同名不同格式的包解出乱码）。
 */
public record ScanResultPayload(int protocol, Status status, int seconds, int windowTicks,
                                List<SnapshotCodec.DimensionData> dimensions) implements CustomPacketPayload {

	/** 魔数对不上时的占位值：客户端见此即丢弃该包并提示版本不一致。 */
	public static final int MISMATCH = -1;

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

	/**
	 * 包 ID。用 {@code Identifier.fromNamespaceAndPath}，不用
	 * {@code CustomPacketPayload.createType(String)}：后者只吃路径段（带冒号即抛异常），
	 * {@code minecraft:} 前缀由它内部补上。后缀与 {@link MsptMapMod#PROTOCOL} 同进同退。
	 */
	public static final Type<ScanResultPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(MsptMapMod.MOD_ID, "scan_result" + MsptMapMod.CHANNEL_SUFFIX));

	public static final StreamCodec<FriendlyByteBuf, ScanResultPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(MsptMapMod.PROTOCOL);
				buf.writeVarInt(payload.status().ordinal());
				buf.writeVarInt(payload.seconds());
				buf.writeVarInt(payload.windowTicks());
				SnapshotCodec.DIMENSIONS.encode(buf, payload.dimensions());
			},
			buf -> {
				if (buf.readVarInt() != MsptMapMod.PROTOCOL) {
					// 余下的按本端格式解会读出乱码（状态码还会越界），直接跳过；状态用 START 占位，
					// 客户端只读 protocol 字段，见到 MISMATCH 即丢弃
					buf.skipBytes(buf.readableBytes());
					return new ScanResultPayload(MISMATCH, Status.START, 0, 0, List.of());
				}
				return new ScanResultPayload(
						MsptMapMod.PROTOCOL,
						Status.values()[buf.readVarInt()],
						buf.readVarInt(),
						buf.readVarInt(),
						SnapshotCodec.DIMENSIONS.decode(buf));
			});

	public static ScanResultPayload start(int seconds) {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.START, seconds, 0, List.of());
	}

	public static ScanResultPayload done(int seconds, int windowTicks, List<SnapshotCodec.DimensionData> dimensions) {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.DONE, seconds, windowTicks, dimensions);
	}

	/** 进度：窗口已过的刻数。客户端进度圈按 {@code windowTicks / (秒数 × 20)} 绘制。 */
	public static ScanResultPayload progress(int seconds, int windowTicks) {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.PROGRESS, seconds, windowTicks, List.of());
	}

	/** 没权限：由 MsptMapMod 收包那道闸发。 */
	public static ScanResultPayload denied() {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.DENIED, 0, 0, List.of());
	}

	public static ScanResultPayload busy() {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.BUSY, 0, 0, List.of());
	}

	@Override
	public Type<ScanResultPayload> type() {
		return TYPE;
	}
}
