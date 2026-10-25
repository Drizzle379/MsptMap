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

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：某个区块的详细信息。
 *
 * <p>两条列表都只含展示所需的最小字段：
 * <ul>
 *   <li>{@link TicketInfo}：票类型在注册表里的路径（英文原代码）、锚点区块坐标、该票在加载链与
 *       模拟链上的传播等级。某条链不参与（类型不支持）或等级超过 32 时为 -1。</li>
 *   <li>{@link EntityInfo}：实体类型完整 ID 与当前数量。名称由客户端翻译，翻译不了就显示 ID。</li>
 * </ul>
 *
 * <p>只由新增的详情请求触发，不改动旧结果包；未升级的服务端没有这个包，客户端会先探测再发送。
 */
public record ChunkDetailResultPayload(int protocol, String dimension, int chunkX, int chunkZ,
                                       List<TicketInfo> tickets, List<EntityInfo> entities)
		//? if >=1.20.5 {
		implements CustomPacketPayload
		//?} else {
		/*implements FabricPacket
		*///?}
{
	/** 包体无法读取时的占位值：客户端见此即放弃本次结果。 */
	public static final int MISMATCH = -1;

	/** 单包上限：正常区块远达不到，只用于挡住异常数据。 */
	public static final int MAX_TICKETS = 512;
	public static final int MAX_ENTITIES = 512;

	/** 每条票 / 实体至少占用的字节数（类型或 ID 的长度前缀、坐标与等级），用于解码前校验数量。 */
	private static final int MIN_TICKET_BYTES = 5;
	private static final int MIN_ENTITY_BYTES = 2;

	/**
	 * 包 ID。用 {@link Ids#of} 构造资源位置，不用 {@code CustomPacketPayload.createType(String)}：
	 * 后者只接受路径段（带冒号即抛异常）。不带版本号，能否读取由包体魔数判定。
	 */
	//? if >=1.20.5 {
	public static final Type<ChunkDetailResultPayload> TYPE = new Type<>(
			Ids.of(MsptMapMod.MOD_ID, "chunk_detail_result"));

	public static final StreamCodec<FriendlyByteBuf, ChunkDetailResultPayload> CODEC = StreamCodec.of(
			ChunkDetailResultPayload::encode, ChunkDetailResultPayload::decode);
	//?} else {
	/*public static final PacketType<ChunkDetailResultPayload> TYPE = PacketType.create(
			Ids.of(MsptMapMod.MOD_ID, "chunk_detail_result"), ChunkDetailResultPayload::decode);
	*///?}

	/** 一条加载票：类型路径、锚点区块坐标、两条链上的传播等级（≤ 32；不参与为 -1）。 */
	public record TicketInfo(String type, int anchorX, int anchorZ, int loadLevel, int simLevel) {
	}

	/** 一种实体的当前数量。 */
	public record EntityInfo(String id, int count) {
	}

	/** 写包体（CODEC / PacketType 共用）。 */
	public static void encode(FriendlyByteBuf buf, ChunkDetailResultPayload payload) {
		buf.writeVarInt(MsptMapMod.PROTOCOL);
		buf.writeUtf(payload.dimension());
		buf.writeVarInt(payload.chunkX());
		buf.writeVarInt(payload.chunkZ());
		buf.writeVarInt(payload.tickets().size());
		for (TicketInfo ticket : payload.tickets()) {
			buf.writeUtf(ticket.type());
			buf.writeVarInt(ticket.anchorX());
			buf.writeVarInt(ticket.anchorZ());
			buf.writeVarInt(ticket.loadLevel());
			buf.writeVarInt(ticket.simLevel());
		}
		buf.writeVarInt(payload.entities().size());
		for (EntityInfo entity : payload.entities()) {
			buf.writeUtf(entity.id());
			buf.writeVarInt(entity.count());
		}
	}

	/** 读包体；无法读取时返回 MISMATCH 哨兵值。 */
	public static ChunkDetailResultPayload decode(FriendlyByteBuf buf) {
		try {
			int peer = buf.readVarInt();
			String dimension = buf.readUtf();
			int x = buf.readVarInt();
			int z = buf.readVarInt();
			int ticketCount = buf.readVarInt();
			// 数量先与剩余字节数对照：异常数据不会先分配一个巨大的列表
			if (ticketCount < 0 || ticketCount > MAX_TICKETS
					|| ticketCount > buf.readableBytes() / MIN_TICKET_BYTES) {
				throw new IllegalArgumentException("ticket count " + ticketCount);
			}
			List<TicketInfo> tickets = new ArrayList<>(ticketCount);
			for (int i = 0; i < ticketCount; i++) {
				tickets.add(new TicketInfo(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
						buf.readVarInt(), buf.readVarInt()));
			}
			int entityCount = buf.readVarInt();
			if (entityCount < 0 || entityCount > MAX_ENTITIES
					|| entityCount > buf.readableBytes() / MIN_ENTITY_BYTES) {
				throw new IllegalArgumentException("entity count " + entityCount);
			}
			List<EntityInfo> entities = new ArrayList<>(entityCount);
			for (int i = 0; i < entityCount; i++) {
				entities.add(new EntityInfo(buf.readUtf(), buf.readVarInt()));
			}
			// 对端版本若在尾部附加字段：不解析，直接丢弃。出口处缓冲必须读净
			buf.skipBytes(buf.readableBytes());
			return new ChunkDetailResultPayload(peer, dimension, x, z, tickets, entities);
		} catch (Exception e) {
			// 异常冒到网络层会踢出玩家，故在此捕获并返回 MISMATCH；残余字节同样需跳过
			buf.skipBytes(buf.readableBytes());
			return new ChunkDetailResultPayload(MISMATCH, "", 0, 0, List.of(), List.of());
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
	public Type<ChunkDetailResultPayload> type() {
		return TYPE;
	}
	//?}

	/** 空结果：维度不存在或无权限时回发，客户端据此显示「无」而不是一直等待。 */
	public static ChunkDetailResultPayload empty(String dimension, int chunkX, int chunkZ) {
		return new ChunkDetailResultPayload(MsptMapMod.PROTOCOL, dimension, chunkX, chunkZ, List.of(), List.of());
	}
}
