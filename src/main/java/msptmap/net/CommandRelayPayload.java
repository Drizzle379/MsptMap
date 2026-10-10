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
 * 客户端 → 服务端：转发一条子命令，由服务端代为执行。
 *
 * <p>两端注册了同名的根命令 {@code msptmap}，而 Fabric 只按客户端那棵命令树判定是否本地执行：能识别的
 * 命令一律不发给服务端，服务端的 access 与 monitor 因而被客户端同名根命令遮蔽，玩家无法执行。客户端
 * 为此注册形状相同的两棵子树，叶子节点将命令原样转发，服务端校验后执行。
 *
 * <p>命令串只接受 {@code msptmap } 前缀（服务端校验，见 CommandRelayHandler）：这条通道不能充当
 * 任意命令的旁路。
 *
 * <p>版本不同的两端也允许互发：包 ID 不带版本号，包体开头的魔数是标记而非闸门——收到其他值仍照读，
 * 无法读取才判 MISMATCH，同 {@link ScanRequestPayload}。1.20.4 及以前是 Fabric Loader 的
 * FabricPacket 体系（PacketType + write），1.20.5 起换成原版的 CustomPacketPayload（StreamCodec）。
 */
public record CommandRelayPayload(int protocol, String command)
		//? if >=1.20.5 {
		implements CustomPacketPayload
		//?} else {
		/*implements FabricPacket
		*///?}
{
	/** 包体无法读取时的占位值：服务端见此即忽略。 */
	public static final int MISMATCH = -1;

	//? if >=1.20.5 {
	public static final Type<CommandRelayPayload> TYPE = new Type<>(
			Ids.of(MsptMapMod.MOD_ID, "command_relay"));

	public static final StreamCodec<FriendlyByteBuf, CommandRelayPayload> CODEC = StreamCodec.of(
			CommandRelayPayload::encode, CommandRelayPayload::decode);
	//?} else {
	/*public static final PacketType<CommandRelayPayload> TYPE = PacketType.create(
			Ids.of(MsptMapMod.MOD_ID, "command_relay"), CommandRelayPayload::decode);
	*///?}

	/** 写包体（CODEC / PacketType 共用）。 */
	public static void encode(FriendlyByteBuf buf, CommandRelayPayload payload) {
		buf.writeVarInt(MsptMapMod.PROTOCOL);
		buf.writeUtf(payload.command());
	}

	/** 读包体；无法读取时返回 MISMATCH 哨兵值。 */
	public static CommandRelayPayload decode(FriendlyByteBuf buf) {
		try {
			int peer = buf.readVarInt();
			String command = buf.readUtf();
			// 对端版本若在尾部附加字段：不解析，直接丢弃——出口处缓冲必须读净，
			// PacketDecoder 见解码后仍有剩余字节即报 IOException 并断线
			buf.skipBytes(buf.readableBytes());
			return new CommandRelayPayload(peer, command);
		} catch (Exception e) {
			// 解码器抛出的异常会冒到网络层并将玩家踢下线，故在此捕获；残余字节同样需跳过
			buf.skipBytes(buf.readableBytes());
			return new CommandRelayPayload(MISMATCH, "");
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
	public Type<CommandRelayPayload> type() {
		return TYPE;
	}
	//?}

	/** 按本端协议构造一个转发请求。参数是完整的服务端命令，如 {@code msptmap monitor on}。 */
	public static CommandRelayPayload relay(String command) {
		return new CommandRelayPayload(MsptMapMod.PROTOCOL, command);
	}
}
