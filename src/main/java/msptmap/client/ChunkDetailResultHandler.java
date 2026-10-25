package msptmap.client;

import msptmap.MsptMapMod;
import msptmap.net.ChunkDetailResultPayload;

/**
 * 区块详情结果包的处理：交给 {@link ClientChunkDetail} 缓存，界面下一帧取用。
 *
 * <p>独立于客户端入口（{@link MsptMapClient}）：入口只挂接收器。
 */
public final class ChunkDetailResultHandler {
	private ChunkDetailResultHandler() {
	}

	static void handle(ChunkDetailResultPayload payload) {
		if (payload.protocol() == ChunkDetailResultPayload.MISMATCH) {
			// 包体无法读取：对面格式与本端差异过大，本次作废（界面继续显示加载中）
			MsptMapMod.LOGGER.warn("服务端的区块详情包解析不了（本端 {}），已丢弃", MsptMapMod.PROTOCOL);
			return;
		}
		if (payload.protocol() != MsptMapMod.PROTOCOL) {
			// 版本不同但包读得动：照常采用。详情只影响显示，无需在聊天栏提醒
			MsptMapMod.LOGGER.warn("服务端的 MsptMap 版本与客户端不一致（对面 {}，本端 {}），仍按当前格式解析",
					payload.protocol(), MsptMapMod.PROTOCOL);
		}
		ClientChunkDetail.accept(payload);
	}
}
