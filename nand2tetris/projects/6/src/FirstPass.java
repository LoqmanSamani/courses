/**
 * FirstPass — walks the whole program once and records where every label points.
 *
 * WHY THIS PASS EXISTS
 * --------------------
 * Consider this fragment:
 *
 *      @END        <- "jump to END"  ... but where is END?
 *      D;JGE
 *      ...
 *      (END)       <- only now do we find out
 *
 * The jump refers to a label that is declared LATER in the file. A single-pass
 * assembler reading top to bottom could not translate @END, because it has not
 * seen (END) yet. This is called a forward reference, and it is the reason the
 * assembler reads the file twice.
 *
 * The first pass does nothing except find every (LABEL) and write down the ROM
 * address it corresponds to. No binary code is produced here.
 *
 * WHAT ADDRESS DOES A LABEL GET?
 * ------------------------------
 * The address of the next REAL instruction — because a label is not an
 * instruction. It occupies no memory and produces no output. It is a marker.
 *
 *      line in file        romAddress      emits code?
 *      ------------------------------------------------
 *      @R0                 0               yes
 *      D=M                 1               yes
 *      (LOOP)              -               no  <- (LOOP) = 2
 *      @R1                 2               yes
 *      D=D-M               3               yes
 *
 * So the counter is incremented for A- and C-instructions and left untouched
 * for labels. That is the whole algorithm.
 */
public class FirstPass {

    private final Parser parser;
    private final SymbolTable symbolTable;

    public FirstPass(Parser parser, SymbolTable symbolTable) {
        this.parser = parser;
        this.symbolTable = symbolTable;
    }

    /**
     * Scans every line and adds one entry to the symbol table per label found.
     *
     * @return how many labels were recorded (useful for a progress report).
     */
    public int run() {

        // The ROM address the NEXT real instruction will occupy.
        // Hack programs always start at ROM address 0.
        int romAddress = 0;

        int labelsFound = 0;

        // Make sure we start from the top, even if this parser was used before.
        parser.reset();

        while (parser.hasMoreLines()) {
            parser.advance();

            if (parser.instructionType() == Parser.InstructionType.L_INSTRUCTION) {

                // A label declaration such as (LOOP).
                // Bind the name to the address of the instruction that follows it,
                // which is the current value of the counter — NOT counter + 1,
                // precisely because the label itself takes up no space.
                symbolTable.addEntry(parser.symbol(), romAddress);
                labelsFound++;

                // Deliberately do NOT increment romAddress here.

            } else {
                // An A- or C-instruction. This one will become a real 16-bit word
                // in the output, so the next instruction sits one address higher.
                romAddress++;
            }
        }

        return labelsFound;
    }
}
