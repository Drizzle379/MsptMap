package msptmap.client;

import msptmap.MsptMapMod;
import msptmap.net.ScanResultPayload;
import msptmap.net.SnapshotCodec;
import msptmap.sampler.MsptSampler;

/**
 * 扫描结果包的处理：按状态更新进度圈、快照与聊天栏提示。
 *
 * <p>独立于客户端入口（{@link MsptMapClient}）：入口只挂接收器与本地命令。聊天输出走 {@link Chat}。
 */
public final class ScanResultHandler {
	/** 结果包读不出内容：对面格式与本端不兼容，本次作废。 */
	static final String MISMATCH_KEY = "msptmap.message.mismatch";
	/** 冷却中：距上次扫描结束不足 {@link MsptSampler#COOLDOWN_SECONDS} 秒，服务端的冷却校验拒绝。 */
	static final String COOLDOWN_KEY = "msptmap.message.cooldown";
	/** 版本不同但包读得动：照常出结果，只附一句提醒。 */
	static final String VERSION_MISMATCH_KEY = "msptmap.message.version_mismatch";

	private ScanResultHandler() {
	}

	/** 收结果包：按状态更新进度圈、快照与聊天栏。 */
	static void handle(ScanResultPayload payload) {
		if (payload.protocol() == ScanResultPayload.MISMATCH) {
			// 包体无法读取：对面格式与本端差异过大，本次作废
			MsptMapMod.LOGGER.warn("服务端 MsptMap 的结果包解析不了（本端 {}），已丢弃", MsptMapMod.PROTOCOL);
			Chat.say(MISMATCH_KEY);
			return;
		}
		// 自己发起的扫描才会转进度圈：进度包从不发给自动广播的接收方（见 MsptSampler.broadcastToOperators）
		boolean selfInitiated = ScanProgress.active();
		switch (payload.status()) {
			case START -> {
				MsptMapMod.LOGGER.info("收到 开始：服务端要扫 {} 秒", payload.seconds());
				// 圈的总刻数用服务端报的秒数（请求里的可能被夹取），进度取决于后续推送的包
				ScanProgress.start(payload.seconds());
			}
			// 每 0.1 秒一个，仅用于画圈，不写日志
			case PROGRESS -> ScanProgress.update(payload.windowTicks());
			case DONE -> {
				ScanProgress.stop();
				ClientSnapshot.accept(payload.windowTicks(), payload.tickNanos(), payload.dimensions());
				MsptMapMod.LOGGER.info("收到 完成：窗口 {} tick、{} 个维度",
						payload.windowTicks(), payload.dimensions().size());
				for (SnapshotCodec.DimensionData dimension : payload.dimensions()) {
					MsptMapMod.LOGGER.info("收到 {} 个区块、维度 {}",
							dimension.chunks().size(), dimension.dimension());
				}
			}
			case DENIED -> {
				ScanProgress.stop();
				MsptMapMod.LOGGER.info("收到 拒绝：服务端没给权限");
			}
			case BUSY -> {
				ScanProgress.stop();
				MsptMapMod.LOGGER.info("收到 忙碌：服务端正在扫另一次");
			}
			case COOLDOWN -> {
				ScanProgress.stop();
				MsptMapMod.LOGGER.info("收到 冷却：距上次扫描结束不足 {} 秒", MsptSampler.COOLDOWN_SECONDS);
			}
		}
		// 收尾的几种状态（完成 / 被拒 / 忙碌 / 冷却）在聊天栏提示；START 与 PROGRESS 不提示。
		// 自动广播的完成不提示：告警本身即为提示，再发一条「分析成功」只会造成干扰
		if (selfInitiated || payload.status() != ScanResultPayload.Status.DONE) {
			Chat.say(statusMessage(payload.status()));
		}
		// 对面版本不同但包读得动：照常出结果，只在完成时附一句提醒。PROGRESS 每 0.1 秒一个包、
		// START 时还无法确定能否跑完，均不提示
		if (payload.status() == ScanResultPayload.Status.DONE && payload.protocol() != MsptMapMod.PROTOCOL) {
			Chat.say(VERSION_MISMATCH_KEY);
		}
	}

	/**
	 * 某种状态对应的聊天文案语言键；START / PROGRESS 为 null（「分析中…」已在点按钮时显示）。
	 *
	 * 纯函数，便于离线断言。
	 */
	static String statusMessage(ScanResultPayload.Status status) {
		return switch (status) {
			case DONE -> "msptmap.message.done";
			case DENIED -> "msptmap.message.denied";
			case BUSY -> "msptmap.message.busy";
			case COOLDOWN -> COOLDOWN_KEY;
			case START, PROGRESS -> null;
		};
	}
}
