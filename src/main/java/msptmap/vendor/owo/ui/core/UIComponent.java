package msptmap.vendor.owo.ui.core;

import msptmap.vendor.owo.ui.event.*;
import msptmap.vendor.owo.ui.util.FocusHandler;
import msptmap.vendor.owo.util.EventSource;
import msptmap.vendor.owo.ui.event.CharInput;
import msptmap.vendor.owo.ui.event.KeyInput;
import msptmap.vendor.owo.ui.event.MouseClick;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public interface UIComponent extends PositionedRectangle {

    /**
     * Draw the current state of this component onto the screen
     *
     * @param graphics     The transformation stack
     * @param mouseX       The mouse pointer's x-coordinate
     * @param mouseY       The mouse pointer's y-coordinate
     * @param partialTicks The fraction of the current tick that has passed
     * @param delta        The duration of the last frame, in partial ticks
     */
    void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta);

    /**
     * Draw something which clearly indicates
     * that this component is currently focused
     *
     * @param context      The transformation stack
     * @param mouseX       The mouse pointer's x-coordinate
     * @param mouseY       The mouse pointer's y-coordinate
     * @param partialTicks The fraction of the current tick that has passed
     * @param delta        The duration of the last frame, in partial ticks
     */
    default void drawFocusHighlight(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        context.drawRectOutline(this.x(), this.y(), this.width(), this.height(), 0xFFFFFFFF);
    }

    /**
     * @return The parent of this component
     */
    @Contract(pure = true)
    @Nullable ParentUIComponent parent();

    /**
     * @return The focus handler of this component hierarchy
     */
    @Contract(pure = true)
    @Nullable FocusHandler focusHandler();

    /**
     * Update this component's positioning and notify the parent
     *
     * @param positioning The new positioning to use
     * @return The component
     */
    UIComponent positioning(Positioning positioning);

    /**
     * @return The positioning of this component
     */
    @Contract(pure = true)
    AnimatableProperty<Positioning> positioning();

    /**
     * Set the external margins of this component and notify the parent
     *
     * @param margins The new margins to use
     */
    UIComponent margins(Insets margins);

    /**
     * @return The external margins of this component
     */
    @Contract(pure = true)
    AnimatableProperty<Insets> margins();

    /**
     * Set the method this component uses to determine its size
     * per axis
     *
     * @param horizontalSizing The new sizing method to use on the x-axis
     * @param verticalSizing   The new sizing method to use on the y-axis
     */
    default UIComponent sizing(Sizing horizontalSizing, Sizing verticalSizing) {
        this.horizontalSizing(horizontalSizing);
        this.verticalSizing(verticalSizing);
        return this;
    }

    /**
     * Set the method this component uses to determine its size
     * on both axes
     *
     * @param sizing The new sizing method to use on both axes
     */
    default UIComponent sizing(Sizing sizing) {
        this.sizing(sizing, sizing);
        return this;
    }

    /**
     * Set the method this component uses to determine its size on the x-axis
     */
    UIComponent horizontalSizing(Sizing horizontalSizing);

    /**
     * @return The sizing method this component uses on the x-axis
     */
    @Contract(pure = true)
    AnimatableProperty<Sizing> horizontalSizing();

    /**
     * Set the method this component uses to determine its size on the y-axis
     */
    UIComponent verticalSizing(Sizing verticalSizing);

    /**
     * @return The sizing method this component uses on the y-axis
     */
    @Contract(pure = true)
    AnimatableProperty<Sizing> verticalSizing();

    /**
     * Set the id of this component. If this is not unique across the hierarchy,
     * calls to {@link ParentUIComponent#childById(Class, String)} may not be deterministic
     *
     * @param id The new id of this component
     */
    UIComponent id(@Nullable String id);

    /**
     * @return The current id of this component
     */
    @Nullable String id();

    /**
     * Inflate this component into some amount of available space
     *
     * @param space The available space for this component to expand into
     */
    void inflate(Size space);

    /**
     * Called when this component is mounted during the layout process,
     * this must only ever happen after the component has been inflated
     *
     * @param parent The new parent of this component
     * @param x      The new x position of this component
     * @param y      The new y position of this component
     */
    void mount(ParentUIComponent parent, int x, int y);

    /**
     * Called when this component is being dismounted from its
     * parent. This usually happens because the layout is being recalculated
     * or the child has been removed - useful for releasing resources for example
     * <p>
     * <b>Note:</b> It is currently not guaranteed in any way that this method is
     * invoked when the component tree becomes itself unreachable. You may still override
     * this method to release resources if it becomes certain at an early point that
     * they're not needed anymore, but generally resource management stays the responsibility
     * of the individual component for the time being
     *
     * @param reason Why the component is being dismounted. If this is
     *               {@link DismountReason#LAYOUT_INFLATION}, resources should still be held onto
     *               as the component will be re-mounted right after
     */
    void dismount(DismountReason reason);

    /**
     * Execute the given closure immediately with this
     * component as the argument. This is primarily useful for calling
     * methods that don't return the component and could thus not be
     * called inline when constructing the UI Tree.
     * <p>
     * All state updates emitted during execution of the closure are deferred
     * and consolidated into a single update that's emitted after execution has
     * finished. Thus, you can also employ this to efficiently update multiple
     * properties on a component.
     * <p>
     * <b>It is imperative that the type parameter be declared to a type that
     * this component can be represented as - otherwise an exception is thrown</b>
     * <p>
     * Example:
     * <pre>
     * container.child(Components.label(Text.of("Click")).&lt;LabelComponent&gt;configure(label -> {
     *     label.mouseDown().subscribe((mouseX, mouseY, button) -> {
     *         System.out.println("Click");
     *         return true;
     *     });
     * }));
     * </pre>
     *
     * @param closure The closure to execute
     * @param <C>     A type this component can be represented as
     * @return This component
     */
    <C extends UIComponent> C configure(Consumer<C> closure);

    /**
     * @return {@code true} if this component currently has a parent
     */
    @Contract(pure = true)
    default boolean hasParent() {
        return this.parent() != null;
    }

    /**
     * @return The root component of this component's
     * tree, or {@code null} if this component is not mounted
     */
    default ParentUIComponent root() {
        var root = this.parent();
        if (root == null) return null;

        while (root.hasParent()) root = root.parent();
        return root;
    }

    /**
     * Remove this component from its parent, if
     * it is currently mounted
     */
    default void remove() {
        if (!this.hasParent()) return;
        this.parent().queue(() -> {
            this.parent().removeChild(this);
        });
    }

    /**
     * Called when the mouse has been clicked inside
     * the bounding box of this component
     *
     * @param click
     * @param doubled
     * @return {@code true} if this component handled the click and no more
     * components should be notified
     */
    boolean onMouseDown(MouseClick click, boolean doubled);

    EventSource<MouseDown> mouseDown();

    /**
     * Called when a mouse button has been released
     * while this component is focused
     *
     * @param click
     * @return {@code true} if this component handled the event and no more
     *                     components should be notified
     */
    boolean onMouseUp(MouseClick click);

    EventSource<MouseUp> mouseUp();

    /**
     * Called when the mouse has been scrolled inside
     * the bounding box of this component
     *
     * @param mouseX The x coordinate at which the mouse pointer is, relative
     *               to this component's bounding box root
     * @param mouseY The y coordinate at which the mouse pointer is, relative
     *               to this component's bounding box root
     * @param amount How far the mouse was scrolled
     * @return {@code true} if this component handled the scroll event
     * and no more components should be notified
     */
    boolean onMouseScroll(double mouseX, double mouseY, double amount);

    EventSource<MouseScroll> mouseScroll();

    /**
     * Called when the mouse has been dragged
     * while this component is focused
     *
     * @param click
     * @param deltaX How far the mouse was moved on the x-axis
     * @param deltaY How far the mouse was moved on the y-axis
     * @return {@code true} if this component handled the mouse move and no more
     * components should be notified
     */
    boolean onMouseDrag(MouseClick click, double deltaX, double deltaY);

    EventSource<MouseDrag> mouseDrag();

    /**
     * Called when a key on the keyboard has been pressed
     * while this component is focused
     *
     * @param input
     * @return {@code true} if this component handled the key-press and no
     *                     more components should be notified
     */
    boolean onKeyPress(KeyInput input);

    EventSource<KeyPress> keyPress();

    /**
     * Called when a keyboard input event occurred - namely when
     * a key has been pressed and the OS determined it should result
     * in a character being typed
     *
     * @param input
     * @return {@code true} if this component handled the input and no
     *                     * more components should be notified
     */
    boolean onCharTyped(CharInput input);

    EventSource<CharTyped> charTyped();

    /**
     * @return {@code true} if this component can gain focus
     */
    default boolean canFocus(FocusSource source) {
        return false;
    }

    /**
     * Called when this component gains focus, due
     * to being clicked or selected via tab-cycling
     */
    void onFocusGained(FocusSource source);

    EventSource<FocusGained> focusGained();

    /**
     * Called when this component loses focus
     */
    void onFocusLost();

    EventSource<FocusLost> focusLost();

    EventSource<MouseEnter> mouseEnter();

    EventSource<MouseLeave> mouseLeave();

    /**
     * @return The style of cursor to use while the mouse is
     * hovering this component
     */
    CursorStyle cursorStyle();

    /**
     * Set the style of cursor to use while the
     * mouse is hovering this component
     */
    UIComponent cursorStyle(CursorStyle style);

    /**
     * Update the state of this component
     * before drawing the next frame
     *
     * @param delta  The duration of the last frame, in partial ticks
     * @param mouseX The mouse pointer's x-coordinate
     * @param mouseY The mouse pointer's y-coordinate
     */
    default void update(float delta, int mouseX, int mouseY) {
    }

    /**
     * Test whether the given coordinates
     * are inside this component's bounding box
     *
     * @param x The x-coordinate to test
     * @param y The y-coordinate to test
     * @return {@code true} if this component's bounding box encloses
     * the given coordinates
     */
    @Override
    default boolean isInBoundingBox(double x, double y) {
        return PositionedRectangle.super.isInBoundingBox(x, y);
    }

    /**
     * @return The current size of this component's content + its margins
     */
    default Size fullSize() {
        var margins = this.margins().get();
        return Size.of(this.width() + margins.horizontal(), this.height() + margins.vertical());
    }

    /**
     * @return The current width of the bounding box
     * of this component
     */
    @Override
    @Contract(pure = true)
    int width();

    /**
     * @return The current height of the bounding box
     * of this component
     */
    @Override
    @Contract(pure = true)
    int height();

    /**
     * @return The current x-coordinate of the top-left
     * corner of the bounding box of this component
     * <p>
     * As a general rule of thumb, this property should be used
     * whenever the component's position is queried during rendering,
     * input processing and s on. If however, the position is required
     * in the context of a layout operation, {@link #baseX()} is almost
     * always the correct choice instead
     */
    @Override
    @Contract(pure = true)
    int x();

    /**
     * @return The current x-coordinate of this component's
     * <i>base point</i> - the point on which it bases
     * layout calculations.
     * <p>
     * For the majority of components this will be identical
     * to {@link #x()} as they don't have special logic.
     */
    default int baseX() {
        return this.x();
    }

    /**
     * Set the x-coordinate of the top-left corner of the
     * bounding box of this component.
     * <p>
     * This method will usually only be called by the
     * parent component - users of the API
     * should instead alter properties to this component
     * to ensure proper layout updates
     *
     * @param x The new x-coordinate of the top-left corner of the
     *          bounding box of this component
     * @see #positioning(Positioning)
     * @see #margins(Insets)
     */
    void updateX(int x);

    /**
     * @return The current y-coordinate of the top-left
     * corner of the bounding box of this component
     * <p>
     * As a general rule of thumb, this property should be used
     * whenever the component's position is queried during rendering,
     * input processing and s on. If however, the position is required
     * in the context of a layout operation, {@link #baseY()} is almost
     * always the correct choice instead
     */
    @Override
    @Contract(pure = true)
    int y();

    /**
     * @return The current y-coordinate of this component's
     * <i>base point</i> - the point on which it bases
     * layout calculations.
     * <p>
     * For the majority of components this will be identical
     * to {@link #y()} as they don't have special logic.
     */
    default int baseY() {
        return this.y();
    }

    /**
     * Set the y-coordinate of the top-left corner of the
     * bounding box of this component.
     * <p>
     * This method will usually only be called by the
     * parent component - users of the API
     * should instead alter properties to this component
     * to ensure proper layout updates
     *
     * @param y The new y-coordinate of the top-left corner of the
     *          bounding box of this component
     * @see #positioning(Positioning)
     * @see #margins(Insets)
     */
    void updateY(int y);

    /**
     * Set the coordinates of the top-left corner of the
     * bounding box of this component.
     * <p>
     * This method will usually only be called by the
     * parent component - users of the API
     * should instead alter properties to this component
     * to ensure proper layout updates
     *
     * @param y The new coordinates of the top-left corner of the
     *          bounding box of this component
     * @see #positioning(Positioning)
     * @see #margins(Insets)
     */
    default void moveTo(int x, int y) {
        this.updateX(x);
        this.updateY(y);
    }

    enum FocusSource {
        /**
         * The component has been clicked
         */
        MOUSE_CLICK,

        /**
         * The component has been selected by
         * cycling focus via the keyboard
         */
        KEYBOARD_CYCLE
    }

    enum DismountReason {
        /**
         * The child has been dismounted because the parent's layout
         * is being inflated
         */
        LAYOUT_INFLATION,
        /**
         * The child has been dismounted because it has been removed
         * from its parent
         */
        REMOVED
    }
}
