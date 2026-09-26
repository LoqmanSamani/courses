import java.util.ArrayList;
import java.util.List;

/**
 * SecondPass — walks the program again and emits one 16-bit binary string per
 * real instruction.
 *
 * By the time this runs, FirstPass has already put every label into the symbol
 * table, so a forward reference like @END can be resolved immediately.
 *
 * This pass handles the three cases from the course slide's "Main Loop":
 *
 *      label            -> skip it, it produces no output
 *      A-instruction    -> resolve the symbol to a number, emit 0 + 15-bit value
 *      C-instruction    -> look up each of the three fields, glue them together
 */
public class SecondPass {

    /**
     * Where the first variable goes. RAM[0]..RAM[15] are reserved for R0..R15,
     * so user variables start right after, at address 16, and climb from there.
     */
    private static final int FIRST_VARIABLE_ADDRESS = 16;

    /** Highest value an A-instruction can hold: 15 bits, all ones. */
    private static final int MAX_A_VALUE = 32767;

    private final Parser parser;
    private final SymbolTable symbolTable;

    /** The RAM address the NEXT newly-discovered variable will be given. */
    private int nextVariableAddress = FIRST_VARIABLE_ADDRESS;

    public SecondPass(Parser parser, SymbolTable symbolTable) {
        this.parser = parser;
        this.symbolTable = symbolTable;
    }

    /**
     * Translates the whole program.
     *
     * @return one string of sixteen '0'/'1' characters per machine instruction.
     */
    public List<String> run() {
        List<String> machineCode = new ArrayList<>();

        // "Restart reading and translating commands" — rewind after the first pass.
        parser.reset();

        while (parser.hasMoreLines()) {
            parser.advance();

            switch (parser.instructionType()) {

                case L_INSTRUCTION:
                    // A label such as (LOOP). It was already recorded by FirstPass
                    // and it is not a machine instruction, so emit nothing at all.
                    break;

                case A_INSTRUCTION:
                    machineCode.add(translateA(parser.symbol()));
                    break;

                case C_INSTRUCTION:
                    machineCode.add(translateC(
                            parser.dest(),
                            parser.comp(),
                            parser.jump()));
                    break;
            }
        }

        return machineCode;
    }

    /**
     * Translates the operand of an A-instruction (@xxx) into 16 bits.
     *
     * The operand is either a literal number or a symbol:
     *
     *      @21       -> the number is right there
     *      @SCREEN   -> a predefined symbol, already in the table
     *      @LOOP     -> a label, put in the table by FirstPass
     *      @counter  -> a variable; if we have never seen this name before,
     *                   ALLOCATE the next free RAM slot for it right now
     *
     * That last case is the only place in the assembler where new RAM addresses
     * are handed out. A name reaching this point that is not in the table cannot
     * be a label (FirstPass found them all), so by elimination it is a variable.
     *
     * The result is '0' (the A-instruction opcode) followed by the value in 15 bits.
     */
    private String translateA(String symbol) {
        int value;

        if (isNumeric(symbol)) {
            // A literal constant, e.g. @21.
            value = Integer.parseInt(symbol);

        } else {
            // A name. Allocate it a fresh RAM slot if this is its first appearance.
            if (!symbolTable.contains(symbol)) {
                symbolTable.addEntry(symbol, nextVariableAddress);
                nextVariableAddress++;
            }
            value = symbolTable.getAddress(symbol);
        }

        if (value < 0 || value > MAX_A_VALUE) {
            throw new IllegalArgumentException(
                    "A-instruction value out of range (0.." + MAX_A_VALUE + "): " + value);
        }

        return "0" + toBinary15(value);
    }

    /**
     * Assembles a C-instruction from its three text fields.
     *
     * Layout:   1 1 1   a c1..c6   d1 d2 d3   j1 j2 j3
     *
     * The leading "111" is the C-instruction opcode. Bit 15 being 1 is what tells
     * the CPU this is a computation and not a constant; bits 14 and 13 are unused
     * by the hardware but are conventionally written as 1s.
     */
    private String translateC(String dest, String comp, String jump) {
        return "111" + Code.comp(comp) + Code.dest(dest) + Code.jump(jump);
    }

    /**
     * True when the whole string is digits, i.e. the operand is a literal number
     * rather than a symbol. Hack symbols may never begin with a digit, so
     * checking the characters is enough.
     */
    private boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Formats a non-negative int as exactly 15 binary digits, left-padded with '0'.
     *
     * Integer.toBinaryString(5) gives "101", but the machine needs "000000000000101",
     * so the missing leading zeros are added by hand.
     */
    private String toBinary15(int value) {
        String bits = Integer.toBinaryString(value);
        StringBuilder padded = new StringBuilder();
        for (int i = bits.length(); i < 15; i++) {
            padded.append('0');
        }
        padded.append(bits);
        return padded.toString();
    }
}
