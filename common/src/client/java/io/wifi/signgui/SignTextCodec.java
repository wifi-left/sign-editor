package io.wifi.signgui;

/**
 * Converts between the text stored on a sign and the text shown in the editor's input boxes.
 *
 * <p>The editor needs a way to type the section sign, because that is how {@code &l}-style codes
 * reach the sign. The convention, which is what the screen's hint line documents, is:
 * <ul>
 *   <li>{@code &} types one section sign;</li>
 *   <li>{@code &&} types one literal {@code &}.</li>
 * </ul>
 *
 * <p>That pair of rules on its own is ambiguous: {@code &§} and {@code §&} would both encode to
 * {@code &&&}, and {@code §§} would encode to {@code &&} and decode back to a single {@code &}.
 * A section sign is therefore written out as itself whenever the next character is another section
 * sign or an ampersand, which makes {@link #unescape} an exact inverse of {@link #escape} for every
 * input. There is no Minecraft dependency here precisely so that claim can be brute forced - see
 * the review notes in the commit; the invariant was checked over every string up to length 7 of an
 * alphabet containing {@code &}, {@code §}, a format letter and a plain letter.
 */
public final class SignTextCodec {

    /** The section sign, i.e. what a single {@code &} in the editor resolves to. */
    public static final char SECTION = '\u00a7';

    private SignTextCodec() {
    }

    /** Stored sign text to editor text. */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                out.append("&&");
            } else if (c == SECTION) {
                boolean ambiguous = i + 1 < text.length()
                        && (text.charAt(i + 1) == '&' || text.charAt(i + 1) == SECTION);
                out.append(ambiguous ? SECTION : '&');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Editor text back to the text that gets stored on the sign. */
    public static String unescape(String editorText) {
        StringBuilder out = new StringBuilder(editorText.length());
        for (int i = 0; i < editorText.length(); i++) {
            char c = editorText.charAt(i);
            if (c == SECTION) {
                out.append(SECTION);
            } else if (c == '&') {
                if (i + 1 < editorText.length() && editorText.charAt(i + 1) == '&') {
                    out.append('&');
                    i++;
                } else {
                    out.append(SECTION);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
