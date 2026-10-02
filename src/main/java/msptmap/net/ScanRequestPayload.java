package msptmap.net;

import msptmap.Ids;
import msptmap.MsptMapMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：请求开一次扫描。
 *
 * 只带一个秒数，由客户端决定（设置界面，范围 1 ~ 60）。0 = 用服务端默认值
 * （{@code MsptMapSettings.seconds}）：该分支服务端仍支持，自己的客户端已不再发送；
 * 服务端实际采用的秒数由结果包的 START 状态带回。
 *
 * 版本不同的两端也允许互发：包 ID 不带版本号，包体开头的魔数是**标记**而非闸门 ——
 * 收到别的值照读下去，能读出来就照常处理，由接收方自行决定怎么提示。
 */
public record ScanRequestPayload(int protocol, int seconds) implements CustomPacketPayload {
	/** 包体读不出内容时的占位值：服务端见此即忽略该请求。 */
	public static final int MISMATCH = -1;

	/**
	 * 包 ID。用 {@link Ids#of}（资源位置构造），不用
	 * {@code CustomPacketPayload.createType(String)}：后者只吃路径段（带冒号即抛异常），
	 * {@code minecraft:} 前缀由它内部补上。不带版本号：两端版本不同也应当能互相送达，
	 * 能不能读由包体的魔数判定。
	 */
	public static final Type<ScanRequestPayload> TYPE = new Type<>(
			Ids.of(MsptMapMod.MOD_ID, "scan_request"));

	public static final StreamCodec<FriendlyByteBuf, ScanRequestPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(MsptMapMod.PROTOCOL);
				buf.writeVarInt(payload.seconds());
			},
			buf -> {
				try {
					// 魔数只当标记：是别的值也照读。请求包只有秒数一个字段，各版本一致；
					// 读得出来就按它走，读不出来（对面格式差得太多）才判 MISMATCH
					int peer = buf.readVarInt();
					if (!buf.isReadable()) {
						return new ScanRequestPayload(MISMATCH, 0);
					}
					int seconds = buf.readVarInt();
					// 对面版本若在尾部多带字段：不解析、直接丢弃。出口处缓冲必须读干净——
					// PacketDecoder 见到解码后仍有剩余字节就报 IOException 断线（PLAN 坑 24）
					buf.skipBytes(buf.readableBytes());
					return new ScanRequestPayload(peer, seconds);
				} catch (Exception e) {
					// 解码器抛出的异常会一路冒到网络层、把玩家踢下线，这里兜住；残余字节同样
					// 要跳过，否则上层照样断线
					buf.skipBytes(buf.readableBytes());
					return new ScanRequestPayload(MISMATCH, 0);
				}
			});

	/** 按本端协议构造一个请求。 */
	public static ScanRequestPayload scan(int seconds) {
		return new ScanRequestPayload(MsptMapMod.PROTOCOL, seconds);
	}

	@Override
	public Type<ScanRequestPayload> type() {
		return TYPE;
	}
}
