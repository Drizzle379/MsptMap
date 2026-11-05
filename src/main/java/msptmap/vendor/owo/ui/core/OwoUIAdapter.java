package msptmap.vendor.owo.ui.core;

import msptmap.vendor.owo.ui.util.CursorAdapter;
import net.minecraft.client.Minecraft;
//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BiFunction;

/**
 * A UI adapter constitutes the main entrypoint to using owo-ui.
 * It takes care of rendering the UI tree correctly, handles input events
 * and cursor styling.
 * <p>
 * Additionally, the adapter implements all interfaces required for it
 * to be treated as a normal widget by the vanilla screen system - this means
 * even if you choose to not use {@link msptmap.vendor.owo.ui.base.BaseOwoScreen}
 * you can always simply add it as a widget and get most of the functionality
 * working out of the box
 * <p>
 * To draw the UI tree managed by this adapter, use the render method of the active backend.
 *
 * @see msptmap.vendor.owo.ui.base.BaseOwoScreen
 */
public class OwoUIAdapter<R extends ParentUIComponent> implements GuiEventListener, Renderable, NarratableEntry {

    public final R rootComponent;
    public final CursorAdapter cursorAdapter;

    protected boolean disposed = false;

    protected int x, y;
    protected int width, height;

    protected OwoUIAdapter(int x, int y, int width, int height, R rootComponent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.cursorAdapter = CursorAdapter.ofClientWindow();
        this.rootComponent = rootComponent;
    }

    /**
     * Create a UI adapter for the given screen. This also sets it up
     * to be rendered and receive input events, without needing you to
     * do any more setup
     */
    public static <R extends ParentUIComponent> OwoUIAdapter<R> create(Screen screen, BiFunction<Sizing, Sizing, R> rootComponentMaker) {
        var rootComponent = rootComponentMaker.apply(Sizing.fill(100), Sizing.fill(100));

        var adapter = new OwoUIAdapter<>(0, 0, screen.width, screen.height, rootComponent);
        screen.addRenderableWidget(adapter);
        screen.setFocused(adapter);

        return adapter;
    }

    /**
     * Create a new UI adapter without the specific context of a screen
     */
    public static <R extends ParentUIComponent> OwoUIAdapter<R> createWithoutScreen(int x, int y, int width, int height, BiFunction<Sizing, Sizing, R> rootComponentMaker) {
        var rootComponent = rootComponentMaker.apply(Sizing.fill(100), Sizing.fill(100));
        return new OwoUIAdapter<>(x, y, width, height, rootComponent);
    }

    public void inflateAndMount() {
        this.rootComponent.inflate(Size.of(this.width, this.height));
        this.rootComponent.mount(null, this.x, this.y);
    }

    public void moveAndResize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.inflateAndMount();
    }

    public void dispose() {
        this.cursorAdapter.dispose();
        this.disposed = true;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (!(graphics instanceof OwoUIGraphics)) graphics = OwoUIGraphics.of(graphics);
        var owoGraphics = (OwoUIGraphics) graphics;

        final var delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        final var window = Minecraft.getInstance().getWindow();

        this.rootComponent.update(delta, mouseX, mouseY);

        graphics.enableScissor(0, 0, window.getWidth(), window.getHeight());
        this.rootComponent.draw(owoGraphics, mouseX, mouseY, a, delta);
        graphics.disableScissor();

        final var hovered = this.rootComponent.childAt(mouseX, mouseY);
        if (!disposed && hovered != null) {
            this.cursorAdapter.applyStyle(hovered.cursorStyle());
        }
    }
    //?} else if >=1.21.10 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        var owoGraphics = OwoUIGraphics.of(graphics);

        final var delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();

        this.rootComponent.update(delta, mouseX, mouseY);

        graphics.enableScissor(0, 0, this.width, this.height);
        this.rootComponent.draw(owoGraphics, mouseX, mouseY, a, delta);
        graphics.disableScissor();

        final var hovered = this.rootComponent.childAt(mouseX, mouseY);
        if (!disposed && hovered != null) {
            this.cursorAdapter.applyStyle(hovered.cursorStyle());
        }
    }
    *///?}

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.rootComponent.isInBoundingBox(mouseX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return this.rootComponent.onMouseDown(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return this.rootComponent.onMouseUp(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.rootComponent.onMouseScroll(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        return this.rootComponent.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return this.rootComponent.onKeyPress(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return this.rootComponent.onCharTyped(input);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {}
}