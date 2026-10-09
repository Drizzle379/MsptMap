package msptmap.client;

import msptmap.util.Ids;
import msptmap.MsptMapMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/**
 * 地图屏的定位状态机：把「点击总览行」与「聊天告警的外部请求」的目标落到 Xaero 的相机上。
 *
 * <p>与地图屏的交互（读地图处理器、脱离跟随、设相机目标）经 {@link Host} 由 mixin 落地，本类只负责
 * 定位的时序：目标维度未加载则忽略，切换落地后才设相机目标。
 */
public final class ChunkFocus {
	/** 宿主：地图屏 mixin 实现，把本类的动作落到 Xaero 的字段上。 */
	public interface Host {
		/** 当前的地图处理器；地图尚未创建时为 null。 */
		MapProcessor processor();

		/** 若相机正在跟随玩家则脱离（同 Xaero 拖图时），并请求下一帧重建控件。 */
		void detachCamera();

		/** 设相机的动画目标（方块坐标）：自下一帧起向它滑动，到达后由 Xaero 置 null。 */
		void setCameraDestination(int[] destination);
	}

	/**
	 * 维度 ID 字符串的按对象缓存：同一帧中热力图与悬停详情各要取一次 ID，而两次拿到的是同一个维度
	 * 对象，第二次直接复用。维度切换（换世界、走传送门）后对象随之更换，引用比较自然失效，无需清理。
	 */
	private static MapDimension lastDimension;
	private static String lastDimensionId;

	/**
	 * 待执行的定位：维度 + 区块中心方块坐标，记录在点击当帧，实际设置相机目标推迟到维度切换落地
	 * 之后（见 {@link #tick()}）。{@code pendingDimension} 为 null 表示玩家所在维度（同「跟随」）。
	 *
	 * <p>不可在点击当帧就设：Xaero 的维度切换在后台线程落地，而相机坐标空间随显示维度变化，落地
	 * 那一帧 Xaero 会清空相机目标与动画（见 GuiMap 的维度比例变化分支）——提前设的目标会在旧维度
	 * 先滑起来，随后被清掉、停在中途。
	 */
	private ResourceKey<Level> pendingDimension;
	private int[] pendingFocus;

	private final Host host;

	public ChunkFocus(Host host) {
		this.host = host;
	}

	/** 维度 ID（如 {@code minecraft:overworld}）。注册表反查 + 字符串构造不便宜，同一个对象只算一次。 */
	public static String dimensionId(MapDimension dimension) {
		if (dimension != lastDimension) {
			lastDimension = dimension;
			lastDimensionId = Ids.id(dimension.getDimId());
		}
		return lastDimensionId;
	}

	/**
	 * 把地图定位到某区块：必要时先切维度（同 Xaero 的维度按钮：目标即玩家所在维度时回「跟随玩家」），
	 * 并脱离跟随相机；相机的动画目标（区块中心）延后到切换落地再设（见 {@link #tick()}）。
	 */
	public void focus(ChunkRef target) {
		MapProcessor processor = host.processor();
		MapWorld mapWorld = processor.getMapWorld();
		MapDimension found = findDimension(mapWorld, target.dimension());
		if (found == null) {
			// 该维度的地图从未加载过（玩家没去过）：切不过去，这次点击不响应
			MsptMapMod.LOGGER.info("定位 {} 区块 ({}, {})：该维度地图未加载，忽略",
					target.dimension(), target.chunkX(), target.chunkZ());
			return;
		}
		ResourceKey<Level> key = found.getDimId();
		if (key == Minecraft.getInstance().level.dimension()) {
			key = null;
		}
		MsptMapMod.LOGGER.info("定位 {} 区块 ({}, {})：目标维度 {}", target.dimension(),
				target.chunkX(), target.chunkZ(), key == null ? "当前（跟随）" : Ids.id(key));
		mapWorld.setCustomDimensionId(key);
		processor.checkForWorldUpdate();
		host.detachCamera();
		// 区块中心：Xaero 的相机目标就是方块坐标；先记下，切换落地后才设（见字段注释）
		pendingDimension = key;
		pendingFocus = new int[]{target.chunkX() * 16 + 8, target.chunkZ() * 16 + 8};
	}

	/**
	 * 每帧渲染末尾调用：该处晚于 Xaero 的相机复位（维度比例变化、拉回玩家等清空相机目标与动画的
	 * 分支），此时设的目标不会被清掉。切换未完成时继续等。
	 */
	public void tick() {
		MapProcessor processor = host.processor();
		if (processor == null) {
			return;
		}
		// 外部请求的定位（聊天告警里点击区块行）：无在途定位时取出，交给与点击总览同一套逻辑。
		// 地图世界还没就绪就先留着，下一帧再取
		if (pendingFocus == null && MapFocus.awaiting() && processor.isMapWorldUsable()) {
			focus(MapFocus.consume());
		}
		if (pendingFocus == null) {
			return;
		}
		// 切换中或地图世界重建中：此时设了也会被 Xaero 清掉，继续等
		if (processor.isWaitingForWorldUpdate() || !processor.isMapWorldUsable()) {
			return;
		}
		ResourceKey<Level> expected = pendingDimension != null ? pendingDimension
				: Minecraft.getInstance().level.dimension();
		// 当前显示维度尚未切到目标：地图空间不同，坐标不可用，继续等
		if (!expected.equals(processor.getMapWorld().getCurrentDimensionId())) {
			return;
		}
		host.setCameraDestination(pendingFocus);
		pendingFocus = null;
	}

	/** 从地图已加载的维度里按 ID 找目标；没有则 null。按 ID 字符串反查，避开各版本构造维度键的差异。 */
	private static MapDimension findDimension(MapWorld mapWorld, String dimId) {
		for (MapDimension dimension : mapWorld.getDimensionsList()) {
			if (dimId.equals(dimensionId(dimension))) {
				return dimension;
			}
		}
		return null;
	}
}
