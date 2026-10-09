package msptmap.net;

import msptmap.Ids;
import msptmap.MsptMapMod;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
*///?}

/**
 * 客户端 → 服务端：请求开一次扫描。
 *
 * <p>只带一个秒数，由客户端决定（设置界面，范围 1~60）。0 表示用服务端默认值
 * （{@code MsptSampler.DEFAULT_SECONDS}）；服务端实际采用的秒数由结果包的 START 状态带回。
 *
 * <p>版本不同的两端也允许互发：包 ID 不带版本号，包体开头的魔数是标记而非闸门——收到别的值照读，
 * 能读出来即照常处理，由接收方自行决定如何提示。
 *
 * <p>1.20.4 及以前是 Fabric Loader 的 FabricPacket 体系（PacketType + write），1.20.5 起换成
 * 原版的 CustomPacketPayload（StreamCodec）；包体编解码共用，仅接口与注册方式分叉。
 */
public record ScanRequestPayload(int protocol, int seconds)
		//? if >=1.20.5 {
		implements CustomPacketPayload
		//?} else {
		/*implements FabricPacket
		*///?}
{
	/** 包体读不出内容时的占位值：服务端见此即忽略该请求。 */
	public static final int MISMATCH = -1;

	/**
	 * 包 ID。用 {@link Ids#of} 构造资源位置，不用 {@code CustomPacketPayload.createType(String)}：
	 * 后者只接受路径段（带冒号即抛异常），{@code minecraft:} 前缀由其内部补上。不带版本号：两端
	 * 版本不同也应能互相送达，能否读取由包体的魔数判定。
	 */
	//? if >=1.20.5 {
	public static final Type<ScanRequestPayload> TYPE = new Type<>(
			Ids.of(MsptMapMod.MOD_ID, "scan_request"));

	public static final StreamCodec<FriendlyByteBuf, ScanRequestPayload> CODEC = StreamCodec.of(
			ScanRequestPayload::encode, ScanRequestPayload::decode);
	//?} else {
	/*public static final PacketType<ScanRequestPayload> TYPE = PacketType.create(
			Ids.of(MsptMapMod.MOD_ID, "scan_request"), ScanRequestPayload::decode);
	*///?}

	/** 写包体（CODEC / PacketType 共用）。 */
	public static void encode(FriendlyByteBuf buf, ScanRequestPayload payload) {
		buf.writeVarInt(MsptMapMod.PROTOCOL);
		buf.writeVarInt(payload.seconds());
	}

	/** 读包体；读不动时返回 MISMATCH 哨兵（见下）。 */
	public static ScanRequestPayload decode(FriendlyByteBuf buf) {
		try {
			// 魔数只当标记：是别的值也照读。请求包只有秒数一个字段，各版本一致；
			// 读得出来即按它执行，读不出来（对面格式差异过大）才判 MISMATCH
			int peer = buf.readVarInt();
			if (!buf.isReadable()) {
				return new ScanRequestPayload(MISMATCH, 0);
			}
			int seconds = buf.readVarInt();
			// 对面版本若在尾部多带字段：不解析，直接丢弃。出口处缓冲必须读干净——
			// PacketDecoder 见到解码后仍有剩余字节即报 IOException 断线
			buf.skipBytes(buf.readableBytes());
			return new ScanRequestPayload(peer, seconds);
		} catch (Exception e) {
			// 解码器抛出的异常会冒到网络层并把玩家踢下线，故在此捕获；残余字节同样要跳过，
			// 否则上层照样断线
			buf.skipBytes(buf.readableBytes());
			return new ScanRequestPayload(MISMATCH, 0);
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
	public Type<ScanRequestPayload> type() {
		return TYPE;
	}
	//?}

	/** 按本端协议构造一个请求。 */
	public static ScanRequestPayload scan(int seconds) {
		return new ScanRequestPayload(MsptMapMod.PROTOCOL, seconds);
	}
}
