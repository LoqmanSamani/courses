import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * VMTranslator — the entry point. Drives the whole translation process.
 *
 * It owns no translation logic of its own. Its job is purely to coordinate:
 *
 *      1. Work out which .vm files to read and what .asm file to write.
 *      2. Construct a CodeWriter, and emit bootstrap code if appropriate.
 *      3. For each input file, construct a Parser and march through it,
 *         handing each parsed command to the CodeWriter.
 *      4. Close the output.
 *
 * Usage:
 *      java VMTranslator Prog.vm        ->  writes Prog.asm beside it
 *      java VMTranslator DirName        ->  writes DirName/DirName.asm,
 *                                           combining every .vm file inside
 */
public class VMTranslator {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Usage: java VMTranslator <file.vm | directory>");
            System.exit(1);
        }

        Path input = Paths.get(args[0]);

        try {
            translate(input);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            // Thrown by Parser or CodeWriter when the source is not valid VM code.
            System.err.println("Translation error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Decides the input file list and the output path, then runs the translation.
     *
     * THE BOOTSTRAP RULE
     * ------------------
     * Bootstrap code (SP=256; call Sys.init) is emitted only for DIRECTORY
     * input, i.e. for a complete program.
     *
     * Single .vm files are translated without it, because the supplied test
     * scripts for those (BasicLoop, FibonacciSeries, SimpleFunction) set SP,
     * LCL and ARG themselves and simulate a call frame. A bootstrap would
     * overwrite that carefully arranged state and the tests would fail.
     */
    private static void translate(Path input) throws IOException {

        if (!Files.exists(input)) {
            throw new IOException("no such file or directory: " + input);
        }

        List<Path> vmFiles;
        Path outputFile;
        boolean isDirectory = Files.isDirectory(input);

        if (isDirectory) {
            // Directory form: every .vm file inside becomes one .asm,
            // named after the directory itself.
            vmFiles = listVmFiles(input);
            if (vmFiles.isEmpty()) {
                throw new IOException("no .vm files found in " + input);
            }
            String dirName = input.toAbsolutePath().getFileName().toString();
            outputFile = input.resolve(dirName + ".asm");

        } else {
            // Single-file form: Prog.vm -> Prog.asm in the same directory.
            if (!input.getFileName().toString().endsWith(".vm")) {
                throw new IllegalArgumentException("input file must have a .vm extension");
            }
            vmFiles = List.of(input);
            outputFile = replaceExtension(input, ".asm");
        }

        // try-with-resources: CodeWriter is AutoCloseable, so the halt loop is
        // appended and the file flushed even if a translation error is thrown.
        try (CodeWriter codeWriter = new CodeWriter(outputFile)) {

            // The bootstrap must be the FIRST code in the file, so that it is
            // the first thing the CPU executes at ROM address 0.
            if (isDirectory) {
                codeWriter.writeInit();
            }

            int totalCommands = 0;
            for (Path vmFile : vmFiles) {
                totalCommands += translateOneFile(vmFile, codeWriter);
            }

            System.out.printf("%-22s -> %-22s %4d commands, %d file%s%s%n",
                    input.getFileName(),
                    outputFile.getFileName(),
                    totalCommands,
                    vmFiles.size(), vmFiles.size() == 1 ? "" : "s",
                    isDirectory ? ", bootstrap" : "");
        }
    }

    /**
     * Translates a single .vm file, appending to the shared CodeWriter.
     *
     * This is the main loop: ask the Parser what the current command is, then
     * call the matching CodeWriter method. Every VM command in the language is
     * handled here.
     *
     * @return how many commands were translated.
     */
    private static int translateOneFile(Path vmFile, CodeWriter codeWriter) throws IOException {

        Parser parser = new Parser(vmFile);

        // Strip ".vm" to get the bare name. CodeWriter needs it to build unique
        // static-variable symbols such as "Class1.0", and as a default label scope.
        String baseName = stripExtension(vmFile.getFileName().toString());
        codeWriter.setFileName(baseName);

        int count = 0;

        while (parser.hasMoreCommands()) {
            parser.advance();
            count++;

            switch (parser.commandType()) {

                case C_ARITHMETIC:
                    // arg1() returns the command itself ("add", "eq", ...).
                    codeWriter.writeArithmetic(parser.arg1());
                    break;

                case C_PUSH:
                case C_POP:
                    // Both share one CodeWriter method; the type tells it which.
                    codeWriter.writePushPop(
                            parser.commandType(),
                            parser.arg1(),      // segment name
                            parser.arg2());     // index within the segment
                    break;

                case C_LABEL:
                    codeWriter.writeLabel(parser.arg1());
                    break;

                case C_GOTO:
                    codeWriter.writeGoto(parser.arg1());
                    break;

                case C_IF:
                    codeWriter.writeIf(parser.arg1());
                    break;

                case C_FUNCTION:
                    // arg1 = function name, arg2 = number of local variables
                    codeWriter.writeFunction(parser.arg1(), parser.arg2());
                    break;

                case C_CALL:
                    // arg1 = function name, arg2 = number of arguments
                    codeWriter.writeCall(parser.arg1(), parser.arg2());
                    break;

                case C_RETURN:
                    // The only command with no arguments at all.
                    codeWriter.writeReturn();
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Unhandled command: '" + parser.currentCommand() + "'");
            }
        }

        return count;
    }

    /**
     * Every .vm file directly inside a directory, sorted by name so the output
     * is deterministic from run to run.
     *
     * Order does not affect correctness — function names are global labels and
     * the bootstrap jumps straight to Sys.init wherever it ends up — but a
     * stable order makes the generated .asm diffable between runs.
     */
    private static List<Path> listVmFiles(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> result = new ArrayList<>();
            entries.filter(Files::isRegularFile)
                   .filter(p -> p.getFileName().toString().endsWith(".vm"))
                   .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                   .forEach(result::add);
            return result;
        }
    }

    /** "StaticsTest.vm" -> "StaticsTest" */
    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(0, dot) : fileName;
    }

    /** /path/to/Prog.vm -> /path/to/Prog.asm */
    private static Path replaceExtension(Path path, String newExtension) {
        String newName = stripExtension(path.getFileName().toString()) + newExtension;
        Path parent = path.getParent();
        return (parent == null) ? Paths.get(newName) : parent.resolve(newName);
    }
}
