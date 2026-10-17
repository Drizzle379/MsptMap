package msptmap.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取实体所在维度。
 *
 * <p>{@code Entity.level()} 的 intermediary 名在 1.21.9 由 {@code method_37908} 变为
 * {@code method_73183}（实体新增实现 {@code ItemOwner} 接口），一份包覆盖 1.21.6–1.21.10 时
 * 直接调用只能在一边命中；{@code level} 字段在各版本名称不变。
 */
@Mixin(Entity.class)
public interface EntityLevelAccessor {
	@Accessor("level")
	Level msptmap$level();
}
