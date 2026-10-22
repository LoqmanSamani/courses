import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JackTokenizer — turns the raw text of a .jack file into a stream of tokens.
 *
 * This is the first half of the syntax analyzer. It knows what a token looks
 * like but nothing at all about the Jack grammar: it cannot tell you whether
 * "let class ;" is legal, only that it is three tokens. Deciding whether a
 * sequence of tokens forms a program is CompilationEngine's job.
 *
 * WHY THE WHOLE FILE IS TOKENIZED UP FRONT
 * The course API is a cursor — hasMoreTokens() / advance() / tokenType() — and
 * that is preserved below. Underneath, though, the file is scanned once into a
 * list rather than a token at a time. That costs a few hundred objects and buys
 * two things:
 *
 *   1. peekSymbol(), one-token lookahead. The parser genuinely needs it: when a
 *      term starts with an identifier, only the NEXT token says whether it is a
 *      plain variable, an array entry (x[i]) or a subroutine call (x.y()).
 *   2. Every token carries the line it came from, so a syntax error can point at
 *      a line number instead of just failing.
 *
 * WHAT COUNTS AS A TOKEN
 *   keyword          one of the 21 reserved words
 *   symbol           one of  { } ( ) [ ] . , ; + - * / & | < > = ~
 *   integerConstant  a decimal number in 0..32767
 *   stringConstant   "..." with no double quote and no newline inside
 *   identifier       a letter or _ followed by letters, digits or _
 *
 * Whitespace and comments (// to end of line, and / * ... * / including the
 * /** doc form) separate tokens and are otherwise discarded.
 */
public class JackTokenizer {

    /** Every character that is a token all by itself. */
    private static final String SYMBOLS = "{}()[].,;+-*/&|<>=~";

    /** The largest integer constant the Hack machine can hold. */
    private static final int MAX_INT_CONST = 32767;

    /**
     * One token: what kind it is, the text it was written with, and where.
     *
     * For a string constant `text` holds the contents WITHOUT the surrounding
     * quotes, because that is what both the XML output and a later code
     * generator want. For every other kind it is the lexeme exactly as written.
     */
    public static final class Token {
        public final TokenType type;
        public final String text;
        public final int line;

        Token(TokenType type, String text, int line) {
            this.type = type;
            this.text = text;
            this.line = line;
        }

        @Override
        public String toString() {
            return type + "(" + text + ") at line " + line;
        }
    }

    private final String fileName;      // for error messages only
    private final List<Token> tokens = new ArrayList<>();
    private int cursor = -1;            // index of the current token
    private Token eof;                  // returned once the real tokens run out

    /** Reads a .jack file and splits it into tokens. */
    public JackTokenizer(Path jackFile) throws IOException {
        this.fileName = jackFile.getFileName().toString();
        scan(Files.readString(jackFile, StandardCharsets.UTF_8));
    }

    /** Tokenizes a string directly. Used by the tests. */
    public JackTokenizer(String source, String fileName) {
        this.fileName = fileName;
        scan(source);
    }

    // =====================================================================
    //  Scanning
    // =====================================================================

    /**
     * The scanner: one pass over the characters, emitting tokens.
     *
     * The order of the tests matters. Comments are checked before the symbol
     * test, because '/' is both the start of a comment and the division
     * operator, and whichever is checked first wins.
     */
    private void scan(String src) {
        int i = 0;
        int line = 1;
        final int n = src.length();

        while (i < n) {
            char c = src.charAt(i);

            // ---- whitespace ------------------------------------------------
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // ---- // comment to end of line ---------------------------------
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // ---- /* ... */ comment, possibly spanning lines ----------------
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int startLine = line;
                i += 2;
                while (true) {
                    if (i + 1 >= n) {
                        throw error(startLine, "unterminated /* comment");
                    }
                    if (src.charAt(i) == '*' && src.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    if (src.charAt(i) == '\n') {
                        line++;
                    }
                    i++;
                }
                continue;
            }

            // ---- symbol ----------------------------------------------------
            if (SYMBOLS.indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), line));
                i++;
                continue;
            }

            // ---- string constant -------------------------------------------
            if (c == '"') {
                int start = ++i;                       // step over the opening quote
                while (i < n && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\n') {
                        throw error(line, "string constant is not closed before the end of the line");
                    }
                    i++;
                }
                if (i >= n) {
                    throw error(line, "string constant is not closed before the end of the file");
                }
                tokens.add(new Token(TokenType.STRING_CONST, src.substring(start, i), line));
                i++;                                   // step over the closing quote
                continue;
            }

            // ---- integer constant -------------------------------------------
            if (Character.isDigit(c)) {
                int start = i;
                while (i < n && Character.isDigit(src.charAt(i))) {
                    i++;
                }
                String digits = src.substring(start, i);
                // Checked here rather than left to the code generator: a literal
                // too big for a 16-bit word is a source error, and the line
                // number that makes it findable is only available right now.
                long value = Long.parseLong(digits);
                if (value > MAX_INT_CONST) {
                    throw error(line, "integer constant " + digits
                            + " is larger than " + MAX_INT_CONST);
                }
                tokens.add(new Token(TokenType.INT_CONST, digits, line));
                continue;
            }

            // ---- identifier or keyword ---------------------------------------
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) {
                    i++;
                }
                String word = src.substring(start, i);
                // The ONLY difference between a keyword and an identifier is
                // whether the word happens to be in the reserved list.
                TokenType type = Keyword.isKeyword(word)
                        ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                tokens.add(new Token(type, word, line));
                continue;
            }

            throw error(line, "unexpected character '" + c + "'");
        }

        // The cursor may step one place past the last token, onto this.
        eof = new Token(TokenType.EOF, "end of file", line);
    }

    private RuntimeException error(int line, String message) {
        return new IllegalArgumentException(fileName + ":" + line + ": " + message);
    }

    // =====================================================================
    //  The cursor API
    // =====================================================================

    /** True while there is another token after the current one. */
    public boolean hasMoreTokens() {
        return cursor + 1 < tokens.size();
    }

    /**
     * Makes the next token current.
     *
     * The cursor is allowed to come to rest one place past the last token, on
     * the EOF marker, and stays there if advanced again. Running off the end is
     * not an error here — it is the parser that decides whether it still wanted
     * something, and it reports that as an ordinary syntax error.
     */
    public void advance() {
        if (cursor < tokens.size()) {
            cursor++;
        }
    }

    /** The token the cursor is on, or the EOF marker past the last one. */
    public Token current() {
        if (cursor < 0) {
            throw new IllegalStateException("advance() has not been called yet");
        }
        return cursor < tokens.size() ? tokens.get(cursor) : eof;
    }

    /** True once the cursor has run off the end of the real tokens. */
    public boolean atEnd() {
        return cursor >= tokens.size();
    }

    public TokenType tokenType() {
        return current().type;
    }

    /** Valid only when tokenType() is KEYWORD. */
    public Keyword keyWord() {
        require(TokenType.KEYWORD);
        return Keyword.of(current().text);
    }

    /** Valid only when tokenType() is SYMBOL. */
    public char symbol() {
        require(TokenType.SYMBOL);
        return current().text.charAt(0);
    }

    /** Valid only when tokenType() is IDENTIFIER. */
    public String identifier() {
        require(TokenType.IDENTIFIER);
        return current().text;
    }

    /** Valid only when tokenType() is INT_CONST. */
    public int intVal() {
        require(TokenType.INT_CONST);
        return Integer.parseInt(current().text);
    }

    /** Valid only when tokenType() is STRING_CONST. Quotes are already stripped. */
    public String stringVal() {
        require(TokenType.STRING_CONST);
        return current().text;
    }

    /**
     * The symbol of the NEXT token, or '\0' if there is none or it is not a
     * symbol.
     *
     * This one-token lookahead is what lets the parser tell the three
     * identifier-led terms apart without backtracking:
     *
     *      x        a plain variable
     *      x[i]     an array entry          -> next symbol is [
     *      x(...)   a subroutine call       -> next symbol is (
     *      x.y(...) a qualified call        -> next symbol is .
     */
    public char peekSymbol() {
        if (cursor + 1 >= tokens.size()) {
            return '\0';
        }
        Token next = tokens.get(cursor + 1);
        return next.type == TokenType.SYMBOL ? next.text.charAt(0) : '\0';
    }

    /** Every token, in order. Used to write the XxxT.xml token dump. */
    public List<Token> allTokens() {
        return List.copyOf(tokens);
    }

    private void require(TokenType expected) {
        if (tokenType() != expected) {
            throw new IllegalStateException(
                    "asked for a " + expected + " but the current token is " + current());
        }
    }
}
