/**
 * TokenType — the five kinds of token in the Jack language.
 *
 * A separate type rather than a string or an int so that the compiler, not a
 * runtime check, catches a misspelled kind. The names are the ones used by the
 * project's API; the XML element each one is written as lives in XmlWriter,
 * because that is a fact about the output format and not about the language.
 */
public enum TokenType {
    /** One of the 21 reserved words. */
    KEYWORD,
    /** One of  { } ( ) [ ] . , ; + - * / &amp; | &lt; &gt; = ~ */
    SYMBOL,
    /** A letter or underscore followed by letters, digits or underscores. */
    IDENTIFIER,
    /** A decimal number in the range 0..32767. */
    INT_CONST,
    /** Text in double quotes, containing neither a quote nor a newline. */
    STRING_CONST,
    /**
     * Not a token at all: the marker the tokenizer returns once the real
     * tokens have run out.
     *
     * Without it the cursor has to stop on the last token and stay there, and
     * then a truncated file quietly succeeds — whatever the parser expects
     * last, that stranded token is offered to it again. Making the end of the
     * input a thing the parser can see turns "the file stopped early" into an
     * ordinary syntax error.
     */
    EOF
}
