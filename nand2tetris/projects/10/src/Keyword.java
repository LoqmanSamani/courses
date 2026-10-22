import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword — the 21 reserved words of Jack.
 *
 * The constant names are the keywords in upper case, which means the lookup
 * table can be built from values() instead of being typed out a second time.
 * A table written twice is a table that will eventually disagree with itself.
 */
public enum Keyword {
    CLASS, METHOD, FUNCTION, CONSTRUCTOR,
    INT, BOOLEAN, CHAR, VOID,
    VAR, STATIC, FIELD,
    LET, DO, IF, ELSE, WHILE, RETURN,
    TRUE, FALSE, NULL, THIS;

    private static final Map<String, Keyword> BY_TEXT = new HashMap<>();
    static {
        for (Keyword k : values()) {
            BY_TEXT.put(k.text(), k);
        }
    }

    /** The word as it is written in a Jack program. */
    public String text() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True if this word is reserved — which is the only thing separating a
     *  keyword from an identifier. */
    public static boolean isKeyword(String word) {
        return BY_TEXT.containsKey(word);
    }

    /** The keyword a word denotes, or null if it is an ordinary identifier. */
    public static Keyword of(String word) {
        return BY_TEXT.get(word);
    }
}
