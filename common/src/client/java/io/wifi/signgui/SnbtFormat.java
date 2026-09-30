package io.wifi.signgui;

/**
 * Line handling for text that is sent as a Minecraft command.
 *
 * <p>Only whitespace is ever touched, and never inside a string literal: a quoted value may legally
 * contain anything the user typed, including a line break, and folding that away would change what
 * the value means.
 *
 * <p>Runs of whitespace collapse to a single space rather than disappearing. A line break can be
 * separating two command arguments - {@code /data merge block 1 2 3} followed by the compound is the
 * normal case - and gluing those together would produce a command that does not parse. One space is
 * accepted between every pair of tokens in both command syntax and SNBT, so collapsing to it is
 * always safe; the trailing space is dropped.
 */
public final class SnbtFormat {

    private SnbtFormat() {
    }

    /** Folds {@code text} onto one line, keeping string literals byte for byte. */
    public static String oneLine(String text) {
        StringBuilder out = new StringBuilder(text.length());
        char quote = 0;
        boolean escaped = false;
        boolean pendingSpace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                // A literal is just another token: the space in front of it matters and must survive,
                // otherwise `tellraw @a "hi"` would fold to `tellraw @a"hi"`.
                if (pendingSpace && out.length() > 0) {
                    out.append(' ');
                }
                pendingSpace = false;
                quote = c;
                out.append(c);
                continue;
            }
            if (Character.isWhitespace(c)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && out.length() > 0) {
                out.append(' ');
            }
            pendingSpace = false;
            out.append(c);
        }
        return out.toString();
    }
}
