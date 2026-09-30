package io.wifi.signgui;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout geometry for {@link SignEditorScreen}.
 *
 * <p>Deliberately free of any Minecraft dependency: it is pure integer math over {@link Rect}s,
 * which is what allows {@link #verify} / {@link #verifyAll} to sweep every plausible screen size
 * and every legal scroll offset without launching the game.
 *
 * <p>Two design decisions carry the whole thing:
 * <ul>
 *   <li>The body viewport height is always an exact multiple of {@code rowH}, and the scroll offset
 *       is always an exact multiple of {@code rowH}. A row is therefore either fully inside the
 *       viewport or fully outside it, never half-visible. That removes the need for a scissor
 *       rectangle, and it removes the "scrolled out of view but still clickable" failure mode.</li>
 *   <li>Every horizontal coordinate is derived from the centred panel, so left/right symmetry is a
 *       property of the formulas instead of something hand-tuned per widget.</li>
 * </ul>
 */
public final class SignEditorLayout {

    private SignEditorLayout() {
    }

    // ------------------------------------------------------------------ tunables

    /** Number of editable sign lines. */
    public static final int ROWS = 4;

    /**
     * Control lines inside one row card: the text with its format toggles, the click command, and
     * the colour detail. Three lines is what the per-line colour, shadow colour and raw-NBT toggle
     * add up to without crowding any single line.
     */
    public static final int ROW_LINES = 3;

    /** Minimum gap between any panel and the screen edge. */
    public static final int SCREEN_PAD = 6;

    /** Scrollbar slot. Always reserved, even when nothing scrolls, so the panel never reflows. */
    public static final int SB_W = 5;
    public static final int SB_GAP = 3;
    public static final int THUMB_MIN_H = 20;

    /** Horizontal padding inside a card. */
    public static final int CARD_PAD_X = 6;

    /** Line-number badge column. */
    public static final int LABEL_W = 18;

    /** Generic horizontal gap between two columns of a row. */
    public static final int GAP = 4;

    /** Colour swatch button. */
    public static final int COLOR_W = 40;

    /** Format toggles: B I U S O. */
    public static final int FMT_BTN_W = 14;
    public static final int FMT_BTN_GAP = 1;
    public static final int FMT_COUNT = 5;
    public static final int FMT_W = FMT_COUNT * FMT_BTN_W + (FMT_COUNT - 1) * FMT_BTN_GAP;

    /** The per-line "edit the raw component" toggle, sitting after the format toggles. */
    public static final int NBT_GAP = 2;
    /**
     * Wide enough for two CJK glyphs (18px) or a three letter abbreviation plus a margin: the mode
     * toggles and the format/minify buttons share this column.
     */
    public static final int NBT_BTN_W = 30;
    /** Format toggles plus the raw-NBT toggle, i.e. the whole control strip of a row. */
    public static final int STRIP_W = FMT_W + NBT_GAP + NBT_BTN_W;

    /**
     * The colour and shadow-colour text fields. Wide enough to show a dye name such as
     * {@code light_blue}, since that is what the field prefers to display.
     */
    public static final int HEX_W = 66;
    /** Gap between the "text colour" group and the "shadow colour" group. */
    public static final int COLOR_GROUP_GAP = 8;
    /**
     * Cap on the width reserved for a colour caption. Tighter than {@link #LABEL_RESERVE_MAX}
     * because two of them share one line, and they are drawn truncated rather than allowed to push
     * the widgets they describe.
     */
    public static final int COLOR_LABEL_MAX = 36;

    /** Panel width bounds. */
    public static final int MAX_PANEL_W = 460;
    public static final int MIN_PANEL_W = 280;
    public static final int MIN_TEXT_W = 60;

    /** Tab bar. */
    public static final int TAB_MAX_W = 100;
    public static final int TAB_MIN_W = 70;
    public static final int TAB_GAP = 2;

    /** Header spacing. */
    public static final int TITLE_GAP_FULL = 4;
    public static final int TITLE_GAP_COMPACT = 3;
    public static final int TIP_GAP = 2;
    public static final int TIP_LINE_GAP = 1;
    public static final int HEADER_GAP_FULL = 5;
    public static final int HEADER_GAP_COMPACT = 4;
    /** Both tiers show the hint lines; the gap below them is what the tier tunes. */
    public static final int TIP_LINES = 2;

    /** Gap between the body and the footer, kept equal to the gap between one row card and the next. */
    public static final int FOOTER_GAP = 4;
    public static final int FOOTER_INNER_GAP_FULL = 5;
    public static final int FOOTER_INNER_GAP_COMPACT = 4;
    public static final int ACTION_GAP = 6;
    public static final int ACTION_BTN_MAX_W = 100;
    public static final int ACTION_BTN_MIN_W = 54;

    /** Glow toggle button width. */
    public static final int GLOW_BTN_W = 52;

    /**
     * Upper bound on the width reserved for a {@code Label:} caption. Real widths are clamped to
     * this so an unusually long translation can only make the caption itself overlap, never the
     * layout behind it.
     */
    public static final int LABEL_RESERVE_MAX = 64;

    /** {@code Font.lineHeight}. */
    public static final int LINE_H = 9;

    // ------------------------------------------------------------------ types

    /** Density tier: a roomy two-line row, or a denser one for short windows. */
    public enum Tier {
        FULL, COMPACT
    }

    /** Axis-aligned integer rectangle. Origin top-left, size non-negative. */
    public record Rect(int x, int y, int w, int h) {

        public int right() {
            return this.x + this.w;
        }

        public int bottom() {
            return this.y + this.h;
        }

        public int centerX() {
            return this.x + this.w / 2;
        }

        public boolean isEmpty() {
            return this.w <= 0 || this.h <= 0;
        }

        public boolean contains(int px, int py) {
            return px >= this.x && px < right() && py >= this.y && py < bottom();
        }

        public boolean intersects(Rect o) {
            return this.x < o.right() && o.x < right() && this.y < o.bottom() && o.y < bottom();
        }

        /** True when this rectangle is fully inside {@code o}. */
        public boolean inside(Rect o) {
            return this.x >= o.x && this.y >= o.y && right() <= o.right() && bottom() <= o.bottom();
        }

        public Rect moveY(int dy) {
            return new Rect(this.x, this.y + dy, this.w, this.h);
        }

        public Rect moveX(int dx) {
            return new Rect(this.x + dx, this.y, this.w, this.h);
        }

        @Override
        public String toString() {
            return "[" + this.x + "," + this.y + " " + this.w + "x" + this.h + "]";
        }
    }

    /**
     * Font-derived widths that the layout has to reserve space for. Pass the real
     * {@code font.width(...)} values so the labels never collide with the widgets next to them.
     */
    public record Metrics(int glowLabelW, int inkLabelW, int colorLabelW, int shadowLabelW) {

        public static final Metrics DEFAULT = new Metrics(30, 60, 36, 36);

        public int glowReserve() {
            return clamp(this.glowLabelW, 0, LABEL_RESERVE_MAX);
        }

        public int inkReserve() {
            return clamp(this.inkLabelW, 0, LABEL_RESERVE_MAX);
        }

        public int shadowReserve() {
            return clamp(this.shadowLabelW, 0, COLOR_LABEL_MAX);
        }

        public int colorReserve() {
            return clamp(this.colorLabelW, 0, COLOR_LABEL_MAX);
        }
    }

    /** All rectangles of one sign line, already shifted by the current scroll offset. */
    public record RowLayout(
            /** The row card itself. */
            Rect card,
            /** Line A: the "3" badge. */
            Rect label,
            /** Line B: the "»" badge. */
            Rect labelCmd,
            /** Line A: the text field. */
            Rect text,
            /** In raw mode: one editor covering all three lines, plus its format and minify buttons. */
            Rect rawText,
            Rect rawFormat,
            Rect rawMinify,
            /** Line B: the click command. */
            Rect cmd,
            /** Line C: caption for the text colour. */
            Rect colorLabel,
            /** Line C: the text-colour swatch, kept next to its hex entry. */
            Rect colorSwatch,
            /** Line C: hex entry for the text colour. */
            Rect colorHex,
            /** Line C: caption for the shadow colour. */
            Rect shadowLabel,
            /** Line C: the shadow-colour swatch. */
            Rect shadowSwatch,
            /** Line C: hex entry for the shadow colour, empty when the panel cannot hold it. */
            Rect shadowHex,
            /** Line A: the format toggles. */
            Rect fmt,
            /** Line A: the raw-component mode toggle. */
            Rect nbt,
            Rect[] fmtButtons) {

        /**
         * True when every one of this row's widgets lies inside {@code viewport}. The raw editor is
         * checked too: it is wider than the field it replaces, so it is the one that could stick out.
         */
        public boolean inside(Rect viewport) {
            for (Rect r : all()) {
                if (!r.inside(viewport)) {
                    return false;
                }
            }
            return true;
        }

        /** True when no widget of this row touches {@code viewport}. */
        public boolean outside(Rect viewport) {
            for (Rect r : all()) {
                if (r.intersects(viewport)) {
                    return false;
                }
            }
            return true;
        }

        private List<Rect> all() {
            List<Rect> rects = new ArrayList<>(16 + this.fmtButtons.length);
            rects.add(this.card);
            rects.add(this.label);
            rects.add(this.labelCmd);
            rects.add(this.text);
            rects.add(this.rawText);
            rects.add(this.rawFormat);
            rects.add(this.rawMinify);
            rects.add(this.cmd);
            rects.add(this.colorLabel);
            rects.add(this.colorSwatch);
            rects.add(this.colorHex);
            rects.add(this.shadowLabel);
            rects.add(this.shadowSwatch);
            rects.add(this.shadowHex);
            rects.add(this.fmt);
            rects.add(this.nbt);
            for (Rect button : this.fmtButtons) {
                rects.add(button);
            }
            return rects;
        }
    }

    /** Everything the screen needs to place widgets and draw chrome. */
    public static final class Layout {
        public final int width;
        public final int height;
        public final Tier tier;

        /** Effective scroll offset: clamped to {@code [0, maxScroll]} and snapped to a row multiple. */
        public final int scroll;
        public final int maxScroll;
        public final int rowsVisible;
        public final int rowH;
        public final int cardH;
        public final int fieldH;
        public final int textW;
        public final int actionBtnW;

        public final Rect panel;
        public final Rect tabEdit;
        public final Rect tabNbt;
        public final int titleY;
        public final int tipY1;
        public final int tipY2;
        public final int tipLines;

        /** Scrollable viewport for the four rows. Exactly {@code rowsVisible * rowH} tall. */
        public final Rect body;
        public final List<RowLayout> rows;

        public final Rect footer;
        public final Rect glowLabel;
        public final Rect glowToggle;
        public final Rect inkLabel;
        public final Rect inkSwatch;
        /** The four action buttons, in order: change side, reload, cancel, confirm. */
        public final Rect[] actionButtons;

        public final Rect scrollTrack;
        public final Rect scrollThumb;
        /** False when the panel was too narrow for the shadow's hex entry, which is then hidden. */
        public final boolean shadowHexPresent;

        public final Rect nbtPreview;
        public final Rect nbtCopy;
        public final Rect nbtExecute;

        private Layout(Builder b) {
            this.width = b.width;
            this.height = b.height;
            this.tier = b.tier;
            this.scroll = b.scroll;
            this.maxScroll = b.maxScroll;
            this.rowsVisible = b.rowsVisible;
            this.rowH = b.rowH;
            this.cardH = b.cardH;
            this.fieldH = b.fieldH;
            this.textW = b.textW;
            this.actionBtnW = b.actionBtnW;
            this.panel = b.panel;
            this.tabEdit = b.tabEdit;
            this.tabNbt = b.tabNbt;
            this.titleY = b.titleY;
            this.tipY1 = b.tipY1;
            this.tipY2 = b.tipY2;
            this.tipLines = b.tipLines;
            this.body = b.body;
            this.rows = b.rows;
            this.footer = b.footer;
            this.glowLabel = b.glowLabel;
            this.glowToggle = b.glowToggle;
            this.inkLabel = b.inkLabel;
            this.inkSwatch = b.inkSwatch;
            this.actionButtons = b.actionButtons;
            this.scrollTrack = b.scrollTrack;
            this.scrollThumb = b.scrollThumb;
            this.shadowHexPresent = b.shadowHexPresent;
            this.nbtPreview = b.nbtPreview;
            this.nbtCopy = b.nbtCopy;
            this.nbtExecute = b.nbtExecute;
        }

        /** The four action buttons, in order: change side, reload, cancel, confirm. */
        public Rect changeSideButton() {
            return this.actionButtons[0];
        }

        public Rect reloadButton() {
            return this.actionButtons[1];
        }

        public Rect cancelButton() {
            return this.actionButtons[2];
        }

        public Rect confirmButton() {
            return this.actionButtons[3];
        }

        /** Index of the first row that is fully visible at the current scroll offset. */
        public int firstVisibleRow() {
            return Math.min(this.scroll / this.rowH, ROWS - 1);
        }

        public boolean rowVisible(int i) {
            return i >= firstVisibleRow() && i < firstVisibleRow() + this.rowsVisible;
        }

        /** Highest legal scroll offset, i.e. the value that reveals the last row. */
        public boolean scrollable() {
            return this.maxScroll > 0;
        }
    }

    private static final class Builder {
        int width;
        int height;
        Tier tier;
        int scroll;
        int maxScroll;
        int rowsVisible;
        int rowH;
        int cardH;
        int fieldH;
        int textW;
        int actionBtnW;
        Rect panel;
        Rect tabEdit;
        Rect tabNbt;
        int titleY;
        int tipY1;
        int tipY2;
        int tipLines;
        Rect body;
        List<RowLayout> rows;
        Rect footer;
        Rect glowLabel;
        Rect glowToggle;
        Rect inkLabel;
        Rect inkSwatch;
        Rect[] actionButtons;
        Rect scrollTrack;
        Rect scrollThumb;
        boolean shadowHexPresent;
        Rect nbtPreview;
        Rect nbtCopy;
        Rect nbtExecute;
    }

    // ------------------------------------------------------------------ tiering

    private record Spec(
            Tier tier,
            int tabH,
            int fieldH,
            int rowPad,
            int fieldGap,
            int rowGap,
            int actionH,
            int footerInnerGap,
            int titleGap,
            int headerGap,
            int footerPadY,
            int maxRowGrow) {

        int rowH() {
            return 2 * this.rowPad + ROW_LINES * this.fieldH + (ROW_LINES - 1) * this.fieldGap + this.rowGap;
        }

        int cardH() {
            return rowH() - this.rowGap;
        }

        int headerH() {
            return SCREEN_PAD + this.tabH + this.titleGap + LINE_H
                    + TIP_GAP + TIP_LINES * LINE_H + (TIP_LINES - 1) * TIP_LINE_GAP
                    + this.headerGap;
        }

        int footerH() {
            return 2 * this.footerPadY + this.fieldH + this.footerInnerGap + this.actionH;
        }

        /** Vertical space left for the body viewport. */
        int bodySpace(int height) {
            return height - SCREEN_PAD - headerH() - footerH() - FOOTER_GAP;
        }

        int rowsThatFit(int height) {
            return Math.max(0, bodySpace(height) / rowH());
        }
    }

    private static final Spec FULL = new Spec(
            Tier.FULL, 14, 16, 4, 3, 4, 18, 5, TITLE_GAP_FULL, HEADER_GAP_FULL, 4, 6);
    private static final Spec COMPACT = new Spec(
            Tier.COMPACT, 12, 14, 3, 2, 3, 16, 4, TITLE_GAP_COMPACT, HEADER_GAP_COMPACT, 3, 3);

    private static Spec spec(Tier tier) {
        return tier == Tier.FULL ? FULL : COMPACT;
    }

    /**
     * Gives a tier more breathing room when the window is taller than it needs.
     *
     * <p>Only the padding inside a row grows, which makes each row taller without touching the
     * fields, the header or the footer. The growth is exactly bounded by the leftover space: adding
     * {@code g} to the padding adds {@code 2g} to the row height, so four rows consume at most
     * {@code 8g}, and {@code g} is computed as {@code surplus / 8}. A tall window therefore gets
     * taller rows instead of a taller empty margin, and because the compact tier caps out just below
     * the full tier's base height there is no jump when the tier changes.
     */
    private static Spec grown(Spec spec, int bodySpace) {
        int surplus = bodySpace - ROWS * spec.rowH();
        int grow = clamp(surplus / (2 * ROWS), 0, spec.maxRowGrow());
        if (grow == 0) {
            return spec;
        }
        return new Spec(
                spec.tier(), spec.tabH(), spec.fieldH(), spec.rowPad() + grow, spec.fieldGap(),
                spec.rowGap(), spec.actionH(), spec.footerInnerGap(), spec.titleGap(),
                spec.headerGap(), spec.footerPadY(), spec.maxRowGrow());
    }

    /** Roomiest tier in which all four lines fit; {@link Tier#COMPACT} if neither fits. */
    public static Tier chooseTier(int height) {
        return FULL.rowsThatFit(height) >= ROWS ? Tier.FULL : Tier.COMPACT;
    }

    /**
     * Shortest screen for which every rectangle is guaranteed to be inside the screen: the compact
     * tier's tightest case, which is the header, a single row and the footer stacked up. Minecraft
     * never scales the GUI this small, but the sweep in {@link #verify} refuses to pretend
     * otherwise.
     */
    public static int minimumHeight() {
        return COMPACT.headerH() + COMPACT.rowH() + FOOTER_GAP + COMPACT.footerH() + SCREEN_PAD;
    }

    // ------------------------------------------------------------------ main entry point

    /** Clamp and snap a desired scroll offset to the row grid. */
    public static int normalizeScroll(int desired, int rowH, int maxScroll) {
        if (maxScroll <= 0 || rowH <= 0) {
            return 0;
        }
        int snapped = ((desired + rowH / 2) / rowH) * rowH;
        return clamp(snapped, 0, maxScroll);
    }

    public static Layout compute(int width, int height, int desiredScroll, Metrics metrics) {
        Spec base = spec(chooseTier(height));
        int bodySpace = base.bodySpace(height);
        Spec spec = grown(base, bodySpace);
        Builder b = new Builder();
        b.width = width;
        b.height = height;
        b.tier = spec.tier;
        b.fieldH = spec.fieldH;
        b.rowH = spec.rowH();
        b.cardH = spec.cardH();

        // ---- horizontal: one centred group of [panel][gap][scrollbar] -----------
        int maxW = clamp(width - 2 * SCREEN_PAD - SB_W - SB_GAP, MIN_PANEL_W, MAX_PANEL_W);
        int groupW = maxW + SB_GAP + SB_W;
        int panelX = Math.floorDiv(width - groupW, 2);
        b.panel = new Rect(panelX, 0, maxW, 0);
        int sbX = panelX + maxW + SB_GAP;

        // Row columns, laid out left to right inside the card padding:
        //   line A: [#n][text][B I U S O][NBT]
        //   line B:         [command, full width]
        //   line C:    [Colour:][swatch][hex]  [Shadow:][swatch][hex]
        b.textW = maxW - (2 * CARD_PAD_X + LABEL_W + GAP + GAP + STRIP_W);
        int innerLeft = panelX + CARD_PAD_X;
        int innerRight = panelX + maxW - CARD_PAD_X;
        int textX = innerLeft + LABEL_W + GAP;
        int fmtX = textX + b.textW + GAP;
        int toggleX = fmtX + FMT_W + NBT_GAP;
        // The raw editor spans everything the row has except the mode toggle itself.
        int rawW = toggleX - GAP - textX;
        // Line C: two swatch+hex groups. The shadow's hex is dropped when the panel cannot hold
        // both, which keeps the row inside the card at any width.
        int colorLabelW = metrics.colorReserve();
        int shadowLabelW = metrics.shadowReserve();
        int colorGroupW = COLOR_W + GAP + HEX_W;
        int shadowGroupX = innerLeft + colorLabelW + GAP + colorGroupW + COLOR_GROUP_GAP;
        boolean shadowHexFits = innerRight - (shadowGroupX + shadowLabelW + GAP + COLOR_W) >= HEX_W + GAP;
        int shadowHexW = shadowHexFits ? HEX_W : 0;

        // ---- header ------------------------------------------------------------
        int tabW = clamp((maxW - TAB_GAP) / 2, TAB_MIN_W, TAB_MAX_W);
        int tabTotalW = 2 * tabW + TAB_GAP;
        int tabX = Math.floorDiv(width - tabTotalW, 2);
        b.tabEdit = new Rect(tabX, SCREEN_PAD, tabW, spec.tabH);
        b.tabNbt = new Rect(tabX + tabW + TAB_GAP, SCREEN_PAD, tabW, spec.tabH);
        b.titleY = SCREEN_PAD + spec.tabH + spec.titleGap;
        b.tipLines = TIP_LINES;
        b.tipY1 = b.titleY + LINE_H + TIP_GAP;
        b.tipY2 = b.tipY1 + LINE_H + TIP_LINE_GAP;
        int contentTop = spec.headerH();

        // ---- vertical: whole rows only ----------------------------------------
        int rowsVisible = clamp(bodySpace / b.rowH, 1, ROWS);
        int bodyH = rowsVisible * b.rowH;
        // Spare room is left below the block: hugging the header is what keeps the form readable.
        int blockTop = contentTop;

        b.rowsVisible = rowsVisible;
        b.maxScroll = (ROWS - rowsVisible) * b.rowH;
        b.scroll = normalizeScroll(desiredScroll, b.rowH, b.maxScroll);

        b.body = new Rect(panelX, blockTop, maxW, bodyH);
        b.shadowHexPresent = shadowHexFits;
        b.rows = new ArrayList<>(ROWS);
        Rect[] fmtButtons = new Rect[FMT_COUNT];
        for (int i = 0; i < ROWS; i++) {
            int rowTop = blockTop + i * b.rowH - b.scroll;
            int yA = rowTop + spec.rowPad;
            int yB = yA + spec.fieldH + spec.fieldGap;
            int yC = yB + spec.fieldH + spec.fieldGap;
            for (int k = 0; k < FMT_COUNT; k++) {
                fmtButtons[k] = new Rect(fmtX + k * (FMT_BTN_W + FMT_BTN_GAP), yA, FMT_BTN_W, spec.fieldH);
            }
            Rect colorSwatch = new Rect(innerLeft + colorLabelW + GAP, yC, COLOR_W, spec.fieldH);
            Rect colorHex = new Rect(colorSwatch.right() + GAP, yC, HEX_W, spec.fieldH);
            Rect shadowSwatch = new Rect(shadowGroupX + shadowLabelW + GAP, yC, COLOR_W, spec.fieldH);
            Rect shadowHex = shadowHexFits
                    ? new Rect(shadowSwatch.right() + GAP, yC, HEX_W, spec.fieldH)
                    : new Rect(shadowSwatch.right() + GAP, yC, 0, 0);
            b.rows.add(new RowLayout(
                    new Rect(panelX, rowTop, maxW, b.cardH),
                    new Rect(innerLeft, yA, LABEL_W, spec.fieldH),
                    new Rect(innerLeft, yB, LABEL_W, spec.fieldH),
                    new Rect(textX, yA, b.textW, spec.fieldH),
                    new Rect(textX, yA, rawW, ROW_LINES * spec.fieldH() + (ROW_LINES - 1) * spec.fieldGap()),
                    new Rect(toggleX, yB, NBT_BTN_W, spec.fieldH),
                    new Rect(toggleX, yC, NBT_BTN_W, spec.fieldH),
                    new Rect(textX, yB, innerRight - textX, spec.fieldH),
                    new Rect(innerLeft, yC, colorLabelW, spec.fieldH),
                    colorSwatch,
                    colorHex,
                    new Rect(shadowGroupX, yC, shadowLabelW, spec.fieldH),
                    shadowSwatch,
                    shadowHex,
                    new Rect(fmtX, yA, FMT_W, spec.fieldH),
                    new Rect(toggleX, yA, NBT_BTN_W, spec.fieldH),
                    fmtButtons.clone()));
        }

        // ---- footer -----------------------------------------------------------
        int footerTop = blockTop + bodyH + FOOTER_GAP;
        b.footer = new Rect(panelX, footerTop, maxW, spec.footerH());
        int glowY = footerTop + spec.footerPadY;
        int glowLabelW = metrics.glowReserve();
        int inkLabelW = metrics.inkReserve();
        b.glowLabel = new Rect(innerLeft, glowY, glowLabelW, spec.fieldH);
        b.glowToggle = new Rect(b.glowLabel.right() + GAP, glowY, GLOW_BTN_W, spec.fieldH);
        b.inkLabel = new Rect(b.glowToggle.right() + 12, glowY, inkLabelW, spec.fieldH);
        b.inkSwatch = new Rect(b.inkLabel.right() + GAP, glowY, COLOR_W, spec.fieldH);

        int actionY = glowY + spec.fieldH + spec.footerInnerGap;
        b.actionBtnW = clamp((maxW - 2 * CARD_PAD_X - 3 * ACTION_GAP) / 4, ACTION_BTN_MIN_W, ACTION_BTN_MAX_W);
        int actionTotalW = ROWS * b.actionBtnW + 3 * ACTION_GAP;
        int actionX = panelX + Math.floorDiv(maxW - actionTotalW, 2);
        b.actionButtons = new Rect[ROWS];
        for (int i = 0; i < ROWS; i++) {
            b.actionButtons[i] = new Rect(actionX + i * (b.actionBtnW + ACTION_GAP), actionY, b.actionBtnW, spec.actionH);
        }

        // ---- scrollbar --------------------------------------------------------
        int thumbH = clamp(Math.round((float) bodyH * bodyH / (ROWS * b.rowH)), 1, bodyH);
        thumbH = clamp(Math.max(thumbH, THUMB_MIN_H), 1, bodyH);
        int thumbY = blockTop;
        if (b.maxScroll > 0 && bodyH > thumbH) {
            thumbY = blockTop + Math.round((float) (bodyH - thumbH) * b.scroll / b.maxScroll);
        }
        b.scrollTrack = new Rect(sbX, blockTop, SB_W, bodyH);
        b.scrollThumb = new Rect(sbX, thumbY, SB_W, thumbH);

        // ---- NBT tab ----------------------------------------------------------
        b.nbtPreview = new Rect(panelX, blockTop, maxW, Math.max(0, actionY - FOOTER_GAP - blockTop));
        int nbtBtnW = clamp((maxW - 2 * CARD_PAD_X - ACTION_GAP) / 2, 80, 140);
        int nbtTotalW = 2 * nbtBtnW + ACTION_GAP;
        int nbtX = panelX + Math.floorDiv(maxW - nbtTotalW, 2);
        b.nbtCopy = new Rect(nbtX, actionY, nbtBtnW, spec.actionH);
        b.nbtExecute = new Rect(nbtX + nbtBtnW + ACTION_GAP, actionY, nbtBtnW, spec.actionH);

        return new Layout(b);
    }

    // ------------------------------------------------------------------ verification

    /**
     * Checks every geometric invariant for one screen size, at every legal scroll offset.
     *
     * @param out collects human readable violations; left untouched when the size is sound
     * @return {@code out}, for chaining
     */
    public static List<String> verify(int width, int height, Metrics metrics, List<String> out) {
        if (width < MIN_PANEL_W + 2 * SCREEN_PAD + SB_W + SB_GAP) {
            return out; // narrower than the panel floor: nothing is guaranteed, and MC never gets here
        }
        if (height < minimumHeight()) {
            return out; // shorter than one row plus the framing: likewise out of contract
        }
        Layout probe = compute(width, height, 0, metrics);
        Rect screen = new Rect(0, 0, width, height);
        String tag = width + "x" + height;

        for (int scroll = 0; scroll <= probe.maxScroll; scroll += probe.rowH) {
            Layout l = compute(width, height, scroll, metrics);
            String where = tag + "@" + scroll;

            // (1) the viewport is exactly rowsVisible whole rows
            if (l.body.h != l.rowsVisible * l.rowH) {
                out.add(where + ": body height " + l.body.h + " != " + l.rowsVisible + "*" + l.rowH);
            }
            // (2) scroll is on the row grid and in range
            if (l.scroll % l.rowH != 0 || l.scroll < 0 || l.scroll > l.maxScroll) {
                out.add(where + ": scroll " + l.scroll + " out of grid/range (max " + l.maxScroll + ")");
            }
            if (l.maxScroll % l.rowH != 0) {
                out.add(where + ": maxScroll " + l.maxScroll + " not a multiple of rowH " + l.rowH);
            }
            // (3) the footer sits strictly below the body, the tabs strictly above it
            if (l.footer.y < l.body.bottom()) {
                out.add(where + ": footer y " + l.footer.y + " above body bottom " + l.body.bottom());
            }
            if (l.tabEdit.bottom() > l.body.y) {
                out.add(where + ": tabs bottom " + l.tabEdit.bottom() + " below body top " + l.body.y);
            }
            // (4) every row is either fully inside the viewport or entirely outside it
            for (int i = 0; i < ROWS; i++) {
                RowLayout r = l.rows.get(i);
                if (l.rowVisible(i)) {
                    if (!r.inside(l.body)) {
                        out.add(where + ": visible row " + i + " not inside viewport");
                    }
                } else if (!r.outside(l.body)) {
                    out.add(where + ": hidden row " + i + " overlaps viewport");
                }
                if (i > 0 && l.rows.get(i).card.y - l.rows.get(i - 1).card.y != l.rowH) {
                    out.add(where + ": row pitch " + i + " is off the grid");
                }
            }
            // (5) bounds and non-overlap of everything interactive
            List<Rect> live = new ArrayList<>();
            List<String> names = new ArrayList<>();
            addRect(live, names, "tab.edit", l.tabEdit, screen, out, where);
            addRect(live, names, "tab.nbt", l.tabNbt, screen, out, where);
            for (int i = 0; i < ROWS; i++) {
                if (!l.rowVisible(i)) {
                    continue;
                }
                RowLayout r = l.rows.get(i);
                // Everything interactive in the normal (component-per-field) mode...
                addRect(live, names, "row" + i + ".text", r.text, screen, out, where);
                addRect(live, names, "row" + i + ".cmd", r.cmd, screen, out, where);
                addRect(live, names, "row" + i + ".colorSwatch", r.colorSwatch, screen, out, where);
                addRect(live, names, "row" + i + ".colorHex", r.colorHex, screen, out, where);
                addRect(live, names, "row" + i + ".nbt", r.nbt, screen, out, where);
                addRect(live, names, "row" + i + ".shadowSwatch", r.shadowSwatch, screen, out, where);
                addRect(live, names, "row" + i + ".shadowHex", r.shadowHex, screen, out, where);
                for (int k = 0; k < r.fmtButtons.length; k++) {
                    addRect(live, names, "row" + i + ".fmt" + k, r.fmtButtons[k], screen, out, where);
                }
                // ...and in raw mode, where all of that collapses into one wide editor plus the
                // toggle that gets you back out.
                for (Rect raw : new Rect[] { r.rawText, r.rawFormat, r.rawMinify }) {
                    if (!raw.inside(screen)) {
                        out.add(where + ": row " + i + " raw control " + raw + " outside screen");
                    }
                    if (!raw.inside(r.card)) {
                        out.add(where + ": row " + i + " raw control " + raw + " escapes its card");
                    }
                }
                if (r.rawText.intersects(r.nbt) || r.rawText.intersects(r.rawFormat) || r.rawText.intersects(r.rawMinify)) {
                    out.add(where + ": row " + i + " raw editor overlaps its buttons");
                }
                if (r.rawFormat.intersects(r.rawMinify) || r.rawFormat.intersects(r.nbt)) {
                    out.add(where + ": row " + i + " raw buttons overlap each other");
                }
            }
            addRect(live, names, "glow.toggle", l.glowToggle, screen, out, where);
            addRect(live, names, "ink.swatch", l.inkSwatch, screen, out, where);
            for (int i = 0; i < l.actionButtons.length; i++) {
                addRect(live, names, "action" + i, l.actionButtons[i], screen, out, where);
            }
            for (int i = 0; i < live.size(); i++) {
                for (int j = i + 1; j < live.size(); j++) {
                    if (live.get(i).intersects(live.get(j))) {
                        out.add(where + ": " + names.get(i) + " overlaps " + names.get(j));
                    }
                }
            }
            // (6) NBT tab rectangles
            addRect(live, names, "nbt.preview", l.nbtPreview, screen, out, where);
            addRect(live, names, "nbt.copy", l.nbtCopy, screen, out, where);
            addRect(live, names, "nbt.execute", l.nbtExecute, screen, out, where);
            if (l.nbtPreview.intersects(l.nbtCopy) || l.nbtPreview.intersects(l.nbtExecute)) {
                out.add(where + ": nbt preview overlaps its buttons");
            }
            // (7) minimum usable widths
            if (l.textW < MIN_TEXT_W) {
                out.add(where + ": textW " + l.textW + " < " + MIN_TEXT_W);
            }
            if (l.actionBtnW < ACTION_BTN_MIN_W) {
                out.add(where + ": actionBtnW " + l.actionBtnW + " < " + ACTION_BTN_MIN_W);
            }
            // (8) the thumb lives inside its track
            if (!l.scrollThumb.inside(l.scrollTrack)) {
                out.add(where + ": thumb " + l.scrollThumb + " not inside track " + l.scrollTrack);
            }
        }
        return out;
    }

    private static void addRect(
            List<Rect> live, List<String> names, String name, Rect r, Rect screen, List<String> out, String where) {
        if (!r.inside(screen)) {
            out.add(where + ": " + name + " " + r + " outside screen " + screen);
        }
        if (!r.isEmpty()) {
            live.add(r);
            names.add(name);
        }
    }

    /** Sweeps every size in {@code [minW,maxW] x [minH,maxH]} on the given grid steps. */
    public static List<String> verifyAll(int minW, int maxW, int wStep, int minH, int maxH, int hStep) {
        List<String> out = new ArrayList<>();
        Metrics[] metrics = {
                Metrics.DEFAULT,
                new Metrics(0, 0, 0, 0),
                new Metrics(LABEL_RESERVE_MAX, LABEL_RESERVE_MAX, LABEL_RESERVE_MAX, LABEL_RESERVE_MAX),
                new Metrics(4 * LABEL_RESERVE_MAX, 4 * LABEL_RESERVE_MAX, 4 * LABEL_RESERVE_MAX, 4 * LABEL_RESERVE_MAX),
        };
        for (Metrics m : metrics) {
            for (int w = minW; w <= maxW; w += wStep) {
                for (int h = minH; h <= maxH; h += hStep) {
                    verify(w, h, m, out);
                    if (out.size() > 64) {
                        out.add("... sweep aborted after 64 violations");
                        return out;
                    }
                }
            }
        }
        return out;
    }

    public static List<String> verifyAll() {
        return verifyAll(288, 2560, 4, 120, 1600, 4);
    }

    // ------------------------------------------------------------------ helpers

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Number of grid steps ({@code rowH}) needed to reveal all four rows. */
    public static int scrollSteps(int rowsVisible) {
        return ROWS - clamp(rowsVisible, 1, ROWS);
    }
}
