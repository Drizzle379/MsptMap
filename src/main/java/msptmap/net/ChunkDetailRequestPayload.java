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

/**
 * 客户端 → 服务端：请求某个区块的详细信息（覆盖它的全部加载票、当前实体组成）。
 *
 * <p>只在综合分析界面选中区块时发送；服务端未升级到带本包的版本时，客户端根本不会发出
 * （见 {@code ClientPlayNetworking.canSend}）。
 *
 * <p>1.20.4 及以前是 Fabric Loader 的 FabricPacket 体系（PacketType + write），1.20.5 起换成
 * 原版的 CustomPacketPayload（StreamCodec）；包体编解码共用，仅接口与注册方式分叉。
 */
public record ChunkDetailRequestPayload(int protocol, String dimension, int chunkX, int chunkZ)
		//? if >=1.20.5 {
		implements CustomPacketPayload
		//?} else {
		/*implements FabricPacket
		*///?}
{
	/** 包体无法读取时的占位值：服务端见此即忽略该请求。 */
	public static final int MISMATCH = -1;

	/**
	 * 包 ID。用 {@link Ids#of} 构造资源位置，不用 {@code CustomPacketPayload.createType(String)}：
	 * 后者只接受路径段（带冒号即抛异常）。不带版本号，能否读取由包体魔数判定。
	 */
	//? if >=1.20.5 {
	public static final Type<ChunkDetailRequestPayload> TYPE = new Type<>(
			Ids.of(MsptMapMod.MOD_ID, "chunk_detail_request"));

	public static final StreamCodec<FriendlyByteBuf, ChunkDetailRequestPayload> CODEC = StreamCodec.of(
			ChunkDetailRequestPayload::encode, ChunkDetailRequestPayload::decode);
	//?} else {
	/*public static final PacketType<ChunkDetailRequestPayload> TYPE = PacketType.create(
			Ids.of(MsptMapMod.MOD_ID, "chunk_detail_request"), ChunkDetailRequestPayload::decode);
	*///?}

	/** 写包体（CODEC / PacketType 共用）。 */
	public static void encode(FriendlyByteBuf buf, ChunkDetailRequestPayload payload) {
		buf.writeVarInt(MsptMapMod.PROTOCOL);
		buf.writeUtf(payload.dimension());
		buf.writeVarInt(payload.chunkX());
		buf.writeVarInt(payload.chunkZ());
	}

	/** 读包体；无法读取时返回 MISMATCH 哨兵值。 */
	public static ChunkDetailRequestPayload decode(FriendlyByteBuf buf) {
		try {
			int peer = buf.readVarInt();
			String dimension = buf.readUtf();
			int x = buf.readVarInt();
			int z = buf.readVarInt();
			// 对端版本若在尾部附加字段：不解析，直接丢弃。出口处缓冲必须读净
			buf.skipBytes(buf.readableBytes());
			return new ChunkDetailRequestPayload(peer, dimension, x, z);
		} catch (Exception e) {
			// 解码器抛出的异常会冒到网络层并将玩家踢下线，故在此捕获
			buf.skipBytes(buf.readableBytes());
			return new ChunkDetailRequestPayload(MISMATCH, "", 0, 0);
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
	public Type<ChunkDetailRequestPayload> type() {
		return TYPE;
	}
	//?}

	/** 按本端协议构造一个请求。 */
	public static ChunkDetailRequestPayload request(String dimension, int chunkX, int chunkZ) {
		return new ChunkDetailRequestPayload(MsptMapMod.PROTOCOL, dimension, chunkX, chunkZ);
	}
}
