package io.wifi.signgui;

/**
 * Flat, square-cornered palette for the sign editor.
 *
 * <p>The theme colour is {@code rgb(34, 134, 81)}. No corner rounding anywhere: hierarchy comes
 * from fill alpha and 1px borders.
 */
public final class SignEditorTheme {

    private SignEditorTheme() {
    }

    /** Theme colour, rgb(34, 134, 81). */
    public static final int ACCENT = 0xFF228651;
    /** Same hue, lightened for hover. */
    public static final int ACCENT_HOVER = 0xFF2EA364;
    /** 33% alpha, for bars and backplates. */
    public static final int ACCENT_DIM = 0x55228651;
    /** 80% alpha, for focused borders. */
    public static final int ACCENT_BORDER = 0xCC228651;

    /** Row cards. */
    public static final int CARD_BG = 0x4C000000;
    public static final int CARD_BG_HOVER = 0x5C0E2A1E;
    public static final int CARD_BG_FOCUS = 0x6C10331F;
    public static final int CARD_BORDER = 0x33FFFFFF;

    /** The footer holding the glow controls and the action buttons. */
    public static final int FOOTER_BG = 0x66000000;
    public static final int FOOTER_BORDER = 0x33FFFFFF;

    /** Hairline separator, e.g. under the header. */
    public static final int DIVIDER = 0x33FFFFFF;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFF9EAAA4;
    public static final int TEXT_MUTED = 0xFF6E7A74;

    /** Plain buttons. */
    public static final int BTN_BG = 0x66000000;
    public static final int BTN_BG_HOVER = 0x8C0E2A1E;
    public static final int BTN_BORDER = 0x40FFFFFF;

    /** Scrollbar. */
    public static final int SB_TRACK = 0x22000000;
    public static final int SB_THUMB = 0x99228651;
    public static final int SB_THUMB_HOVER = 0xCC2EA364;

    /** Outline drawn around a colour swatch so pale colours stay visible. */
    public static final int SWATCH_BORDER = 0xFF101413;
}
