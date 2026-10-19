import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * CodeWriter — turns parsed VM commands into Hack assembly.
 *
 * This is where all knowledge of the Hack platform lives: how the stack is laid
 * out, where each memory segment sits, and what instruction sequence implements
 * each VM operation. Parser knows none of this; it only hands over the pieces.
 *
 *
 * THE STACK
 * ---------
 * RAM[0], symbolically SP, holds the address of the next FREE stack slot. The
 * stack itself grows upward from address 256.
 *
 *      SP -> 258      <- next free slot
 *            257      <- topmost value  (often called y)
 *            256      <- the one below  (often called x)
 *
 * So "push" means: write at RAM[SP], then SP++. "pop" means: SP--, then read
 * RAM[SP]. Every single command below is built out of those two moves.
 *
 *
 * THE MEMORY SEGMENTS
 * -------------------
 * The VM presents eight segments. They are implemented three different ways:
 *
 *   POINTER-BASED (local, argument, this, that)
 *       RAM[1..4] hold the *base address* of each segment, under the symbols
 *       LCL, ARG, THIS, THAT. "local 2" means RAM[ RAM[LCL] + 2 ]. One extra
 *       indirection, so these need a scratch register to compute the address.
 *
 *   FIXED (temp, pointer)
 *       temp 0..7   -> RAM[5..12]   directly, no indirection
 *       pointer 0   -> RAM[3]  (THIS)   pointer 1 -> RAM[4]  (THAT)
 *       "pop pointer 0" is how a program sets its own this-segment base.
 *
 *   VIRTUAL (constant, static)
 *       constant i  -> not memory at all; the value i itself
 *       static i    -> the assembly symbol <FileName>.i, which the Hack
 *                      assembler will allocate somewhere from RAM[16] upward
 *
 *
 * A NOTE ON THE GENERATED CODE
 * ----------------------------
 * Every VM command is preceded in the output by a "// <the vm command>" comment,
 * so the .asm file can be read side by side with the .vm source when debugging
 * in the CPU emulator.
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

    /** A general-purpose scratch address, free for the translator to use. */
    private static final String SCRATCH = "R13";

    private final BufferedWriter out;

    /**
     * Base name of the .vm file currently being translated, used to build unique
     * static-variable symbols. Set by setFileName() before each input file.
     */
    private String currentFileName = "Static";

    /**
     * Makes every generated label unique. eq/gt/lt each need a branch target, and
     * a program may contain hundreds of them, so a simple counter is appended.
     */
    private int labelCounter = 0;

    public CodeWriter(Path asmFile) throws IOException {
        this.out = Files.newBufferedWriter(asmFile, StandardCharsets.UTF_8);
    }

    /**
     * Tells the writer which .vm file the following commands come from.
     *
     * This matters only for the static segment: two different files may both use
     * "static 0", and those must be two DIFFERENT variables. Prefixing with the
     * file name keeps them apart.
     */
    public void setFileName(String fileName) throws IOException {
        this.currentFileName = fileName;
        write("// ===== " + fileName + ".vm =====");
    }

    // ------------------------------------------------------------------
    // Arithmetic and logical commands
    // ------------------------------------------------------------------

    /**
     * Emits the implementation of one of the nine arithmetic/logical commands.
     *
     * They come in three shapes:
     *
     *   BINARY (add, sub, and, or)
     *       Pop y, then apply the operation to the value still sitting on top.
     *       The result replaces x, so the stack shrinks by exactly one.
     *
     *   UNARY (neg, not)
     *       Modify the top value in place. The stack does not change size, so
     *       SP never moves — we just address RAM[SP-1] directly.
     *
     *   COMPARISON (eq, gt, lt)
     *       Compute x - y, then branch on the ALU's verdict. The result must be
     *       the VM's booleans: -1 for true (all sixteen bits set) and 0 for false.
     */
    public void writeArithmetic(String command) throws IOException {
        write("// " + command);

        switch (command) {

            case "add":
                binaryOp("M=D+M");   // x + y
                break;

            case "sub":
                // Order matters: D holds y and M holds x, so x - y is M-D, not D-M.
                binaryOp("M=M-D");
                break;

            case "and":
                binaryOp("M=D&M");
                break;

            case "or":
                binaryOp("M=D|M");
                break;

            case "neg":
                unaryOp("M=-M");
                break;

            case "not":
                unaryOp("M=!M");
                break;

            case "eq":
                comparisonOp("JEQ");
                break;

            case "gt":
                comparisonOp("JGT");
                break;

            case "lt":
                comparisonOp("JLT");
                break;

            default:
                throw new IllegalArgumentException("Unknown arithmetic command: " + command);
        }
    }

    /**
     * Shared skeleton for add / sub / and / or.
     *
     * After this runs, the two operands have been replaced by one result and SP
     * has decreased by one.
     *
     *      @SP
     *      AM=M-1      SP--, and point A at the new top (that is y's slot)
     *      D=M         D = y
     *      A=A-1       step down one slot: A now points at x
     *      <operation> combine D (y) with M (x), storing into x's slot
     *
     * Note that SP already points just past x's slot, so x's slot IS the new top
     * of the stack. No further SP adjustment is needed.
     */
    private void binaryOp(String operation) throws IOException {
        write("@SP");
        write("AM=M-1");      // SP--, A = SP  (address of y)
        write("D=M");         // D = y
        write("A=A-1");       // A = address of x
        write(operation);     // x = x <op> y
    }

    /**
     * Shared skeleton for neg / not.
     *
     * The stack size is unchanged, so SP is left alone and we simply reach into
     * the top slot at RAM[SP-1] and rewrite it.
     */
    private void unaryOp(String operation) throws IOException {
        write("@SP");
        write("A=M-1");       // A = SP-1, the top of the stack. SP itself untouched.
        write(operation);
    }

    /**
     * Shared skeleton for eq / gt / lt.
     *
     * The trick here is "assume true, then correct if wrong", which avoids needing
     * two branches:
     *
     *      compute D = x - y
     *      write -1 (true) into x's slot speculatively
     *      if the jump condition holds, skip ahead and keep it
     *      otherwise fall through and overwrite the slot with 0 (false)
     *
     * The VM spec defines true as -1 rather than 1 precisely because -1 is
     * 1111111111111111, which makes the bitwise and/or/not commands behave
     * correctly on boolean values too.
     *
     * @param jumpCondition JEQ for eq, JGT for gt, JLT for lt — applied to x - y.
     */
    private void comparisonOp(String jumpCondition) throws IOException {
        String trueLabel = "COMP_TRUE_" + labelCounter;
        labelCounter++;

        write("@SP");
        write("AM=M-1");             // SP--, A points at y
        write("D=M");                // D = y
        write("A=A-1");              // A points at x
        write("D=M-D");              // D = x - y
        write("M=-1");               // speculatively store true in x's slot
        write("@" + trueLabel);
        write("D;" + jumpCondition); // condition holds? jump, leaving true in place
        write("@SP");
        write("A=M-1");              // otherwise re-address the top slot ...
        write("M=0");                // ... and replace true with false
        write("(" + trueLabel + ")");
    }

    // ------------------------------------------------------------------
    // push / pop
    // ------------------------------------------------------------------

    /**
     * Emits the implementation of a push or pop command.
     *
     * @param type    C_PUSH or C_POP
     * @param segment the VM segment name
     * @param index   the offset within that segment
     */
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
     * push <segment> <index> — load a value into D, then push D.
     *
     * Every case below differs only in HOW the value reaches D. Once it is there,
     * pushD() does the identical work of storing it and bumping SP.
     */
    private void writePush(String segment, int index) throws IOException {

        if (segment.equals("constant")) {
            // The value is the index itself — no memory is read at all.
            // D=A (not D=M) because we want the number, not RAM[number].
            write("@" + index);
            write("D=A");

        } else if (POINTER_SEGMENTS.containsKey(segment)) {
            // local / argument / this / that: RAM[ RAM[base] + index ]
            write("@" + index);
            write("D=A");                                 // D = index
            write("@" + POINTER_SEGMENTS.get(segment));
            write("A=M+D");                               // A = base + index
            write("D=M");                                 // D = the value stored there

        } else if (segment.equals("temp")) {
            // temp is a fixed block at RAM[5..12] — the address is known at
            // translation time, so no runtime arithmetic is needed.
            checkRange(index, TEMP_SIZE, "temp");
            write("@" + (TEMP_BASE + index));
            write("D=M");

        } else if (segment.equals("pointer")) {
            // pointer 0 and 1 ARE the THIS and THAT base registers themselves.
            write("@" + pointerSymbol(index));
            write("D=M");

        } else if (segment.equals("static")) {
            // A named symbol; the Hack assembler assigns it a RAM address.
            write("@" + currentFileName + "." + index);
            write("D=M");

        } else {
            throw new IllegalArgumentException("Unknown segment: " + segment);
        }

        pushD();
    }

    /**
     * pop <segment> <index> — remove the top value and store it somewhere.
     *
     * The pointer-based segments need care. We must compute the destination
     * address BEFORE popping, because computing it needs the D register and
     * popping also needs D. The address is therefore parked in a scratch
     * register (R13) while the pop happens.
     *
     * For the fixed and static segments the address is a compile-time constant,
     * so no scratch register is involved.
     */
    private void writePop(String segment, int index) throws IOException {

        if (POINTER_SEGMENTS.containsKey(segment)) {
            // Step 1: compute base + index and save it in R13.
            write("@" + index);
            write("D=A");                                 // D = index
            write("@" + POINTER_SEGMENTS.get(segment));
            write("D=M+D");                               // D = base + index
            write("@" + SCRATCH);
            write("M=D");                                 // R13 = destination address

            // Step 2: pop the value into D.
            popToD();

            // Step 3: store D at the address parked in R13.
            write("@" + SCRATCH);
            write("A=M");                                 // A = destination address
            write("M=D");                                 // RAM[dest] = popped value

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
            // "pop constant i" is meaningless: constant is not a real memory
            // segment, so there is nowhere to store the value.
            throw new IllegalArgumentException("Cannot pop to the constant segment");

        } else {
            throw new IllegalArgumentException("Unknown segment: " + segment);
        }
    }

    // ------------------------------------------------------------------
    // Reusable stack primitives
    // ------------------------------------------------------------------

    /**
     * Pushes whatever is currently in D onto the stack.
     *
     *      @SP
     *      A=M     point at the free slot
     *      M=D     write the value there
     *      @SP
     *      M=M+1   the free slot is now one higher
     */
    private void pushD() throws IOException {
        write("@SP");
        write("A=M");
        write("M=D");
        write("@SP");
        write("M=M+1");
    }

    /**
     * Pops the top of the stack into D.
     *
     *      @SP
     *      AM=M-1  SP--, and simultaneously point A at that slot
     *      D=M     read the value
     *
     * AM=M-1 is doing two jobs in one instruction: it stores the decremented
     * value back into SP *and* leaves it in A. That is the dest field "AM" from
     * the Hack spec — two destination bits set at once.
     */
    private void popToD() throws IOException {
        write("@SP");
        write("AM=M-1");
        write("D=M");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** pointer 0 -> THIS, pointer 1 -> THAT. Anything else is an error. */
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
     * Appends an infinite loop and closes the file.
     *
     * Hack programs cannot "end" — when the last instruction completes, the PC
     * would keep incrementing into whatever garbage follows. Parking the program
     * in a tight self-jump keeps it safely halted, exactly as the Project 4
     * programs did.
     */
    @Override
    public void close() throws IOException {
        write("// ----- end of program: halt -----");
        write("(END_OF_PROGRAM)");
        write("@END_OF_PROGRAM");
        write("0;JMP");
        out.close();
    }
}
