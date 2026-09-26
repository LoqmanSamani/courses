import java.util.HashMap;
import java.util.Map;

/**
 * Code — pure translation tables. Text mnemonic in, binary string out.
 *
 * This class has no state and no knowledge of files, passes or symbols. It only
 * answers three questions:
 *
 *      "What bits encode the comp field 'D+M'?"  -> comp("D+M")
 *      "What bits encode the dest field 'AM'?"   -> dest("AM")
 *      "What bits encode the jump field 'JGT'?"  -> jump("JGT")
 *
 * Every table below comes straight from the Hack machine-language specification.
 *
 * A C-instruction is assembled as:
 *
 *      1 1 1   a c1 c2 c3 c4 c5 c6   d1 d2 d3   j1 j2 j3
 *      \___/   \________comp_______/  \_dest__/  \_jump_/
 *      opcode        (7 bits)          (3 bits)  (3 bits)
 *
 * which is 3 + 7 + 3 + 3 = 16 bits.
 */
public final class Code {

    /** Utility class — never instantiated. */
    private Code() { }

    /**
     * comp field -> 7 bits: the 'a' bit followed by c1..c6.
     *
     * The 'a' bit selects which value the ALU sees as its second operand:
     *      a = 0  ->  the A register
     *      a = 1  ->  M, i.e. RAM[A]
     *
     * Notice that every A-form entry has an identical M-form entry with the same
     * six c-bits and only the leading 'a' bit flipped. That is exactly the
     * Mux16 you wired into the CPU in Project 5: one bit doubles the table.
     *
     * The six c-bits are, in order, the zx nx zy ny f no control inputs of the
     * ALU you built in Project 2. They are copied into the instruction verbatim.
     */
    private static final Map<String, String> COMP = new HashMap<>();
    static {
        //                     a c1c2c3c4c5c6
        COMP.put("0",         "0101010");
        COMP.put("1",         "0111111");
        COMP.put("-1",        "0111010");
        COMP.put("D",         "0001100");
        COMP.put("A",         "0110000");
        COMP.put("!D",        "0001101");
        COMP.put("!A",        "0110001");
        COMP.put("-D",        "0001111");
        COMP.put("-A",        "0110011");
        COMP.put("D+1",       "0011111");
        COMP.put("A+1",       "0110111");
        COMP.put("D-1",       "0001110");
        COMP.put("A-1",       "0110010");
        COMP.put("D+A",       "0000010");
        COMP.put("D-A",       "0010011");
        COMP.put("A-D",       "0000111");
        COMP.put("D&A",       "0000000");
        COMP.put("D|A",       "0010101");

        // The a=1 half: same computations, but on M instead of A.
        COMP.put("M",         "1110000");
        COMP.put("!M",        "1110001");
        COMP.put("-M",        "1110011");
        COMP.put("M+1",       "1110111");
        COMP.put("M-1",       "1110010");
        COMP.put("D+M",       "1000010");
        COMP.put("D-M",       "1010011");
        COMP.put("M-D",       "1000111");
        COMP.put("D&M",       "1000000");
        COMP.put("D|M",       "1010101");

        // Commutative forms that some .asm files use. The Hack spec lists only
        // the left-hand spellings above, but accepting these costs nothing and
        // makes the assembler more forgiving.
        COMP.put("A+D",       "0000010");
        COMP.put("M+D",       "1000010");
        COMP.put("A&D",       "0000000");
        COMP.put("M&D",       "1000000");
        COMP.put("A|D",       "0010101");
        COMP.put("M|D",       "1010101");
    }

    /**
     * jump field -> 3 bits j1 j2 j3.
     *
     * Each bit is an independent condition, tested against the ALU's zr and ng
     * status outputs by the jump logic inside the CPU:
     *
     *      j1 (bit 2) -> jump if out <  0
     *      j2 (bit 1) -> jump if out == 0
     *      j3 (bit 0) -> jump if out >  0
     *
     * The named mnemonics are just the eight combinations of those three bits.
     * JGE is "equal OR greater", JMP is "all three", null is "none".
     */
    private static final Map<String, String> JUMP = new HashMap<>();
    static {
        JUMP.put("",    "000");   // no jump
        JUMP.put("JGT", "001");   //          out > 0
        JUMP.put("JEQ", "010");   //          out = 0
        JUMP.put("JGE", "011");   // out = 0 or out > 0
        JUMP.put("JLT", "100");   //          out < 0
        JUMP.put("JNE", "101");   // out < 0 or out > 0
        JUMP.put("JLE", "110");   // out < 0 or out = 0
        JUMP.put("JMP", "111");   // always
    }

    /**
     * Translates a comp mnemonic into its 7 bits.
     * @throws IllegalArgumentException when the mnemonic is not a legal computation.
     */
    public static String comp(String mnemonic) {
        String bits = COMP.get(mnemonic);
        if (bits == null) {
            throw new IllegalArgumentException("Unknown comp field: '" + mnemonic + "'");
        }
        return bits;
    }

    /**
     * Translates a dest mnemonic into its 3 bits d1 d2 d3.
     *
     * Unlike comp and jump, dest is NOT looked up in a table. Each of the three
     * bits simply asks "is this register mentioned?":
     *
     *      d1 (bit 2) -> store the result in A
     *      d2 (bit 1) -> store the result in D
     *      d3 (bit 0) -> store the result in M, i.e. RAM[A]
     *
     * Testing for the characters directly means "AM", "MA", "AMD", "ADM" and
     * every other ordering all work, with no table entries needed. Those three
     * bits become the load signals for the A register, the D register and the
     * RAM inside the CPU.
     */
    public static String dest(String mnemonic) {
        char a = mnemonic.indexOf('A') >= 0 ? '1' : '0';
        char d = mnemonic.indexOf('D') >= 0 ? '1' : '0';
        char m = mnemonic.indexOf('M') >= 0 ? '1' : '0';
        return "" + a + d + m;
    }

    /**
     * Translates a jump mnemonic into its 3 bits.
     * @throws IllegalArgumentException when the mnemonic is not a legal jump.
     */
    public static String jump(String mnemonic) {
        String bits = JUMP.get(mnemonic);
        if (bits == null) {
            throw new IllegalArgumentException("Unknown jump field: '" + mnemonic + "'");
        }
        return bits;
    }
}
