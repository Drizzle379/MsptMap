package msptmap.net;

import msptmap.MsptMapMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求开一次扫描。
 *
 * 只带一个秒数，由客户端决定（设置界面，范围 1 ~ 60）。0 = 用服务端默认值
 * （{@code MsptMapSettings.seconds}）：该分支服务端仍支持，自己的客户端已不再发送；
 * 服务端实际采用的秒数由结果包的 START 状态带回。
 *
 * 版本一致性有两道闸：包 ID 带协议后缀（两端协议不同则 Fabric 视为「不认识的包」，
 * 发送方的 canSend 为假、一个字节都不发出），以及包体开头的魔数（防同名不同格式的包解出乱码）。
 */
public record ScanRequestPayload(int protocol, int seconds) implements CustomPacketPayload {
	/** 魔数对不上时的占位值：服务端见此即忽略该请求。 */
	public static final int MISMATCH = -1;

	/**
	 * 包 ID。用 {@code Identifier.fromNamespaceAndPath}，不用
	 * {@code CustomPacketPayload.createType(String)}：后者只吃路径段（带冒号即抛异常），
	 * {@code minecraft:} 前缀由它内部补上。后缀与 {@link MsptMapMod#PROTOCOL} 同进同退。
	 */
	public static final Type<ScanRequestPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(MsptMapMod.MOD_ID, "scan_request" + MsptMapMod.CHANNEL_SUFFIX));

	public static final StreamCodec<FriendlyByteBuf, ScanRequestPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(MsptMapMod.PROTOCOL);
				buf.writeVarInt(payload.seconds());
			},
			buf -> {
				if (buf.readVarInt() != MsptMapMod.PROTOCOL) {
					// 余下的按本端格式解会读出乱码，直接跳过
					buf.skipBytes(buf.readableBytes());
					return new ScanRequestPayload(MISMATCH, 0);
				}
				return new ScanRequestPayload(MsptMapMod.PROTOCOL, buf.readVarInt());
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
