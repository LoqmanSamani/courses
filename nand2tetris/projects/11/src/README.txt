JACK COMPILER
nand2tetris, project 11
==============================================================================

Compiles Jack source to VM code.

Written to the Java 8 language level, so it builds on an old JDK as well as a
current one.

    javac *.java
    java JackCompiler Prog.jack      # writes Prog.vm beside it
    java JackCompiler DirName        # compiles every .jack inside, each to
                                     # its own .vm

Nothing is linked or combined: each class compiles independently to its own
.vm file, and the names are tied together only at run time. That is why a class
can call a subroutine of a class this compiler has never seen -- the OS classes,
for instance.


THE CLASSES
-----------------------------------------------------------------------------
    JackCompiler       the entry point: which files to read, what to write
    JackTokenizer      text -> tokens (unchanged from project 10)
    CompilationEngine  tokens -> VM code. A recursive-descent parser that emits
                       code as it recognises each construct
    SymbolTable        what every identifier means: its kind, type and index
    VMWriter           the VM language: one method per command
    TokenType          the five kinds of token, plus an end-of-input marker
    Keyword            the 21 reserved words
    VarKind            static / field / arg / var, and NONE for "not a variable"
    VMSegment          the eight VM memory segments
    VMCommand          the nine VM arithmetic commands


WHAT CHANGED FROM PROJECT 10
-----------------------------------------------------------------------------
The parser is the same parser. What changed is what each method does once it
has recognised something: instead of writing an XML tag it writes the VM
commands that carry the construct out. Two things had to be added for that.

The symbol table. The analyzer could treat every identifier alike; a compiler
cannot. Three decisions turn on what a name actually denotes, and all three go
through the symbol table: which segment a variable lives in, whether Foo.bar()
is a method on an object or a function of a class, and how many fields an
object needs when a constructor allocates it.

The code generator. It rests on one rule -- every expression leaves its value
on the stack -- which is why the translation is so short. compileExpression
does not need to know what its caller wants and the caller does not need to
know what the expression was, so `let x = <anything>` is always "compile the
right-hand side, then pop into x".


THREE THINGS WORTH POINTING AT
-----------------------------------------------------------------------------
Array assignment needs a scratch register, and it is worth seeing why. The
obvious order -- work out the address, aim THAT at it, evaluate the right-hand
side, store -- is wrong, because the right-hand side may contain an array
access of its own, which would move THAT before the store happened.
`let a[b[i]] = c[j]` breaks exactly there. So the address is computed first and
left on the stack, the value is computed on top of it, and only then, with no
expressions left to evaluate, is the value parked in temp 0 so THAT can safely
be set.

Operators come out after their operands, with no precedence logic anywhere.
Jack has no precedence -- a+b*c is (a+b)*c, strictly left to right -- so a flat
loop is not a simplification, it is the language. Writing the operator after
recursing into the term to its right is what turns infix source into postfix
stack code.

Every function returns a value, including the ones Jack declares void. So a
`do` statement throws the result away into temp 0, and a bare `return` pushes a
zero first. Without that the stack would grow by one on every call.


ERRORS
-----------------------------------------------------------------------------
Every error names the file, the line, what was expected and what was found:

    Main.jack: line 5: expected ';' but found 'return'
    Main.jack: line 4: expected '}' but the file ended
    Main.jack: line 9: 'count' is not a declared variable
    Main.jack:4: integer constant 32768 is larger than 32767
