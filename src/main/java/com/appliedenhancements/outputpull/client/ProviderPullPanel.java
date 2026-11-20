package com.appliedenhancements.outputpull.client;

import com.appliedenhancements.outputpull.ProviderOutputPull;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Layout and background of the feature G "Pull Outputs" side panel, drawn to the right of the pattern provider
 * window. The six face buttons form a cube net:
 * <pre>
 *        [U]
 *   [W]  [N]  [E]  [S]
 *        [D]
 * </pre>
 * Client only. All coordinates are window (screen) coordinates.
 */
public final class ProviderPullPanel {
    public static final int CELL = 18;
    public static final int BUTTON_SIZE = 16;
    private static final int PADDING = 5;
    private static final int TITLE_HEIGHT = 11;
    private static final int NOTE_HEIGHT = 10;
    /** Gap between the provider window and the panel. */
    private static final int GAP = 2;
    /**
     * Distance from the window's top edge to the panel's top. AE2 draws a small side box (upgrades / cards) at the
     * top right of the provider window that the panel would otherwise cover (found in the TEST instance 2026-11-19).
     */
    private static final int TOP_CLEARANCE = 44;
    /** Minimum distance from the screen edge when the panel is pushed back inside a narrow window. */
    private static final int SCREEN_MARGIN = 2;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 3;

    private static final int BORDER_COLOR = 0xFF373737;
    private static final int FILL_COLOR = 0xFFC6C6C6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int DISABLED_NOTE_COLOR = 0xFFAA2222;
    private static final int LOADING_NOTE_COLOR = 0xFF606060;

    private ProviderPullPanel() {
    }

    public static Component title() {
        return Component.translatable("gui.appliedenhancements.provider_pull.title");
    }

    /** The one-line note under the buttons, or null when there is nothing to say. */
    @Nullable
    public static Component note() {
        if (ClientProviderPullState.current() == null) {
            return Component.translatable("gui.appliedenhancements.provider_pull.loading");
        }
        if (!ClientProviderPullState.enabled()) {
            return Component.translatable("gui.appliedenhancements.provider_pull.disabled");
        }
        return null;
    }

    /** Fixed width (independent of the note), so the buttons don't move when the state arrives. */
    public static int width(Font font) {
        int content = GRID_COLUMNS * CELL - (CELL - BUTTON_SIZE);
        content = Math.max(content, font.width(title()));
        content = Math.max(content, font.width(
                Component.translatable("gui.appliedenhancements.provider_pull.disabled")));
        content = Math.max(content, font.width(
                Component.translatable("gui.appliedenhancements.provider_pull.loading")));
        return content + 2 * PADDING;
    }

    /**
     * Right of the provider window, below AE2's top-right side box. If that would run past the screen's right edge
     * (narrow GUI scale), the panel is pushed left so it stays fully visible; it may then cover part of the window.
     */
    public static Rect2i bounds(Font font, int leftPos, int topPos, int imageWidth, int screenWidth) {
        int height = PADDING + TITLE_HEIGHT + GRID_ROWS * CELL - (CELL - BUTTON_SIZE) + PADDING;
        if (note() != null) {
            height += NOTE_HEIGHT;
        }
        int width = width(font);
        int x = leftPos + imageWidth + GAP;
        if (x + width > screenWidth - SCREEN_MARGIN) {
            x = Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN);
        }
        return new Rect2i(x, topPos + TOP_CLEARANCE, width, height);
    }

    public static boolean contains(Rect2i bounds, double x, double y) {
        return x >= bounds.getX() && x < bounds.getX() + bounds.getWidth()
                && y >= bounds.getY() && y < bounds.getY() + bounds.getHeight();
    }

    /** Top-left corner of a face button. */
    public static int buttonX(Rect2i bounds, Direction face) {
        int gridWidth = GRID_COLUMNS * CELL - (CELL - BUTTON_SIZE);
        int gridLeft = bounds.getX() + (bounds.getWidth() - gridWidth) / 2;
        return gridLeft + column(face) * CELL;
    }

    public static int buttonY(Rect2i bounds, Direction face) {
        return bounds.getY() + PADDING + TITLE_HEIGHT + row(face) * CELL;
    }

    private static int column(Direction face) {
        return switch (face) {
            case WEST -> 0;
            case UP, NORTH, DOWN -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
        };
    }

    private static int row(Direction face) {
        return switch (face) {
            case UP -> 0;
            case WEST, NORTH, EAST, SOUTH -> 1;
            case DOWN -> 2;
        };
    }

    /** Background, border, title and note. The buttons are normal widgets drawn afterwards. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Rect2i bounds) {
        int x0 = bounds.getX();
        int y0 = bounds.getY();
        int x1 = x0 + bounds.getWidth();
        int y1 = y0 + bounds.getHeight();
        graphics.fill(x0, y0, x1, y1, BORDER_COLOR);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FILL_COLOR);
        graphics.text(font, title(), x0 + PADDING, y0 + PADDING, TITLE_COLOR, false);

        var note = note();
        if (note != null) {
            int noteY = y0 + PADDING + TITLE_HEIGHT + GRID_ROWS * CELL - (CELL - BUTTON_SIZE) + 2;
            int color = ClientProviderPullState.current() == null ? LOADING_NOTE_COLOR : DISABLED_NOTE_COLOR;
            graphics.text(font, note, x0 + PADDING, noteY, color, false);
        }
    }

    // ---- texts shared by the buttons ----

    /** Short code shown on a face button: off, same face, or the initial of the machine side. */
    public static String modeCode(int mode) {
        if (mode == ProviderOutputPull.OFF || !ProviderOutputPull.isValid(mode)) {
            return "·";
        }
        if (mode == ProviderOutputPull.SAME_FACE) {
            return "=";
        }
        return directionInitial(Direction.from3DDataValue(mode - 2));
    }

    public static String directionInitial(Direction direction) {
        return switch (direction) {
            case DOWN -> "D";
            case UP -> "U";
            case NORTH -> "N";
            case SOUTH -> "S";
            case WEST -> "W";
            case EAST -> "E";
        };
    }

    public static Component directionName(Direction direction) {
        return switch (direction) {
            case DOWN -> Component.translatable("gui.appliedenhancements.provider_pull.direction.down");
            case UP -> Component.translatable("gui.appliedenhancements.provider_pull.direction.up");
            case NORTH -> Component.translatable("gui.appliedenhancements.provider_pull.direction.north");
            case SOUTH -> Component.translatable("gui.appliedenhancements.provider_pull.direction.south");
            case WEST -> Component.translatable("gui.appliedenhancements.provider_pull.direction.west");
            case EAST -> Component.translatable("gui.appliedenhancements.provider_pull.direction.east");
        };
    }

    public static Component modeName(int mode) {
        if (mode == ProviderOutputPull.OFF || !ProviderOutputPull.isValid(mode)) {
            return Component.translatable("gui.appliedenhancements.provider_pull.mode.off");
        }
        if (mode == ProviderOutputPull.SAME_FACE) {
            return Component.translatable("gui.appliedenhancements.provider_pull.mode.same_face");
        }
        return Component.translatable("gui.appliedenhancements.provider_pull.mode.side",
                directionName(Direction.from3DDataValue(mode - 2)));
    }
}
