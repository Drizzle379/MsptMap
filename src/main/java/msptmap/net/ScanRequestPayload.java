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
 */
public record ScanRequestPayload(int seconds) implements CustomPacketPayload {
	/**
	 * 包 ID。用 {@code Identifier.fromNamespaceAndPath}，不用
	 * {@code CustomPacketPayload.createType(String)}：后者只吃路径段（带冒号即抛异常），
	 * {@code minecraft:} 前缀由它内部补上。
	 */
	public static final Type<ScanRequestPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(MsptMapMod.MOD_ID, "scan_request"));

	public static final StreamCodec<FriendlyByteBuf, ScanRequestPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeVarInt(payload.seconds()),
			buf -> new ScanRequestPayload(buf.readVarInt()));

	@Override
	public Type<ScanRequestPayload> type() {
		return TYPE;
	}
}
