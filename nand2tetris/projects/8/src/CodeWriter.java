import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * CodeWriter — turns parsed VM commands into Hack assembly.
 *
 * All knowledge of the Hack platform lives here: the stack layout, the memory
 * segments, and — new in Project 8 — program flow and the function call
 * protocol. Parser knows none of this; it only hands over the pieces.
 *
 *
 * THE STACK
 * ---------
 * RAM[0], symbolically SP, holds the address of the next FREE stack slot. The
 * stack grows upward from address 256.
 *
 *      SP -> 258      <- next free slot
 *            257      <- topmost value  (often called y)
 *            256      <- the one below  (often called x)
 *
 * "push" = write at RAM[SP], then SP++.  "pop" = SP--, then read RAM[SP].
 * Every command below is built out of those two moves.
 *
 *
 * THE MEMORY SEGMENTS
 * -------------------
 *   POINTER-BASED (local, argument, this, that)
 *       RAM[1..4] hold each segment's base address, under the symbols
 *       LCL, ARG, THIS, THAT. "local 2" means RAM[ RAM[LCL] + 2 ].
 *
 *   FIXED (temp, pointer)
 *       temp 0..7   -> RAM[5..12] directly
 *       pointer 0/1 -> RAM[3]/RAM[4], i.e. THIS and THAT themselves
 *
 *   VIRTUAL (constant, static)
 *       constant i  -> the value i itself, not memory at all
 *       static i    -> the symbol <FileName>.i, allocated by the assembler
 *
 *
 * THE FUNCTION CALL PROTOCOL  (Project 8)
 * ---------------------------------------
 * When function f calls function g, a "stack frame" for g is built on top of
 * the stack. Laid out from low address to high:
 *
 *      ARG -> | arg 0        |   <- the arguments the caller already pushed
 *             | arg 1        |
 *             | ...          |
 *             | returnAddr   |   <- where to resume in the caller
 *             | saved LCL    |   <- the caller's four segment pointers,
 *             | saved ARG    |      so they can be restored on return
 *             | saved THIS   |
 *             | saved THAT   |
 *      LCL -> | local 0      |   <- g's locals, initialised to 0
 *             | local 1      |
 *             | ...          |
 *      SP  -> |              |   <- g's working stack grows from here
 *
 * The caller builds everything up to and including "saved THAT"; the callee's
 * `function` command pushes the zeroed locals. `return` tears it all down.
 *
 *
 * A NOTE ON THE GENERATED CODE
 * ----------------------------
 * Every VM command is preceded by a "// <the vm command>" comment, so the .asm
 * can be read side by side with the .vm source when debugging in the emulator.
 */
public class CodeWriter implements AutoCloseable {

    /** VM segment name -> the Hack symbol holding that segment's base address. */
    private static final Map<String, String> POINTER_SEGMENTS = Map.of(
            "local",    "LCL",
            "argument", "ARG",
            "this",     "THIS",
            "that",     "THAT");

    /** temp 0..7 live at RAM[5..12]. */
    private static final int TEMP_BASE = 5;
    private static final int TEMP_SIZE = 8;

    /** General-purpose scratch addresses, reserved for the translator's own use. */
    private static final String SCRATCH      = "R13";  // pop destination address
    private static final String FRAME_PTR    = "R14";  // return: walking frame pointer
    private static final String RETURN_ADDR  = "R15";  // return: saved return address

    /** Size of the saved-state block a call pushes: retAddr + LCL + ARG + THIS + THAT. */
    private static final int FRAME_SIZE = 5;

    private final BufferedWriter out;

    /**
     * Base name of the .vm file being translated. Used to build static-variable
     * symbols, and as the label scope when code sits outside any function.
     */
    private String currentFileName = "Static";

    /**
     * The function currently being translated. VM labels are local to their
     * function, so they are emitted as "<function>$<label>" to keep two
     * functions' identically-named labels apart.
     */
    private String currentFunction = "Static";

    /** Makes eq/gt/lt branch targets unique. */
    private int comparisonCounter = 0;

    /** Makes each call site's return label unique. */
    private int callCounter = 0;

    public CodeWriter(Path asmFile) throws IOException {
        this.out = Files.newBufferedWriter(asmFile, StandardCharsets.UTF_8);
    }

    /**
     * Tells the writer which .vm file the following commands come from.
     *
     * This matters for the static segment: two files may both use "static 0",
     * and those must be two DIFFERENT variables. Prefixing with the file name
     * keeps them apart — which is exactly what the StaticsTest program checks.
     */
    public void setFileName(String fileName) throws IOException {
        this.currentFileName = fileName;
        // Code outside any function still needs a label scope; the file name
        // serves as a sensible default until a `function` command appears.
        this.currentFunction = fileName;
        write("// ========== " + fileName + ".vm ==========");
    }

    // ------------------------------------------------------------------
    // Bootstrap  (Project 8)
    // ------------------------------------------------------------------

    /**
     * Emits the bootstrap code that must sit at the very top of a .asm file
     * produced from a full program (a directory of .vm files).
     *
     * On a real machine, something has to run first. This is that something:
     *
     *      SP = 256            establish the stack
     *      call Sys.init 0     hand control to the program's entry point
     *
     * Sys.init never returns — by convention it ends in an infinite loop — so
     * no return address is really needed, but we emit a proper call anyway so
     * that the stack frame layout is correct and uniform.
     *
     * This is deliberately NOT emitted for single-file translation, because the
     * supplied .tst scripts for those tests set SP, LCL and ARG themselves and
     * would be disrupted by a bootstrap.
     */
    public void writeInit() throws IOException {
        write("// ---------- bootstrap ----------");
        write("// SP = 256");
        write("@256");
        write("D=A");
        write("@SP");
        write("M=D");

        // Hand control to Sys.init via the normal calling sequence.
        writeCall("Sys.init", 0);
    }

    // ------------------------------------------------------------------
    // Arithmetic and logical commands
    // ------------------------------------------------------------------

    /**
     * Emits one of the nine arithmetic/logical commands.
     *
     *   BINARY (add, sub, and, or)  pop y, combine with the value still on top
     *   UNARY  (neg, not)           modify the top value in place; SP unchanged
     *   COMPARE (eq, gt, lt)        compute x - y, then branch on the result
     */
    public void writeArithmetic(String command) throws IOException {
        write("// " + command);

        switch (command) {
            case "add": binaryOp("M=D+M"); break;      // x + y
            case "sub": binaryOp("M=M-D"); break;      // x - y  (D is y, M is x)
            case "and": binaryOp("M=D&M"); break;
            case "or":  binaryOp("M=D|M"); break;
            case "neg": unaryOp("M=-M");   break;
            case "not": unaryOp("M=!M");   break;
            case "eq":  comparisonOp("JEQ"); break;
            case "gt":  comparisonOp("JGT"); break;
            case "lt":  comparisonOp("JLT"); break;
            default:
                throw new IllegalArgumentException("Unknown arithmetic command: " + command);
        }
    }

    /**
     * Shared skeleton for add / sub / and / or.
     *
     *      @SP
     *      AM=M-1      SP--, and point A at the new top (y's slot)
     *      D=M         D = y
     *      A=A-1       step down one slot: A now points at x
     *      <operation> combine D (y) with M (x), storing into x's slot
     *
     * SP already points just past x's slot, so x's slot IS the new top.
     */
    private void binaryOp(String operation) throws IOException {
        write("@SP");
        write("AM=M-1");
        write("D=M");
        write("A=A-1");
        write(operation);
    }

    /** Shared skeleton for neg / not. Stack size unchanged, so SP is untouched. */
    private void unaryOp(String operation) throws IOException {
        write("@SP");
        write("A=M-1");
        write(operation);
    }

    /**
     * Shared skeleton for eq / gt / lt, using "assume true, then correct".
     *
     * True is -1 (1111111111111111) rather than 1, so that the bitwise and/or/not
     * commands behave correctly on boolean values too.
     */
    private void comparisonOp(String jumpCondition) throws IOException {
        String trueLabel = "COMP_TRUE_" + comparisonCounter;
        comparisonCounter++;

        write("@SP");
        write("AM=M-1");             // SP--, A points at y
        write("D=M");                // D = y
        write("A=A-1");              // A points at x
        write("D=M-D");              // D = x - y
        write("M=-1");               // speculatively store true
        write("@" + trueLabel);
        write("D;" + jumpCondition); // condition holds? keep true
        write("@SP");
        write("A=M-1");
        write("M=0");                // otherwise overwrite with false
        write("(" + trueLabel + ")");
    }

    // ------------------------------------------------------------------
    // push / pop
    // ------------------------------------------------------------------

    public void writePushPop(Parser.CommandType type, String segment, int index)
            throws IOException {

        String verb = (type == Parser.CommandType.C_PUSH) ? "push" : "pop";
        write("// " + verb + " " + segment + " " + index);

        if (type == Parser.CommandType.C_PUSH) {
            writePush(segment, index);
        } else {
            writePop(segment, index);
        }
    }

    /**
     * push <segment> <index> — get the value into D, then push D.
     * Every branch differs only in HOW the value reaches D.
     */
    private void writePush(String segment, int index) throws IOException {

        if (segment.equals("constant")) {
            // The value is the index itself. D=A, not D=M: we want the number,
            // not the contents of RAM at that number.
            write("@" + index);
            write("D=A");

        } else if (POINTER_SEGMENTS.containsKey(segment)) {
            write("@" + index);
            write("D=A");                                 // D = index
            write("@" + POINTER_SEGMENTS.get(segment));
            write("A=M+D");                               // A = base + index
            write("D=M");                                 // D = value stored there

        } else if (segment.equals("temp")) {
            checkRange(index, TEMP_SIZE, "temp");
            write("@" + (TEMP_BASE + index));             // address known at translation time
            write("D=M");

        } else if (segment.equals("pointer")) {
            write("@" + pointerSymbol(index));            // THIS or THAT itself
            write("D=M");

        } else if (segment.equals("static")) {
            write("@" + currentFileName + "." + index);
            write("D=M");

        } else {
            throw new IllegalArgumentException("Unknown segment: " + segment);
        }

        pushD();
    }

    /**
     * pop <segment> <index> — remove the top value and store it.
     *
     * The pointer-based segments need care: the destination address must be
     * computed BEFORE popping, because both steps need the D register. The
     * address is therefore parked in a scratch register while the pop happens.
     */
    private void writePop(String segment, int index) throws IOException {

        if (POINTER_SEGMENTS.containsKey(segment)) {
            write("@" + index);
            write("D=A");                                 // D = index
            write("@" + POINTER_SEGMENTS.get(segment));
            write("D=M+D");                               // D = base + index
            write("@" + SCRATCH);
            write("M=D");                                 // park the destination

            popToD();                                     // D = the popped value

            write("@" + SCRATCH);
            write("A=M");                                 // A = destination
            write("M=D");

        } else if (segment.equals("temp")) {
            checkRange(index, TEMP_SIZE, "temp");
            popToD();
            write("@" + (TEMP_BASE + index));
            write("M=D");

        } else if (segment.equals("pointer")) {
            popToD();
            write("@" + pointerSymbol(index));
            write("M=D");

        } else if (segment.equals("static")) {
            popToD();
            write("@" + currentFileName + "." + index);
            write("M=D");

        } else if (segment.equals("constant")) {
            throw new IllegalArgumentException("Cannot pop to the constant segment");

        } else {
            throw new IllegalArgumentException("Unknown segment: " + segment);
        }
    }

    // ------------------------------------------------------------------
    // Program flow  (Project 8)
    // ------------------------------------------------------------------

    /**
     * label <name> — declares a jump target.
     *
     * The name is qualified with the enclosing function so that two functions
     * can each have a label called LOOP without colliding in the single flat
     * namespace the Hack assembler provides.
     */
    public void writeLabel(String label) throws IOException {
        write("// label " + label);
        write("(" + scopedLabel(label) + ")");
    }

    /**
     * goto <name> — unconditional jump.
     *
     * Exactly the two-line idiom from Project 4: load the target into A, then
     * jump. The comp field "0" is a placeholder; JMP ignores it.
     */
    public void writeGoto(String label) throws IOException {
        write("// goto " + label);
        write("@" + scopedLabel(label));
        write("0;JMP");
    }

    /**
     * if-goto <name> — pop the top value and jump if it is NOT zero.
     *
     * The VM defines false as 0 and true as -1, so "not zero" is the right test
     * and JNE is the right condition. Note the value is CONSUMED: if-goto always
     * pops, whether or not it jumps.
     */
    public void writeIf(String label) throws IOException {
        write("// if-goto " + label);
        popToD();                       // D = the condition value
        write("@" + scopedLabel(label));
        write("D;JNE");                 // jump when D != 0
    }

    /** Qualifies a VM label with its enclosing function: "Sys.main$LOOP". */
    private String scopedLabel(String label) {
        return currentFunction + "$" + label;
    }

    // ------------------------------------------------------------------
    // Function calling  (Project 8)
    // ------------------------------------------------------------------

    /**
     * function <name> <nVars> — marks the start of a function body.
     *
     * Two jobs:
     *   1. Emit the entry label, so that `call` has somewhere to jump to.
     *      Function names are globally unique, so no scoping prefix is needed.
     *   2. Push nVars zeros, which both allocates the local segment and
     *      initialises it. The VM spec guarantees locals start at 0, and the
     *      NestedCall test checks this explicitly by pre-filling the stack
     *      region with -1 beforehand.
     *
     * LCL was already set to the current SP by the caller, so the zeros land
     * exactly where the local segment begins.
     */
    public void writeFunction(String functionName, int nVars) throws IOException {
        write("// function " + functionName + " " + nVars);

        // From here on, labels inside this body belong to this function.
        currentFunction = functionName;

        write("(" + functionName + ")");

        for (int i = 0; i < nVars; i++) {
            write("// local " + i + " = 0");
            write("@SP");
            write("A=M");
            write("M=0");
            write("@SP");
            write("M=M+1");
        }
    }

    /**
     * call <name> <nArgs> — invoke a function.
     *
     * The caller's job, in order:
     *
     *   1. Push the return address (a label placed just after this call).
     *   2. Push LCL, ARG, THIS, THAT — the caller's state, to be restored later.
     *   3. Repoint ARG at the arguments the caller already pushed:
     *          ARG = SP - 5 - nArgs
     *      The 5 is the block just pushed in steps 1-2; stepping back over that
     *      and over the nArgs arguments lands on argument 0.
     *   4. Repoint LCL at the current SP, where the callee's locals will go.
     *   5. Jump to the function.
     *   6. Place the return label, so execution resumes here afterwards.
     *
     * The return label must be unique per call site — the same function may be
     * called from many places — hence the counter.
     */
    public void writeCall(String functionName, int nArgs) throws IOException {
        write("// call " + functionName + " " + nArgs);

        String returnLabel = functionName + "$ret." + callCounter;
        callCounter++;

        // 1. Push the return address. D=A because we want the label's ADDRESS,
        //    the number the assembler will substitute, not memory contents.
        write("// push return address");
        write("@" + returnLabel);
        write("D=A");
        pushD();

        // 2. Save the caller's four segment pointers.
        for (String symbol : new String[] {"LCL", "ARG", "THIS", "THAT"}) {
            write("// push " + symbol);
            write("@" + symbol);
            write("D=M");
            pushD();
        }

        // 3. ARG = SP - (5 + nArgs)
        write("// ARG = SP - " + (FRAME_SIZE + nArgs));
        write("@SP");
        write("D=M");
        write("@" + (FRAME_SIZE + nArgs));
        write("D=D-A");
        write("@ARG");
        write("M=D");

        // 4. LCL = SP
        write("// LCL = SP");
        write("@SP");
        write("D=M");
        write("@LCL");
        write("M=D");

        // 5. Transfer control.
        write("// goto " + functionName);
        write("@" + functionName);
        write("0;JMP");

        // 6. Execution resumes here when the callee returns.
        write("(" + returnLabel + ")");
    }

    /**
     * return — unwind the frame and resume in the caller.
     *
     * The sequence, using LCL as the anchor since every saved value sits at a
     * known offset below it:
     *
     *      frame   = LCL                 a walking pointer, kept in R14
     *      retAddr = *(frame - 5)        stashed in R15 BEFORE anything moves
     *      *ARG    = pop()               the return value goes where arg 0 was
     *      SP      = ARG + 1             so it becomes the caller's new top
     *      THAT    = *(frame - 1)        restore the caller's pointers,
     *      THIS    = *(frame - 2)        walking back down the saved block
     *      ARG     = *(frame - 3)
     *      LCL     = *(frame - 4)
     *      goto retAddr
     *
     * WHY retAddr IS SAVED FIRST — this is the classic bug in this project.
     * When nArgs is 0, ARG points at the very slot holding the return address.
     * The "*ARG = pop()" step would then overwrite it, and the function would
     * jump into nowhere. Reading it into R15 first makes the order safe.
     */
    public void writeReturn() throws IOException {
        write("// return");

        // frame = LCL
        write("// R14 = LCL (frame pointer)");
        write("@LCL");
        write("D=M");
        write("@" + FRAME_PTR);
        write("M=D");

        // retAddr = *(frame - 5)    — D still holds LCL here
        write("// R15 = *(frame-5) (return address)");
        write("@" + FRAME_SIZE);
        write("A=D-A");             // A = LCL - 5
        write("D=M");
        write("@" + RETURN_ADDR);
        write("M=D");

        // *ARG = pop()
        write("// *ARG = return value");
        popToD();
        write("@ARG");
        write("A=M");
        write("M=D");

        // SP = ARG + 1
        write("// SP = ARG + 1");
        write("@ARG");
        write("D=M+1");
        write("@SP");
        write("M=D");

        // Restore the caller's pointers by walking R14 downward.
        // "AM=M-1" decrements the frame pointer and addresses it in one step.
        restoreFromFrame("THAT");   // *(frame-1)
        restoreFromFrame("THIS");   // *(frame-2)
        restoreFromFrame("ARG");    // *(frame-3)
        restoreFromFrame("LCL");    // *(frame-4)

        // goto retAddr
        write("// goto return address");
        write("@" + RETURN_ADDR);
        write("A=M");
        write("0;JMP");
    }

    /**
     * Restores one saved segment pointer: steps the frame pointer in R14 down
     * by one and copies the value found there into the given symbol.
     */
    private void restoreFromFrame(String symbol) throws IOException {
        write("// " + symbol + " = *(--frame)");
        write("@" + FRAME_PTR);
        write("AM=M-1");            // R14--, and A = the new R14 value
        write("D=M");               // D = the saved pointer
        write("@" + symbol);
        write("M=D");
    }

    // ------------------------------------------------------------------
    // Reusable stack primitives
    // ------------------------------------------------------------------

    /**
     * Pushes whatever is in D onto the stack: write at RAM[SP], then SP++.
     */
    private void pushD() throws IOException {
        write("@SP");
        write("A=M");
        write("M=D");
        write("@SP");
        write("M=M+1");
    }

    /**
     * Pops the top of the stack into D: SP--, then read RAM[SP].
     *
     * "AM=M-1" does two jobs in one instruction — it stores the decremented
     * value back into SP *and* leaves it in A. That is the dest field "AM" from
     * the Hack spec: two destination bits set at once.
     */
    private void popToD() throws IOException {
        write("@SP");
        write("AM=M-1");
        write("D=M");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** pointer 0 -> THIS, pointer 1 -> THAT. */
    private String pointerSymbol(int index) {
        if (index == 0) {
            return "THIS";
        } else if (index == 1) {
            return "THAT";
        }
        throw new IllegalArgumentException("pointer index must be 0 or 1, got " + index);
    }

    /** Guards the fixed-size segments against an out-of-range index. */
    private void checkRange(int index, int size, String segmentName) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                    segmentName + " index must be 0.." + (size - 1) + ", got " + index);
        }
    }

    /** Writes one line of assembly. */
    private void write(String line) throws IOException {
        out.write(line);
        out.newLine();
    }

    /**
     * Appends a halt loop and closes the file.
     *
     * Hack programs cannot simply "end": once the last instruction completes the
     * PC keeps incrementing into whatever follows. A tight self-jump parks the
     * machine safely — the same trick the Project 4 programs used.
     *
     * Well-formed programs reach this only by falling off the end of top-level
     * code (as the single-file ProgramFlow tests do); a bootstrapped program
     * stays inside Sys.init's own infinite loop and never gets here.
     */
    @Override
    public void close() throws IOException {
        write("// ---------- end of program: halt ----------");
        write("(END_OF_PROGRAM)");
        write("@END_OF_PROGRAM");
        write("0;JMP");
        out.close();
    }
}
