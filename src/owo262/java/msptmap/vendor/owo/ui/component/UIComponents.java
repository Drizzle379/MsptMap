package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.container.FlowLayout;
import msptmap.vendor.owo.ui.container.UIContainers;
import msptmap.vendor.owo.ui.core.Sizing;
import msptmap.vendor.owo.ui.core.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 内置裁剪版的组件工厂。只保留综合分析界面实际用到的组件；依赖接口注入到原版控件的
 * 组件（按钮、文本框、复选框、滑块等）已随接口注入一起移除。
 */
public final class UIComponents {

    private UIComponents() {}

    public static <E extends Entity> EntityComponent<E> entity(Sizing sizing, EntityType<E> type, @Nullable CompoundTag nbt) {
        return new EntityComponent<>(sizing, type, nbt);
    }

    public static <E extends Entity> EntityComponent<E> entity(Sizing sizing, E entity) {
        return new EntityComponent<>(sizing, entity);
    }

    public static ItemComponent item(ItemStack item) {
        return new ItemComponent(item);
    }

    public static BlockComponent block(BlockState state) {
        return new BlockComponent(state, null);
    }

    public static BlockComponent block(BlockState state, BlockEntity blockEntity) {
        return new BlockComponent(state, blockEntity);
    }

    public static BlockComponent block(BlockState state, @Nullable CompoundTag nbt) {
        final var client = Minecraft.getInstance();

        BlockEntity blockEntity = null;

        if (state.getBlock() instanceof EntityBlock provider) {
            blockEntity = provider.newBlockEntity(client.player.blockPosition(), state);
            BlockComponent.prepareBlockEntity(state, blockEntity, nbt);
        }

        return new BlockComponent(state, blockEntity);
    }

    public static LabelComponent label(net.minecraft.network.chat.Component text) {
        return new LabelComponent(text);
    }

    public static SpriteComponent sprite(SpriteId spriteId) {
        return new SpriteComponent(Minecraft.getInstance().getAtlasManager().get(spriteId));
    }

    public static SpriteComponent sprite(TextureAtlasSprite sprite) {
        return new SpriteComponent(sprite);
    }

    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, 256, 256);
    }

    public static BoxComponent box(Sizing horizontalSizing, Sizing verticalSizing) {
        return new BoxComponent(horizontalSizing, verticalSizing);
    }

    public static SpacerComponent spacer(int percent) {
        return new SpacerComponent(percent);
    }

    public static SpacerComponent spacer() {
        return spacer(100);
    }

    public static <T, C extends UIComponent> FlowLayout list(List<T> data, Consumer<FlowLayout> layoutConfigurator, Function<T, C> componentMaker, boolean vertical) {
        var layout = vertical ? UIContainers.verticalFlow(Sizing.content(), Sizing.content()) : UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        layoutConfigurator.accept(layout);

        for (var value : data) {
            layout.child(componentMaker.apply(value));
        }

        return layout;
    }

    public static <T extends UIComponent> T createWithSizing(Supplier<T> componentMaker, Sizing horizontalSizing, Sizing verticalSizing) {
        var component = componentMaker.get();
        component.sizing(horizontalSizing, verticalSizing);
        return component;
    }

}
