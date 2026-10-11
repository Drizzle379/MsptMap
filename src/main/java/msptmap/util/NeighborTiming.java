package msptmap.util;

import net.minecraft.core.BlockPos;

/**
 * 方块更新的嵌套计数：邻居更新（NC）与形状更新（PP）的各个入口共用一份，两者相互嵌套时不重复计时。
 *
 * <p>NC 的入口在 {@code ServerLevel}、PP 的入口在 {@code Level}，分属两个 mixin。状态因此定义在
 * Level 侧（{@code LevelMixin} 的 {@code @Unique} 字段），ServerLevel 经继承获得同一份；两个 mixin
 * 都通过本接口访问。
 *
 * <p>接口必须放在 mixin 包之外：{@code msptmap.mixins} 被配置声明为 mixin 包，其中的非 mixin 类
 * 一旦被直接引用（如 {@code implements NeighborTiming}），Mixin 会在类加载时报 IllegalClassLoadError。
 */
public interface NeighborTiming {
	/** 进入任一方块更新入口：仅最外层记开始时刻。 */
	void msptmapUpdateBegin();

	/** 退出任一方块更新入口：最外层关窗，耗时记在该方块所在区块。 */
	void msptmapUpdateEnd(BlockPos pos);
}
