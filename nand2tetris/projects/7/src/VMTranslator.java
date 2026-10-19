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
 *      2. For each input file, create a Parser.
 *      3. Walk the commands, asking Parser what each one is and handing the
 *         pieces to CodeWriter.
 *      4. Close the output.
 *
 * Usage:
 *      java VMTranslator Prog.vm        ->  writes Prog.asm beside it
 *      java VMTranslator DirName        ->  writes DirName/DirName.asm,
 *                                           combining every .vm file inside
 *
 * Project 7 only ever uses the single-file form. Directory input is supported
 * because Project 8 requires it, and handling it here costs only a few lines.
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
     */
    private static void translate(Path input) throws IOException {

        if (!Files.exists(input)) {
            throw new IOException("no such file or directory: " + input);
        }

        List<Path> vmFiles;
        Path outputFile;

        if (Files.isDirectory(input)) {
            // Directory form: translate every .vm file inside into one .asm,
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

        // try-with-resources: CodeWriter implements AutoCloseable, so the closing
        // halt loop is appended and the file flushed even if an error is thrown.
        try (CodeWriter codeWriter = new CodeWriter(outputFile)) {

            int totalCommands = 0;
            for (Path vmFile : vmFiles) {
                totalCommands += translateOneFile(vmFile, codeWriter);
            }

            System.out.printf("%s -> %s  (%d VM command%s from %d file%s)%n",
                    input.getFileName(),
                    outputFile.getFileName(),
                    totalCommands, totalCommands == 1 ? "" : "s",
                    vmFiles.size(), vmFiles.size() == 1 ? "" : "s");
        }
    }

    /**
     * Translates a single .vm file, appending to the shared CodeWriter.
     *
     * This is the main loop of the whole program: ask the Parser what the current
     * command is, then call the matching CodeWriter method.
     *
     * @return how many commands were translated.
     */
    private static int translateOneFile(Path vmFile, CodeWriter codeWriter) throws IOException {

        Parser parser = new Parser(vmFile);

        // Strip the ".vm" to get the bare name, which CodeWriter needs in order to
        // build unique static-variable symbols such as "StaticTest.3".
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

                default:
                    // label / goto / if-goto / function / call / return.
                    // The Parser recognises these, but program flow and function
                    // calling are Project 8's subject, so there is nothing to
                    // emit for them yet.
                    throw new IllegalArgumentException(
                            "command not supported in Project 7: '"
                                    + parser.currentCommand() + "'");
            }
        }

        return count;
    }

    /**
     * Every .vm file directly inside a directory, sorted by name so that the
     * output is deterministic from run to run.
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

    /** "StaticTest.vm" -> "StaticTest" */
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
