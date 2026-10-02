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
 * 版本不同的两端也允许互发：包 ID 不带版本号，包体开头的魔数是**标记**而非闸门。
 * 这个包的字段多，遇到读不动的格式才判 {@link #MISMATCH}，由客户端提示版本不一致。
 */
public record ScanResultPayload(int protocol, Status status, int seconds, int windowTicks,
                                List<SnapshotCodec.DimensionData> dimensions) implements CustomPacketPayload {

	/** 包体读不出内容时的占位值：客户端见此即提示版本不一致。 */
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
	 * {@code minecraft:} 前缀由它内部补上。不带版本号：两端版本不同也应当能互相送达，
	 * 能不能读由包体的魔数判定。
	 */
	public static final Type<ScanResultPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(MsptMapMod.MOD_ID, "scan_result"));

	public static final StreamCodec<FriendlyByteBuf, ScanResultPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(MsptMapMod.PROTOCOL);
				buf.writeVarInt(payload.status().ordinal());
				buf.writeVarInt(payload.seconds());
				buf.writeVarInt(payload.windowTicks());
				SnapshotCodec.DIMENSIONS.encode(buf, payload.dimensions());
			},
			buf -> {
				try {
					// 魔数只当标记：是别的值也照读，读得出来就照常出结果
					int peer = buf.readVarInt();
					ScanResultPayload result = new ScanResultPayload(
							peer,
							Status.values()[buf.readVarInt()],
							buf.readVarInt(),
							buf.readVarInt(),
							SnapshotCodec.DIMENSIONS.decode(buf));
					// 对面版本若在尾部多带字段：不解析、直接丢弃。出口处缓冲必须读干净——
					// PacketDecoder 见到解码后仍有剩余字节就报 IOException 断线（PLAN 坑 24）
					buf.skipBytes(buf.readableBytes());
					return result;
				} catch (Exception e) {
					// 对面格式与本端差得多，读到一半就垮了。异常冒到网络层会踢人，这里兜住，
					// 换成 MISMATCH 交给客户端提示；状态用 START 占位（客户端只看 protocol 字段）。
					// 残余字节同样要跳过，否则上层照样断线
					buf.skipBytes(buf.readableBytes());
					return new ScanResultPayload(MISMATCH, Status.START, 0, 0, List.of());
				}
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
