import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Assembler — the entry point. Wires the other five classes together and writes
 * the .hack output file.
 *
 * This mirrors the "Overall logic" of the course slide exactly:
 *
 *      1. Initialization        -> build the Parser and the SymbolTable
 *      2. First Pass            -> read all commands, record label addresses
 *      3. Restart reading       -> parser.reset(), done inside SecondPass
 *      4. Main Loop             -> translate each command, emit machine code
 *
 * Usage:
 *      java Assembler Prog.asm [More.asm ...]
 *
 * For each input Xxx.asm it writes Xxx.hack next to it: one line of sixteen
 * '0'/'1' characters per machine instruction.
 */
public class Assembler {

    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("Usage: java Assembler <file.asm> [<file2.asm> ...]");
            System.exit(1);
        }

        // Translate each file given on the command line, independently.
        for (String arg : args) {
            try {
                assembleFile(Paths.get(arg));
            } catch (IOException e) {
                System.err.println("I/O error while processing " + arg + ": " + e.getMessage());
                System.exit(1);
            } catch (IllegalArgumentException e) {
                // Thrown by Code or SecondPass when the source contains something
                // that is not legal Hack assembly.
                System.err.println("Assembly error in " + arg + ": " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /**
     * Runs the full two-pass pipeline on one .asm file.
     */
    private static void assembleFile(Path asmPath) throws IOException {

        if (!Files.exists(asmPath)) {
            throw new IOException("file not found");
        }

        // ---- 1. Initialization -------------------------------------------------
        // The parser cleans the file (comments and whitespace removed) and gives
        // us a rewindable stream of instructions.
        Parser parser = new Parser(asmPath);

        // The symbol table starts out holding only the predefined symbols
        // (R0..R15, SP, LCL, ARG, THIS, THAT, SCREEN, KBD).
        SymbolTable symbolTable = new SymbolTable();

        // ---- 2. First Pass -----------------------------------------------------
        // Find every (LABEL) and bind it to the ROM address of the instruction
        // that follows it. Nothing is translated yet.
        int labels = new FirstPass(parser, symbolTable).run();

        // ---- 3 & 4. Restart reading, then the Main Loop ------------------------
        // SecondPass rewinds the parser itself, then walks the program again,
        // this time producing one 16-bit word per real instruction. Variables
        // are discovered and allocated RAM addresses as they are encountered.
        List<String> machineCode = new SecondPass(parser, symbolTable).run();

        // ---- Write the output --------------------------------------------------
        Path hackPath = replaceExtension(asmPath, ".hack");
        Files.write(hackPath, machineCode, StandardCharsets.UTF_8);

        System.out.printf("%-12s -> %-12s  %4d instructions, %d labels, %d symbols%n",
                asmPath.getFileName(),
                hackPath.getFileName(),
                machineCode.size(),
                labels,
                symbolTable.size());
    }

    /**
     * Builds the sibling output path: /path/to/Prog.asm -> /path/to/Prog.hack
     *
     * If the input somehow has no dot in its name, the new extension is simply
     * appended.
     */
    private static Path replaceExtension(Path path, String newExtension) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name;
        Path parent = path.getParent();
        String newName = base + newExtension;
        return (parent == null) ? Paths.get(newName) : parent.resolve(newName);
    }
}
