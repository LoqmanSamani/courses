import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * XmlWriter — the only class that knows what the output file looks like.
 *
 * It keeps three details out of the parser, which should be busy with grammar
 * and not with formatting:
 *
 *   INDENTATION.  Two spaces per level of nesting. The writer tracks the depth
 *   itself, so the parser never counts spaces: it opens a tag, writes what is
 *   inside, and closes it.
 *
 *   ESCAPING.  Four of the Jack symbols are not safe to drop into XML as they
 *   are. Three of them really occur (< > &), and they must be escaped or the
 *   output is not well-formed XML.
 *
 *   LINE ENDINGS.  CRLF, because the .xml files supplied with the project use
 *   them and the point of this program is to produce output that matches those
 *   files. Java's println() would emit a bare LF on Linux.
 */
public class XmlWriter implements Closeable {

    private static final String EOL = "\r\n";
    private static final String INDENT = "  ";

    private final Writer out;
    private int depth = 0;

    public XmlWriter(Path file) throws IOException {
        this.out = new BufferedWriter(
                Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    /** Writes an opening tag and indents everything after it. */
    public void open(String tag) throws IOException {
        line("<" + tag + ">");
        depth++;
    }

    /** Outdents and writes the matching closing tag. */
    public void close(String tag) throws IOException {
        depth--;
        line("</" + tag + ">");
    }

    /**
     * Writes a complete element on one line: {@code <tag> value </tag>}.
     *
     * The spaces around the value are not decoration. They are what the
     * supplied comparison files contain, and they also make the output
     * unambiguous when the value is itself a space.
     */
    public void leaf(String tag, String value) throws IOException {
        line("<" + tag + "> " + escape(value) + " </" + tag + ">");
    }

    /**
     * Writes a line at the current depth with no indentation of its own.
     *
     * Used for the &lt;tokens&gt; wrapper of the token dump, whose contents the
     * supplied comparison files leave unindented.
     */
    public void rawLine(String text) throws IOException {
        out.write(text);
        out.write(EOL);
    }

    /**
     * Writes one token as a complete element.
     *
     * The mapping from kind of token to element name lives here rather than in
     * the parser, because it is a fact about the output format and not about
     * the Jack language — and because both the token dump and the parse tree
     * need it, and a rule written down twice is a rule that drifts.
     */
    public void token(JackTokenizer.Token t) throws IOException {
        leaf(tagFor(t.type), t.text);
    }

    /** The XML element name for each kind of token. */
    private static String tagFor(TokenType type) {
        return switch (type) {
            case KEYWORD      -> "keyword";
            case SYMBOL       -> "symbol";
            case IDENTIFIER   -> "identifier";
            case INT_CONST    -> "integerConstant";
            case STRING_CONST -> "stringConstant";
            // Never reached: every caller has already rejected the end of the
            // file before asking for it to be written out.
            case EOF -> throw new IllegalStateException(
                    "tried to write the end-of-file marker");
        };
    }

    private void line(String text) throws IOException {
        out.write(INDENT.repeat(depth));
        out.write(text);
        out.write(EOL);
    }

    /**
     * Replaces the characters XML reserves.
     *
     * The ampersand has to be replaced first, otherwise the ampersands
     * introduced by the other two replacements would themselves be escaped and
     * &lt; would come out as &amp;lt;.
     *
     * A double quote would also need escaping in general, but it cannot reach
     * here: it is not one of the Jack symbols, and the tokenizer strips the
     * quotes from a string constant, which itself may not contain one.
     */
    public static String escape(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
