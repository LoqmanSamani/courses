import java.io.IOException;

/**
 * CompilationEngine — parses Jack and emits VM code as it goes.
 *
 * It is the same recursive-descent parser as project 10: one method per rule of
 * the grammar, a rule that mentions another rule calls that rule's method, and
 * no backtracking anywhere. What changed is what the methods do when they get
 * there. Instead of writing a tag, each one now writes the VM commands that
 * carry out the construct it just recognised. Nothing is kept: the code comes
 * out during the single pass over the tokens, which is possible because Jack
 * was designed so that it can be.
 *
 * THREE IDEAS DO ALL THE WORK
 *
 * 1. EVERY EXPRESSION LEAVES ITS VALUE ON THE STACK. That one rule is why the
 *    translation is so short. compileExpression does not need to know what its
 *    caller wants; the caller does not need to know what the expression was.
 *    `let x = a + b` is "whatever compiles the right-hand side, then pop into
 *    x", and that is true whether the right-hand side is a constant or a nest
 *    of calls twenty deep.
 *
 * 2. OPERATORS ARE EMITTED AFTER THEIR OPERANDS. Jack is written infix and the
 *    VM is a stack machine, so `a + b` becomes push a, push b, add. The parser
 *    gets this for free simply by writing the operator after recursing into the
 *    second operand.
 *
 * 3. A VARIABLE IS A SEGMENT AND AN INDEX, and the symbol table knows which.
 *    Once a name has been looked up there, generating code for it is one push
 *    or one pop.
 *
 * WHAT THE COMPILER HAS TO DECIDE THAT THE PARSER DID NOT
 * The analyzer could treat every identifier alike. Here, three decisions turn
 * on what a name actually denotes, and all three go through the symbol table:
 * which segment a variable lives in; whether `Foo.bar()` is a method on an
 * object or a function of a class; and how many fields an object needs when a
 * constructor allocates it.
 */
public class CompilationEngine {

    /** The nine binary operators, in the order their VM commands appear below. */
    private static final String OPERATORS = "+-*/&|<>=";

    private final JackTokenizer tokenizer;
    private final VMWriter vm;
    private final SymbolTable symbols = new SymbolTable();

    private String className;         // the class being compiled
    private int labelCounter;         // makes the labels of one subroutine unique

    public CompilationEngine(JackTokenizer tokenizer, VMWriter vm) {
        this.tokenizer = tokenizer;
        this.vm = vm;
    }

    // =====================================================================
    //  class
    // =====================================================================

    /**
     * class: 'class' className '{' classVarDec* subroutineDec* '}'
     *
     * The class name is remembered because every subroutine compiled below is
     * named after it — the VM has one flat namespace, so Main.main and
     * Square.main have to be told apart by the compiler.
     */
    public void compileClass() throws IOException {
        tokenizer.advance();

        expectKeyword(Keyword.CLASS);
        className = expectIdentifier();
        vm.writeComment("class " + className);
        expectSymbol('{');

        // The field and static declarations must all be read before any
        // subroutine is compiled: a method may use a field declared below it,
        // and a constructor needs the final field count to size the object.
        while (isKeyword(Keyword.STATIC) || isKeyword(Keyword.FIELD)) {
            compileClassVarDec();
        }
        while (isKeyword(Keyword.CONSTRUCTOR)
                || isKeyword(Keyword.FUNCTION)
                || isKeyword(Keyword.METHOD)) {
            compileSubroutine();
        }

        expectSymbol('}');
        if (!tokenizer.atEnd()) {
            throw syntaxError("the end of the file");
        }
    }

    /**
     * classVarDec: ('static'|'field') type varName (',' varName)* ';'
     *
     * Declarations emit no code at all. They only teach the symbol table, which
     * is what lets the statements further down emit code.
     */
    public void compileClassVarDec() throws IOException {
        Keyword word = expectKeyword(Keyword.STATIC, Keyword.FIELD);
        VarKind kind = (word == Keyword.STATIC) ? VarKind.STATIC : VarKind.FIELD;
        String type = compileType();
        symbols.define(expectIdentifier(), type, kind);
        while (isSymbol(',')) {
            expectSymbol(',');
            symbols.define(expectIdentifier(), type, kind);
        }
        expectSymbol(';');
    }

    /** type: 'int' | 'char' | 'boolean' | className. Returns the type's name. */
    private String compileType() throws IOException {
        if (isKeyword(Keyword.INT) || isKeyword(Keyword.CHAR) || isKeyword(Keyword.BOOLEAN)) {
            return consume();
        }
        if (tokenizer.tokenType() == TokenType.IDENTIFIER) {
            return consume();
        }
        throw syntaxError("a type");
    }

    // =====================================================================
    //  subroutines
    // =====================================================================

    /**
     * subroutineDec:
     *     ('constructor'|'function'|'method') ('void'|type)
     *     subroutineName '(' parameterList ')' subroutineBody
     *
     * The three kinds differ in exactly one respect — what `this` is when the
     * body starts running — and that difference is settled in a few lines at
     * the top of the body. Everything after it is identical.
     */
    public void compileSubroutine() throws IOException {
        Keyword kind = expectKeyword(Keyword.CONSTRUCTOR, Keyword.FUNCTION, Keyword.METHOD);

        symbols.startSubroutine();
        labelCounter = 0;

        // A method's first argument is the object it was called on. Declaring it
        // here, before the parameter list is read, is what makes the declared
        // parameters start at argument 1 — and it is why a method call has to
        // push the object before the arguments.
        if (kind == Keyword.METHOD) {
            symbols.define("this", className, VarKind.ARG);
        }

        if (isKeyword(Keyword.VOID)) {
            consume();
        } else {
            compileType();
        }
        String name = expectIdentifier();

        expectSymbol('(');
        compileParameterList();
        expectSymbol(')');

        compileSubroutineBody(kind, name);
    }

    /** parameterList: ((type varName) (',' type varName)*)? */
    public void compileParameterList() throws IOException {
        if (isSymbol(')')) {
            return;
        }
        String type = compileType();
        symbols.define(expectIdentifier(), type, VarKind.ARG);
        while (isSymbol(',')) {
            expectSymbol(',');
            type = compileType();
            symbols.define(expectIdentifier(), type, VarKind.ARG);
        }
    }

    /**
     * subroutineBody: '{' varDec* statements '}'
     *
     * The `function` command has to state how many locals to reserve, and that
     * is not known until every var declaration has been read. Jack puts them all
     * at the top of the body, so reading them first and only then writing the
     * header costs nothing — the language is shaped to let a one-pass compiler
     * work.
     */
    private void compileSubroutineBody(Keyword kind, String name) throws IOException {
        expectSymbol('{');
        while (isKeyword(Keyword.VAR)) {
            compileVarDec();
        }

        String qualified = className + "." + name;
        vm.writeComment(kindWord(kind) + " " + qualified);
        vm.writeFunction(qualified, symbols.varCount(VarKind.VAR));

        if (kind == Keyword.CONSTRUCTOR) {
            // Ask the OS for as many words as the class has fields, and make
            // the result `this`. The object does not exist until this runs.
            vm.writePush(VMSegment.CONSTANT, symbols.varCount(VarKind.FIELD));
            vm.writeCall("Memory.alloc", 1);
            vm.writePop(VMSegment.POINTER, 0);
        } else if (kind == Keyword.METHOD) {
            // The object was passed as argument 0; point `this` at it, so that
            // every field access below becomes an ordinary `this` segment access.
            vm.writePush(VMSegment.ARGUMENT, 0);
            vm.writePop(VMSegment.POINTER, 0);
        }
        // A function has no `this`, and must not touch pointer 0.

        compileStatements();
        expectSymbol('}');
    }

    /** varDec: 'var' type varName (',' varName)* ';' */
    public void compileVarDec() throws IOException {
        expectKeyword(Keyword.VAR);
        String type = compileType();
        symbols.define(expectIdentifier(), type, VarKind.VAR);
        while (isSymbol(',')) {
            expectSymbol(',');
            symbols.define(expectIdentifier(), type, VarKind.VAR);
        }
        expectSymbol(';');
    }

    // =====================================================================
    //  statements
    // =====================================================================

    /** statements: statement* */
    public void compileStatements() throws IOException {
        boolean more = true;
        while (more && tokenizer.tokenType() == TokenType.KEYWORD) {
            switch (tokenizer.keyWord()) {
                case LET:    compileLet();    break;
                case IF:     compileIf();     break;
                case WHILE:  compileWhile();  break;
                case DO:     compileDo();     break;
                case RETURN: compileReturn(); break;
                default:     more = false;    break;
            }
        }
    }

    /**
     * letStatement: 'let' varName ('[' expression ']')? '=' expression ';'
     *
     * The plain case is one pop. The array case is the one piece of code
     * generation in the whole compiler that needs a scratch register, and it is
     * worth seeing why.
     *
     * The obvious order — work out the address, point THAT at it, evaluate the
     * right-hand side, store — is wrong, because evaluating the right-hand side
     * may itself contain an array access, and that would move THAT somewhere
     * else before the store happens. `let a[b[i]] = c[j]` breaks exactly there.
     *
     * So the address is computed first and left on the stack, the value is
     * computed on top of it, and only then, with no more expressions left to
     * evaluate, is the value parked in temp 0 so that THAT can safely be set.
     */
    public void compileLet() throws IOException {
        expectKeyword(Keyword.LET);
        String name = expectIdentifier();
        vm.writeComment("let " + name);

        boolean intoArray = isSymbol('[');
        if (intoArray) {
            pushVariable(name);              // the array's base address
            expectSymbol('[');
            compileExpression();             // the index
            expectSymbol(']');
            vm.writeArithmetic(VMCommand.ADD); // base + index = the slot's address
        }

        expectSymbol('=');
        compileExpression();                 // the value, on top of the address
        expectSymbol(';');

        if (intoArray) {
            vm.writePop(VMSegment.TEMP, 0);     // park the value
            vm.writePop(VMSegment.POINTER, 1);  // now it is safe to aim THAT
            vm.writePush(VMSegment.TEMP, 0);
            vm.writePop(VMSegment.THAT, 0);
        } else {
            popVariable(name);
        }
    }

    /**
     * ifStatement:
     *     'if' '(' expression ')' '{' statements '}' ('else' '{' statements '}')?
     *
     * The condition is negated and used to jump *past* the then-branch, which
     * is one instruction shorter than jumping into it and reads the same way:
     *
     *      <condition>          not          if-goto IF_FALSE
     *      <then>               goto IF_END
     *      label IF_FALSE       <else>
     *      label IF_END
     *
     * With no else, the trailing goto and label are not emitted at all.
     */
    public void compileIf() throws IOException {
        expectKeyword(Keyword.IF);
        int id = labelCounter++;
        String falseLabel = "IF_FALSE" + id;
        String endLabel = "IF_END" + id;

        vm.writeComment("if");
        expectSymbol('(');
        compileExpression();
        expectSymbol(')');
        vm.writeArithmetic(VMCommand.NOT);
        vm.writeIf(falseLabel);

        expectSymbol('{');
        compileStatements();
        expectSymbol('}');

        if (isKeyword(Keyword.ELSE)) {
            vm.writeGoto(endLabel);
            vm.writeLabel(falseLabel);
            expectKeyword(Keyword.ELSE);
            expectSymbol('{');
            compileStatements();
            expectSymbol('}');
            vm.writeLabel(endLabel);
        } else {
            vm.writeLabel(falseLabel);
        }
    }

    /**
     * whileStatement: 'while' '(' expression ')' '{' statements '}'
     *
     * The condition is re-evaluated at the top of every pass, so its label has
     * to come before it rather than before the body.
     */
    public void compileWhile() throws IOException {
        expectKeyword(Keyword.WHILE);
        int id = labelCounter++;
        String topLabel = "WHILE_EXP" + id;
        String endLabel = "WHILE_END" + id;

        vm.writeComment("while");
        vm.writeLabel(topLabel);
        expectSymbol('(');
        compileExpression();
        expectSymbol(')');
        vm.writeArithmetic(VMCommand.NOT);
        vm.writeIf(endLabel);

        expectSymbol('{');
        compileStatements();
        expectSymbol('}');

        vm.writeGoto(topLabel);
        vm.writeLabel(endLabel);
    }

    /**
     * doStatement: 'do' subroutineCall ';'
     *
     * Every VM function returns a value, including the ones Jack declares void,
     * so the caller of a `do` has to throw one away or the stack would grow by
     * one on every call until it collided with the heap. temp 0 is the usual
     * bin for it.
     */
    public void compileDo() throws IOException {
        expectKeyword(Keyword.DO);
        vm.writeComment("do");
        compileSubroutineCall();
        expectSymbol(';');
        vm.writePop(VMSegment.TEMP, 0);
    }

    /**
     * returnStatement: 'return' expression? ';'
     *
     * A void subroutine still has to return something, for the reason above, so
     * a bare `return` pushes a zero first.
     */
    public void compileReturn() throws IOException {
        expectKeyword(Keyword.RETURN);
        vm.writeComment("return");
        if (isSymbol(';')) {
            vm.writePush(VMSegment.CONSTANT, 0);
        } else {
            compileExpression();
        }
        expectSymbol(';');
        vm.writeReturn();
    }

    // =====================================================================
    //  expressions
    // =====================================================================

    /**
     * expression: term (op term)*
     *
     * Jack has no operator precedence — a+b*c is (a+b)*c, strictly left to
     * right — so this flat loop is not a simplification, it is the language.
     * Each operator is written after the term to its right, which is exactly
     * what turns infix source into postfix stack code.
     */
    public void compileExpression() throws IOException {
        compileTerm();
        while (tokenizer.tokenType() == TokenType.SYMBOL
                && OPERATORS.indexOf(tokenizer.symbol()) >= 0) {
            char op = tokenizer.symbol();
            consume();
            compileTerm();
            writeOperator(op);
        }
    }

    /**
     * term: integerConstant | stringConstant | keywordConstant
     *     | varName | varName '[' expression ']' | subroutineCall
     *     | '(' expression ')' | unaryOp term
     *
     * As in project 10, the one place that needs to see past the current token
     * is a term starting with an identifier: only the next token says whether it
     * is a variable, an array entry or a call.
     */
    public void compileTerm() throws IOException {
        switch (tokenizer.tokenType()) {

            case INT_CONST:
                vm.writePush(VMSegment.CONSTANT, tokenizer.intVal());
                consume();
                break;

            case STRING_CONST:
                writeStringConstant(tokenizer.stringVal());
                consume();
                break;

            case KEYWORD: {
                Keyword k = tokenizer.keyWord();
                if (k == Keyword.TRUE) {
                    // true is -1, which is 0 with every bit flipped. Representing
                    // it this way is what makes the bitwise and/or double as
                    // logical operators everywhere else.
                    vm.writePush(VMSegment.CONSTANT, 0);
                    vm.writeArithmetic(VMCommand.NOT);
                } else if (k == Keyword.FALSE || k == Keyword.NULL) {
                    vm.writePush(VMSegment.CONSTANT, 0);
                } else if (k == Keyword.THIS) {
                    vm.writePush(VMSegment.POINTER, 0);
                } else {
                    throw syntaxError("a term");
                }
                consume();
                break;
            }

            case SYMBOL: {
                char c = tokenizer.symbol();
                if (c == '(') {
                    expectSymbol('(');
                    compileExpression();
                    expectSymbol(')');
                } else if (c == '-') {
                    consume();
                    compileTerm();
                    vm.writeArithmetic(VMCommand.NEG);
                } else if (c == '~') {
                    consume();
                    compileTerm();
                    vm.writeArithmetic(VMCommand.NOT);
                } else {
                    throw syntaxError("a term");
                }
                break;
            }

            case IDENTIFIER: {
                char next = tokenizer.peekSymbol();
                if (next == '[') {
                    String name = expectIdentifier();
                    pushVariable(name);
                    expectSymbol('[');
                    compileExpression();
                    expectSymbol(']');
                    vm.writeArithmetic(VMCommand.ADD);
                    vm.writePop(VMSegment.POINTER, 1);   // aim THAT at the slot
                    vm.writePush(VMSegment.THAT, 0);     // and read it
                } else if (next == '(' || next == '.') {
                    compileSubroutineCall();
                } else {
                    pushVariable(expectIdentifier());
                }
                break;
            }

            default:
                throw syntaxError("a term");
        }
    }

    /**
     * subroutineCall:
     *       subroutineName '(' expressionList ')'
     *     | (className|varName) '.' subroutineName '(' expressionList ')'
     *
     * Three cases hide in two lines of grammar, and telling them apart is the
     * one place the compiler genuinely needs to know what a name means:
     *
     *   foo(x)          a method of this class, on this object.
     *                   Push `this`, then the arguments.
     *   point.draw(x)   `point` is a variable, so this is a method on the object
     *                   it holds. Push that object, then the arguments, and call
     *                   the method of the variable's DECLARED TYPE — not of
     *                   anything named `point`.
     *   Screen.draw(x)  `Screen` is not a variable, so it must be a class name,
     *                   and this is a function or constructor. Nothing extra is
     *                   pushed.
     *
     * The only thing separating the last two is whether the symbol table has
     * heard of the name.
     */
    private void compileSubroutineCall() throws IOException {
        String first = expectIdentifier();
        String target;
        int nArgs = 0;

        if (isSymbol('.')) {
            expectSymbol('.');
            String subroutine = expectIdentifier();
            if (symbols.kindOf(first) != VarKind.NONE) {
                pushVariable(first);                       // the receiving object
                target = symbols.typeOf(first) + "." + subroutine;
                nArgs = 1;
            } else {
                target = first + "." + subroutine;         // a class name
            }
        } else {
            vm.writePush(VMSegment.POINTER, 0);              // an implicit `this`
            target = className + "." + first;
            nArgs = 1;
        }

        expectSymbol('(');
        nArgs += compileExpressionList();
        expectSymbol(')');
        vm.writeCall(target, nArgs);
    }

    /** expressionList: (expression (',' expression)*)? Returns how many. */
    public int compileExpressionList() throws IOException {
        int count = 0;
        if (!isSymbol(')')) {
            compileExpression();
            count++;
            while (isSymbol(',')) {
                expectSymbol(',');
                compileExpression();
                count++;
            }
        }
        return count;
    }

    // =====================================================================
    //  code generation helpers
    // =====================================================================

    /**
     * Builds a string constant at run time.
     *
     * There are no string literals in the VM language and no data section to put
     * one in, so a literal becomes a short program: ask the OS for a String of
     * the right length, then append the characters one at a time. Every
     * appendChar returns the string itself, which is why the result is still on
     * the stack at the end and no stack juggling is needed in between.
     */
    private void writeStringConstant(String value) throws IOException {
        vm.writePush(VMSegment.CONSTANT, value.length());
        vm.writeCall("String.new", 1);
        for (int i = 0; i < value.length(); i++) {
            vm.writePush(VMSegment.CONSTANT, value.charAt(i));
            vm.writeCall("String.appendChar", 2);
        }
    }

    /**
     * The VM command for a Jack operator.
     *
     * Seven of the nine are single VM commands. Multiplication and division are
     * not: the Hack machine's ALU cannot do them, so they are OS calls, and this
     * is the point where that hardware limitation surfaces in the compiler.
     */
    private void writeOperator(char op) throws IOException {
        switch (op) {
            case '+': vm.writeArithmetic(VMCommand.ADD); break;
            case '-': vm.writeArithmetic(VMCommand.SUB); break;
            case '&': vm.writeArithmetic(VMCommand.AND); break;
            case '|': vm.writeArithmetic(VMCommand.OR);  break;
            case '<': vm.writeArithmetic(VMCommand.LT);  break;
            case '>': vm.writeArithmetic(VMCommand.GT);  break;
            case '=': vm.writeArithmetic(VMCommand.EQ);  break;
            case '*': vm.writeCall("Math.multiply", 2); break;
            case '/': vm.writeCall("Math.divide", 2);   break;
            default:  throw syntaxError("an operator");
        }
    }

    /** Pushes the value of a variable, whichever segment it lives in. */
    private void pushVariable(String name) throws IOException {
        vm.writePush(segmentOf(name), symbols.indexOf(name));
    }

    /** Pops the top of the stack into a variable. */
    private void popVariable(String name) throws IOException {
        vm.writePop(segmentOf(name), symbols.indexOf(name));
    }

    /**
     * Where a variable lives.
     *
     * Note that a field becomes `this`: field i of the current object is word i
     * of whatever pointer 0 points at, which is why setting pointer 0 at the top
     * of a method or constructor is all that is needed to make fields work.
     */
    private VMSegment segmentOf(String name) {
        VarKind kind = symbols.kindOf(name);
        switch (kind) {
            case STATIC: return VMSegment.STATIC;
            case FIELD:  return VMSegment.THIS;
            case ARG:    return VMSegment.ARGUMENT;
            case VAR:    return VMSegment.LOCAL;
            default:
                throw new IllegalArgumentException(
                        "line " + tokenizer.current().line
                                + ": '" + name + "' is not a declared variable");
        }
    }

    private static String kindWord(Keyword kind) {
        return kind.text();
    }

    // =====================================================================
    //  token handling
    // =====================================================================

    /** Returns the current token's text and moves on. */
    private String consume() {
        String text = tokenizer.current().text;
        tokenizer.advance();
        return text;
    }

    private boolean isSymbol(char c) {
        return tokenizer.tokenType() == TokenType.SYMBOL && tokenizer.symbol() == c;
    }

    private boolean isKeyword(Keyword k) {
        return tokenizer.tokenType() == TokenType.KEYWORD && tokenizer.keyWord() == k;
    }

    private void expectSymbol(char c) {
        if (!isSymbol(c)) {
            throw syntaxError("'" + c + "'");
        }
        consume();
    }

    /** Checks the current token against the allowed keywords and returns it. */
    private Keyword expectKeyword(Keyword... allowed) {
        for (int i = 0; i < allowed.length; i++) {
            if (isKeyword(allowed[i])) {
                Keyword found = allowed[i];
                consume();
                return found;
            }
        }
        StringBuilder wanted = new StringBuilder();
        for (int i = 0; i < allowed.length; i++) {
            if (i > 0) {
                wanted.append(i == allowed.length - 1 ? " or " : ", ");
            }
            wanted.append('\'').append(allowed[i].text()).append('\'');
        }
        throw syntaxError(wanted.toString());
    }

    private String expectIdentifier() {
        if (tokenizer.tokenType() != TokenType.IDENTIFIER) {
            throw syntaxError("an identifier");
        }
        return consume();
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
