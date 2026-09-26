import java.util.HashMap;
import java.util.Map;

/**
 * SymbolTable — maps every symbolic name in a program to a number.
 *
 * Hack assembly lets you write names instead of raw numbers:
 *
 *      @SCREEN     a predefined constant   -> 16384
 *      @LOOP       a label you declared    -> the ROM line number of (LOOP)
 *      @counter    a variable you invented -> a free RAM slot, starting at 16
 *
 * All three kinds live in this one table. They are only distinguished by WHEN
 * they get added:
 *
 *      predefined -> added here, in the constructor, before anything is read
 *      labels     -> added by FirstPass, which knows each label's ROM address
 *      variables  -> added by SecondPass, the first time an unknown name appears
 *
 * By the time a name is looked up during code generation, it is guaranteed to be
 * in the table — that guarantee is the entire reason the assembler needs two passes.
 */
public class SymbolTable {

    /** name -> numeric value (a ROM line number, or a RAM address). */
    private final Map<String, Integer> table = new HashMap<>();

    /**
     * Creates the table pre-loaded with the symbols the Hack platform defines
     * for every program. A source file may use these without declaring anything.
     */
    public SymbolTable() {

        // R0..R15 are friendly names for RAM addresses 0..15. They are the
        // general-purpose "registers" your Project 4 programs used (@R0, @R2).
        for (int i = 0; i <= 15; i++) {
            table.put("R" + i, i);
        }

        // Aliases for four of those same low addresses. They are not used in
        // Project 6, but the VM translator in Part II relies on them heavily:
        // they are the stack pointer and the segment base pointers.
        table.put("SP",   0);
        table.put("LCL",  1);
        table.put("ARG",  2);
        table.put("THIS", 3);
        table.put("THAT", 4);

        // The two memory-mapped I/O devices, exactly as wired in your Memory chip.
        table.put("SCREEN", 16384);   // 0x4000 — first word of the screen map
        table.put("KBD",    24576);   // 0x6000 — the keyboard word
    }

    /** Records a new name -> value binding. */
    public void addEntry(String symbol, int address) {
        table.put(symbol, address);
    }

    /** True if this name already has a value. */
    public boolean contains(String symbol) {
        return table.containsKey(symbol);
    }

    /** The value bound to this name. Call contains() first. */
    public int getAddress(String symbol) {
        return table.get(symbol);
    }

    /** How many symbols are currently known — handy for reporting. */
    public int size() {
        return table.size();
    }
}
