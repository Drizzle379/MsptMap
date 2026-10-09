package msptmap.net;

import msptmap.util.Ids;
import msptmap.MsptMapMod;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
*///?}

import java.util.List;

/**
 * 服务端 → 客户端：扫描的进展与结果。
 *
 * <p>六种状态共用这一个包，客户端因此只需一个接收器、状态判断只写一遍。除 DONE 外都不带区块数据
 * （空列表）；PROGRESS 复用 DONE 的 {@code windowTicks} 表示窗口已过的刻数，{@code tickNanos}
 * 亦仅 DONE 非 0。
 *
 * <p>版本不同的两端也允许互发：包 ID 不带版本号，包体开头的魔数是标记而非闸门。这个包字段较多，
 * 遇到读不动的格式才判 {@link #MISMATCH}，由客户端提示版本不一致。
 *
 * <p>1.20.4 及以前是 Fabric Loader 的 FabricPacket 体系（PacketType + write），1.20.5 起换成
 * 原版的 CustomPacketPayload（StreamCodec）；包体编解码共用，仅接口与注册方式分叉。
 */
public record ScanResultPayload(int protocol, Status status, int seconds, int windowTicks, long tickNanos,
                                List<SnapshotCodec.DimensionData> dimensions)
		//? if >=1.20.5 {
		implements CustomPacketPayload
		//?} else {
		/*implements FabricPacket
		*///?}
{

	/** 包体读不出内容时的占位值：客户端见此即提示版本不一致。 */
	public static final int MISMATCH = -1;

	/**
	 * 状态按 ordinal 上线：新增状态只能追加在末尾，插入中间会使版本不一致的另一端读错状态。
	 *
	 * <p>START = 窗口已开，秒数为服务端最终采用的秒数（客户端据此定进度圈的总刻数）；
	 * PROGRESS = 窗口已过的刻数（每 0.1 秒一次，客户端只画服务端报过的数）；DENIED / BUSY =
	 * 本次未扫描，客户端取消读条并在聊天栏提示；COOLDOWN = 冷却中拒绝（距上次扫描结束不足
	 * {@link msptmap.sampler.MsptSampler#COOLDOWN_SECONDS} 秒）。
	 */
	public enum Status {
		START,
		DONE,
		DENIED,
		BUSY,
		PROGRESS,
		COOLDOWN
	}

	/**
	 * 包 ID。用 {@link Ids#of} 构造资源位置，不用 {@code CustomPacketPayload.createType(String)}：
	 * 后者只接受路径段（带冒号即抛异常），{@code minecraft:} 前缀由其内部补上。不带版本号：两端
	 * 版本不同也应能互相送达，能否读取由包体的魔数判定。
	 */
	//? if >=1.20.5 {
	public static final Type<ScanResultPayload> TYPE = new Type<>(
			Ids.of(MsptMapMod.MOD_ID, "scan_result"));

	public static final StreamCodec<FriendlyByteBuf, ScanResultPayload> CODEC = StreamCodec.of(
			ScanResultPayload::encode, ScanResultPayload::decode);
	//?} else {
	/*public static final PacketType<ScanResultPayload> TYPE = PacketType.create(
			Ids.of(MsptMapMod.MOD_ID, "scan_result"), ScanResultPayload::decode);
	*///?}

	/** 写包体（CODEC / PacketType 共用）。 */
	public static void encode(FriendlyByteBuf buf, ScanResultPayload payload) {
		buf.writeVarInt(MsptMapMod.PROTOCOL);
		buf.writeVarInt(payload.status().ordinal());
		buf.writeVarInt(payload.seconds());
		buf.writeVarInt(payload.windowTicks());
		buf.writeVarLong(payload.tickNanos());
		SnapshotCodec.writeDimensions(buf, payload.dimensions());
	}

	/** 读包体；读不动时返回 MISMATCH 哨兵（见下）。 */
	public static ScanResultPayload decode(FriendlyByteBuf buf) {
		try {
			// 魔数只当标记：是别的值也照读，读得出来就照常出结果
			int peer = buf.readVarInt();
			ScanResultPayload result = new ScanResultPayload(
					peer,
					Status.values()[buf.readVarInt()],
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarLong(),
					SnapshotCodec.readDimensions(buf));
			// 对面版本若在尾部多带字段：不解析，直接丢弃。出口处缓冲必须读干净——
			// PacketDecoder 见到解码后仍有剩余字节即报 IOException 断线
			buf.skipBytes(buf.readableBytes());
			return result;
		} catch (Exception e) {
			// 对面格式与本端差异过大，读到一半即失败。异常冒到网络层会踢出玩家，故在此捕获并
			// 返回 MISMATCH 交由客户端提示；状态用 START 占位（客户端只看 protocol 字段）。
			// 残余字节同样要跳过，否则上层照样断线
			buf.skipBytes(buf.readableBytes());
			return new ScanResultPayload(MISMATCH, Status.START, 0, 0, 0L, List.of());
		}
	}

	//? if <1.20.5 {
	/*@Override
	public void write(FriendlyByteBuf buf) {
		encode(buf, this);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
	*///?}
	//? if >=1.20.5 {
	@Override
	public Type<ScanResultPayload> type() {
		return TYPE;
	}
	//?}

	public static ScanResultPayload start(int seconds) {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.START, seconds, 0, 0L, List.of());
	}

	/** 完成：{@code tickNanos} 为窗口内各 tick 耗时之和（纳秒），供总览算「区块合计占整 tick」的百分比。 */
	public static ScanResultPayload done(int seconds, int windowTicks, long tickNanos,
			List<SnapshotCodec.DimensionData> dimensions) {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.DONE, seconds, windowTicks, tickNanos, dimensions);
	}

	/** 进度：窗口已过的刻数。客户端进度圈按 {@code windowTicks / (秒数 × 20)} 绘制。 */
	public static ScanResultPayload progress(int seconds, int windowTicks) {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.PROGRESS, seconds, windowTicks, 0L, List.of());
	}

	/** 没权限：由 MsptMapMod 收包那道闸发。 */
	public static ScanResultPayload denied() {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.DENIED, 0, 0, 0L, List.of());
	}

	public static ScanResultPayload busy() {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.BUSY, 0, 0, 0L, List.of());
	}

	/** 冷却中：由 {@link msptmap.sampler.MsptSampler} 的冷却闸发。 */
	public static ScanResultPayload cooldown() {
		return new ScanResultPayload(MsptMapMod.PROTOCOL, Status.COOLDOWN, 0, 0, 0L, List.of());
	}
}
