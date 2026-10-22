import java.io.IOException;

/**
 * CompilationEngine — the parser. Reads a stream of tokens and writes the
 * parse tree as XML.
 *
 * HOW IT WORKS
 * It is a recursive-descent parser, which is the plainest kind there is: there
 * is one method per rule of the Jack grammar, and a rule that mentions another
 * rule simply calls that rule's method. compileWhile calls compileExpression
 * and compileStatements because the grammar for a while statement mentions an
 * expression and some statements. The shape of the code and the shape of the
 * grammar are the same shape, which is the whole appeal.
 *
 * The grammar needs no backtracking: at every point, the current token (plus,
 * in exactly one place, the token after it) is enough to decide what comes
 * next. That one place is a term beginning with an identifier, where only the
 * following token distinguishes a variable from an array entry from a call.
 *
 * WHICH RULES GET A TAG
 * Not every rule appears in the output. The project asks for tags around the
 * structural rules only — class, classVarDec, subroutineDec, parameterList,
 * subroutineBody, varDec, statements, the five statements, expression, term
 * and expressionList. The rules that exist only to give something a name —
 * type, className, subroutineName, varName, statement, op, unaryOp,
 * keywordConstant — and subroutineCall emit their tokens inline, with no
 * wrapper. So compileSubroutineCall below writes no tag of its own, while
 * compileParameterList writes its tags even when there are no parameters.
 */
public class CompilationEngine {

    /** The nine binary operators. */
    private static final String OPERATORS = "+-*/&|<>=";

    private final JackTokenizer tokenizer;
    private final XmlWriter xml;

    public CompilationEngine(JackTokenizer tokenizer, XmlWriter xml) {
        this.tokenizer = tokenizer;
        this.xml = xml;
    }

    // =====================================================================
    //  class
    // =====================================================================

    /**
     * class: 'class' className '{' classVarDec* subroutineDec* '}'
     *
     * The entry point, and the only method the driver calls. It positions the
     * tokenizer on the first token itself, so callers do not have to.
     */
    public void compileClass() throws IOException {
        tokenizer.advance();                    // onto the first token

        xml.open("class");
        expectKeyword(Keyword.CLASS);
        expectIdentifier();                     // className
        expectSymbol('{');

        // Both loops are driven purely by the current token: a class body is a
        // run of variable declarations followed by a run of subroutines.
        while (isKeyword(Keyword.STATIC) || isKeyword(Keyword.FIELD)) {
            compileClassVarDec();
        }
        while (isKeyword(Keyword.CONSTRUCTOR)
                || isKeyword(Keyword.FUNCTION)
                || isKeyword(Keyword.METHOD)) {
            compileSubroutine();
        }

        expectSymbol('}');
        xml.close("class");

        // A Jack file holds exactly one class, so anything still left over is
        // not part of the program.
        if (!tokenizer.atEnd()) {
            throw syntaxError("the end of the file");
        }
    }

    /** classVarDec: ('static'|'field') type varName (',' varName)* ';' */
    public void compileClassVarDec() throws IOException {
        xml.open("classVarDec");
        expectKeyword(Keyword.STATIC, Keyword.FIELD);
        compileType();
        expectIdentifier();                     // varName
        while (isSymbol(',')) {
            expectSymbol(',');
            expectIdentifier();
        }
        expectSymbol(';');
        xml.close("classVarDec");
    }

    /**
     * type: 'int' | 'char' | 'boolean' | className
     *
     * No tag of its own — whichever token it is goes straight into the output.
     */
    private void compileType() throws IOException {
        if (isKeyword(Keyword.INT) || isKeyword(Keyword.CHAR) || isKeyword(Keyword.BOOLEAN)) {
            emit();
        } else if (tokenizer.tokenType() == TokenType.IDENTIFIER) {
            emit();                             // a class name
        } else {
            throw syntaxError("a type");
        }
    }

    // =====================================================================
    //  subroutines
    // =====================================================================

    /**
     * subroutineDec:
     *     ('constructor'|'function'|'method') ('void'|type)
     *     subroutineName '(' parameterList ')' subroutineBody
     */
    public void compileSubroutine() throws IOException {
        xml.open("subroutineDec");
        expectKeyword(Keyword.CONSTRUCTOR, Keyword.FUNCTION, Keyword.METHOD);

        if (isKeyword(Keyword.VOID)) {
            emit();
        } else {
            compileType();
        }

        expectIdentifier();                     // subroutineName

        // The parentheses belong to this rule, not to parameterList: the tags
        // go around the parameters only.
        expectSymbol('(');
        compileParameterList();
        expectSymbol(')');

        compileSubroutineBody();
        xml.close("subroutineDec");
    }

    /**
     * parameterList: ((type varName) (',' type varName)*)?
     *
     * The whole rule is optional, but the tags are not: an empty parameter list
     * still writes its opening and closing tag.
     */
    public void compileParameterList() throws IOException {
        xml.open("parameterList");
        if (!isSymbol(')')) {                   // ')' is the only thing that can follow
            compileType();
            expectIdentifier();
            while (isSymbol(',')) {
                expectSymbol(',');
                compileType();
                expectIdentifier();
            }
        }
        xml.close("parameterList");
    }

    /** subroutineBody: '{' varDec* statements '}' */
    public void compileSubroutineBody() throws IOException {
        xml.open("subroutineBody");
        expectSymbol('{');
        while (isKeyword(Keyword.VAR)) {
            compileVarDec();
        }
        compileStatements();
        expectSymbol('}');
        xml.close("subroutineBody");
    }

    /** varDec: 'var' type varName (',' varName)* ';' */
    public void compileVarDec() throws IOException {
        xml.open("varDec");
        expectKeyword(Keyword.VAR);
        compileType();
        expectIdentifier();
        while (isSymbol(',')) {
            expectSymbol(',');
            expectIdentifier();
        }
        expectSymbol(';');
        xml.close("varDec");
    }

    // =====================================================================
    //  statements
    // =====================================================================

    /**
     * statements: statement*
     *
     * Like parameterList, the tags are written even when there are no
     * statements at all. The loop ends at the first token that cannot begin a
     * statement, which in a well-formed program is always '}'.
     */
    public void compileStatements() throws IOException {
        xml.open("statements");
        boolean more = true;
        while (more && tokenizer.tokenType() == TokenType.KEYWORD) {
            switch (tokenizer.keyWord()) {
                case LET    -> compileLet();
                case IF     -> compileIf();
                case WHILE  -> compileWhile();
                case DO     -> compileDo();
                case RETURN -> compileReturn();
                default     -> more = false;
            }
        }
        xml.close("statements");
    }

    /** letStatement: 'let' varName ('[' expression ']')? '=' expression ';' */
    public void compileLet() throws IOException {
        xml.open("letStatement");
        expectKeyword(Keyword.LET);
        expectIdentifier();                     // varName
        if (isSymbol('[')) {                    // an array entry on the left
            expectSymbol('[');
            compileExpression();
            expectSymbol(']');
        }
        expectSymbol('=');
        compileExpression();
        expectSymbol(';');
        xml.close("letStatement");
    }

    /**
     * ifStatement:
     *     'if' '(' expression ')' '{' statements '}' ('else' '{' statements '}')?
     *
     * The else is attached to the nearest if simply by being parsed here,
     * before this call returns.
     */
    public void compileIf() throws IOException {
        xml.open("ifStatement");
        expectKeyword(Keyword.IF);
        expectSymbol('(');
        compileExpression();
        expectSymbol(')');
        expectSymbol('{');
        compileStatements();
        expectSymbol('}');
        if (isKeyword(Keyword.ELSE)) {
            expectKeyword(Keyword.ELSE);
            expectSymbol('{');
            compileStatements();
            expectSymbol('}');
        }
        xml.close("ifStatement");
    }

    /** whileStatement: 'while' '(' expression ')' '{' statements '}' */
    public void compileWhile() throws IOException {
        xml.open("whileStatement");
        expectKeyword(Keyword.WHILE);
        expectSymbol('(');
        compileExpression();
        expectSymbol(')');
        expectSymbol('{');
        compileStatements();
        expectSymbol('}');
        xml.close("whileStatement");
    }

    /** doStatement: 'do' subroutineCall ';' */
    public void compileDo() throws IOException {
        xml.open("doStatement");
        expectKeyword(Keyword.DO);
        compileSubroutineCall();
        expectSymbol(';');
        xml.close("doStatement");
    }

    /** returnStatement: 'return' expression? ';' */
    public void compileReturn() throws IOException {
        xml.open("returnStatement");
        expectKeyword(Keyword.RETURN);
        if (!isSymbol(';')) {                   // 'return;' versus 'return x;'
            compileExpression();
        }
        expectSymbol(';');
        xml.close("returnStatement");
    }

    // =====================================================================
    //  expressions
    // =====================================================================

    /**
     * expression: term (op term)*
     *
     * Note what is missing: precedence. Jack deliberately has none — a*b+c is
     * (a*b)+c only because it is evaluated strictly left to right — so the
     * parser needs no precedence climbing and a flat loop is exactly right.
     */
    public void compileExpression() throws IOException {
        xml.open("expression");
        compileTerm();
        while (tokenizer.tokenType() == TokenType.SYMBOL
                && OPERATORS.indexOf(tokenizer.symbol()) >= 0) {
            emit();                             // the operator
            compileTerm();
        }
        xml.close("expression");
    }

    /**
     * term: integerConstant | stringConstant | keywordConstant
     *     | varName | varName '[' expression ']' | subroutineCall
     *     | '(' expression ')' | unaryOp term
     *
     * This is the one rule that needs to look past the current token. An
     * identifier alone could begin three different things, and only the token
     * after it says which:
     *
     *      count          a variable
     *      count[i]       an array entry       -> next token is [
     *      count()        a call               -> next token is (
     *      Screen.draw()  a qualified call     -> next token is .
     */
    public void compileTerm() throws IOException {
        xml.open("term");

        switch (tokenizer.tokenType()) {
            case INT_CONST, STRING_CONST -> emit();

            case KEYWORD -> {
                // Only true, false, null and this are terms.
                Keyword k = tokenizer.keyWord();
                if (k != Keyword.TRUE && k != Keyword.FALSE
                        && k != Keyword.NULL && k != Keyword.THIS) {
                    throw syntaxError("a term");
                }
                emit();
            }

            case SYMBOL -> {
                char c = tokenizer.symbol();
                if (c == '(') {
                    expectSymbol('(');
                    compileExpression();
                    expectSymbol(')');
                } else if (c == '-' || c == '~') {
                    emit();                     // the unary operator
                    compileTerm();              // and what it applies to
                } else {
                    throw syntaxError("a term");
                }
            }

            case IDENTIFIER -> {
                char next = tokenizer.peekSymbol();
                if (next == '[') {
                    emit();                     // varName
                    expectSymbol('[');
                    compileExpression();
                    expectSymbol(']');
                } else if (next == '(' || next == '.') {
                    compileSubroutineCall();
                } else {
                    emit();                     // a plain variable
                }
            }
        }

        xml.close("term");
    }

    /**
     * subroutineCall:
     *       subroutineName '(' expressionList ')'
     *     | (className|varName) '.' subroutineName '(' expressionList ')'
     *
     * Deliberately writes no tag of its own: the project's XML has no
     * subroutineCall element, so these tokens appear directly inside whatever
     * contains them — a doStatement or a term.
     */
    private void compileSubroutineCall() throws IOException {
        expectIdentifier();                     // subroutineName, className or varName
        if (isSymbol('.')) {
            expectSymbol('.');
            expectIdentifier();                 // subroutineName
        }
        expectSymbol('(');
        compileExpressionList();
        expectSymbol(')');
    }

    /** expressionList: (expression (',' expression)*)? */
    public void compileExpressionList() throws IOException {
        xml.open("expressionList");
        if (!isSymbol(')')) {                   // ')' closes an empty list
            compileExpression();
            while (isSymbol(',')) {
                expectSymbol(',');
                compileExpression();
            }
        }
        xml.close("expressionList");
    }

    // =====================================================================
    //  token handling
    // =====================================================================

    /** Writes the current token and moves to the next one. */
    private void emit() throws IOException {
        xml.token(tokenizer.current());
        tokenizer.advance();
    }

    private boolean isSymbol(char c) {
        return tokenizer.tokenType() == TokenType.SYMBOL && tokenizer.symbol() == c;
    }

    private boolean isKeyword(Keyword k) {
        return tokenizer.tokenType() == TokenType.KEYWORD && tokenizer.keyWord() == k;
    }

    private void expectSymbol(char c) throws IOException {
        if (!isSymbol(c)) {
            throw syntaxError("'" + c + "'");
        }
        emit();
    }

    private void expectKeyword(Keyword... allowed) throws IOException {
        for (Keyword k : allowed) {
            if (isKeyword(k)) {
                emit();
                return;
            }
        }
        StringBuilder wanted = new StringBuilder();
        for (int i = 0; i < allowed.length; i++) {
            if (i > 0) {
                wanted.append(i == allowed.length - 1 ? " or " : ", ");
            }
            wanted.append('\'').append(allowed[i].name().toLowerCase()).append('\'');
        }
        throw syntaxError(wanted.toString());
    }

    private void expectIdentifier() throws IOException {
        if (tokenizer.tokenType() != TokenType.IDENTIFIER) {
            throw syntaxError("an identifier");
        }
        emit();
    }

    /** Reports what was expected, what was found, and on which line. */
    private IllegalArgumentException syntaxError(String expected) {
        JackTokenizer.Token token = tokenizer.current();
        String found = token.type == TokenType.EOF
                ? "the file ended"
                : "found '" + token.text + "'";
        return new IllegalArgumentException(
                "line " + token.line + ": expected " + expected + " but " + found);
    }
}
