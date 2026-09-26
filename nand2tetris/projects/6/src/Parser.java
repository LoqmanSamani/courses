import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser — turns a .asm text file into a stream of clean, classified instructions.
 *
 * This class does NO translation. Its only job is to answer questions about the
 * instruction we are currently looking at:
 *
 *      "What kind of instruction is this?"        -> instructionType()
 *      "What symbol / number does it mention?"    -> symbol()
 *      "What are its dest / comp / jump parts?"   -> dest(), comp(), jump()
 *
 * Turning those text pieces into 1s and 0s is the Code class's job, and deciding
 * what a symbol's numeric value is belongs to SymbolTable. Keeping those three
 * responsibilities apart is the whole point of the design.
 *
 * The parser is REWINDABLE (see reset()). That matters because the assembler reads
 * the file twice: once to collect label addresses, once to generate code.
 */
public class Parser {

    /**
     * The three shapes a line of Hack assembly can have.
     *
     *   A_INSTRUCTION  ->  @xxx        e.g. @21, @sum, @SCREEN
     *   C_INSTRUCTION  ->  dest=comp;jump   e.g. D=M+1, D;JGT, M=0
     *   L_INSTRUCTION  ->  (Xxx)       a label. NOT a real instruction: it
     *                                  generates no binary output, it just marks
     *                                  a position in the program.
     */
    public enum InstructionType {
        A_INSTRUCTION,
        C_INSTRUCTION,
        L_INSTRUCTION
    }

    /** Every meaningful instruction of the file, already stripped of noise. */
    private final List<String> instructions = new ArrayList<>();

    /** Index into `instructions` of the instruction we are currently on. */
    private int cursor = -1;

    /**
     * Reads the file and pre-cleans it.
     *
     * Cleaning removes everything that carries no meaning for the machine:
     *   - whole-line comments and trailing comments  (// ...)
     *   - blank lines
     *   - ALL whitespace inside an instruction, so "D = D + M" and "D=D+M" are
     *     treated identically. This is safe in Hack assembly because the language
     *     has no string literals — no whitespace is ever significant.
     */
    public Parser(Path asmFile) throws IOException {
        for (String rawLine : Files.readAllLines(asmFile, StandardCharsets.UTF_8)) {

            // Drop a trailing comment: keep only the text before the first "//".
            int commentStart = rawLine.indexOf("//");
            String code = (commentStart >= 0) ? rawLine.substring(0, commentStart) : rawLine;

            // Remove every whitespace character (spaces, tabs, stray \r from
            // Windows line endings). \\s covers all of them.
            code = code.replaceAll("\\s", "");

            // Whatever survives is a real instruction. Empty lines disappear here.
            if (!code.isEmpty()) {
                instructions.add(code);
            }
        }
    }

    /** True while there is still an instruction after the current one. */
    public boolean hasMoreLines() {
        return cursor + 1 < instructions.size();
    }

    /**
     * Moves to the next instruction and makes it "current".
     * Must be called before any of the accessor methods below.
     */
    public void advance() {
        cursor++;
    }

    /**
     * Rewinds to the very beginning so the file can be walked a second time.
     * The assembler calls this between the first pass and the second pass.
     */
    public void reset() {
        cursor = -1;
    }

    /** The raw text of the current instruction, e.g. "@sum" or "D=M+1". */
    public String currentInstruction() {
        return instructions.get(cursor);
    }

    /** How many real instructions the file contains (labels included). */
    public int size() {
        return instructions.size();
    }

    /**
     * Classifies the current instruction by looking at its first character only.
     *
     *   '@'  -> A-instruction
     *   '('  -> label declaration
     *   else -> C-instruction
     */
    public InstructionType instructionType() {
        String line = currentInstruction();
        if (line.charAt(0) == '@') {
            return InstructionType.A_INSTRUCTION;
        } else if (line.charAt(0) == '(') {
            return InstructionType.L_INSTRUCTION;
        } else {
            return InstructionType.C_INSTRUCTION;
        }
    }

    /**
     * The symbol or number of the current instruction.
     * Only valid for A_INSTRUCTION and L_INSTRUCTION.
     *
     *   "@sum"   -> "sum"      (strip the leading @)
     *   "@21"    -> "21"       (still a String; the caller decides if it is a number)
     *   "(LOOP)" -> "LOOP"     (strip the surrounding parentheses)
     */
    public String symbol() {
        String line = currentInstruction();
        if (instructionType() == InstructionType.A_INSTRUCTION) {
            return line.substring(1);
        }
        // L_INSTRUCTION: drop the first char '(' and the last char ')'.
        return line.substring(1, line.length() - 1);
    }

    /**
     * The dest field of a C-instruction, or "" when there is none.
     *
     * A C-instruction looks like   dest=comp;jump   where dest and jump are
     * optional. The dest part, if present, is everything before the '='.
     *
     *   "D=M+1"  -> "D"
     *   "AM=M-1" -> "AM"
     *   "D;JGT"  -> ""    (no '=' means the result is not stored anywhere)
     */
    public String dest() {
        String line = currentInstruction();
        int eq = line.indexOf('=');
        return (eq >= 0) ? line.substring(0, eq) : "";
    }

    /**
     * The comp field of a C-instruction — the part the ALU actually computes.
     * This field is ALWAYS present; it is what sits between '=' and ';'.
     *
     *   "D=M+1"  -> "M+1"
     *   "D;JGT"  -> "D"
     *   "0;JMP"  -> "0"
     */
    public String comp() {
        String line = currentInstruction();

        // Start just after the '=' if there is one, otherwise at the beginning.
        int start = line.indexOf('=') + 1;      // indexOf returns -1 when absent,
                                                // so -1 + 1 = 0 — exactly what we want.

        // End just before the ';' if there is one, otherwise at the end of the line.
        int semi = line.indexOf(';');
        int end = (semi >= 0) ? semi : line.length();

        return line.substring(start, end);
    }

    /**
     * The jump field of a C-instruction, or "" when there is none.
     * It is everything after the ';'.
     *
     *   "D;JGT"  -> "JGT"
     *   "0;JMP"  -> "JMP"
     *   "D=M+1"  -> ""    (no ';' means this instruction never jumps)
     */
    public String jump() {
        String line = currentInstruction();
        int semi = line.indexOf(';');
        return (semi >= 0) ? line.substring(semi + 1) : "";
    }
}
