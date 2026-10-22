JACK SYNTAX ANALYZER
nand2tetris, project 10
==============================================================================

Parses Jack source and writes its parse tree as XML.

    javac *.java
    java JackAnalyzer Prog.jack      # writes Prog.xml and ProgT.xml
    java JackAnalyzer DirName        # does that for every .jack inside

Two files come out per class. ProgT.xml is the flat token stream and Prog.xml
is the parse tree; the first is scaffolding, kept because the project supplies
a comparison file for it and because it lets the tokenizer be checked on its
own — when a parse tree is wrong, it is worth knowing straight away whether the
tokens underneath it were right.


THE CLASSES
-----------------------------------------------------------------------------
    JackAnalyzer       the entry point: which files to read, what to write
    JackTokenizer      text -> tokens. Knows what a token is, not what a
                       program is
    CompilationEngine  tokens -> parse tree. A recursive-descent parser: one
                       method per grammar rule
    XmlWriter          the output format: indentation, escaping, line endings
    TokenType          the five kinds of token, plus an end-of-input marker
    Keyword            the 21 reserved words

The split between the tokenizer and the parser is the point of the design. The
tokenizer can tell you that "let class ;" is three tokens; only the parser can
tell you it is not a program.


TWO THINGS WORTH POINTING AT
-----------------------------------------------------------------------------
Lookahead. The grammar needs none anywhere except in one place: a term that
begins with an identifier. Only the token AFTER it says which of three things
it is —

    count          a variable
    count[i]       an array entry          next token is [
    count(...)     a call                  next token is (
    Screen.draw()  a qualified call        next token is .

so the tokenizer offers peekSymbol() and nothing else needs to backtrack.

The end of the file is a token. The tokenizer hands out an EOF marker once the
real tokens run out, instead of parking the cursor on the last one. Parking is
the obvious thing to do and it is wrong: whatever the parser expects next gets
offered that same stranded token again, so a class missing its final brace is
accepted and the brace is written to the output twice. Making the end of the
input something the parser can see turns "the file stopped early" into an
ordinary syntax error with a line number.


ERRORS
-----------------------------------------------------------------------------
Every error names the file, the line, what was expected and what was found:

    Main.jack: line 5: expected ';' but found 'return'
    Main.jack: line 4: expected '}' but the file ended
    Main.jack:4: integer constant 32768 is larger than 32767
    Main.jack:3: string constant is not closed before the end of the line
