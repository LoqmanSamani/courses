import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * JackCompiler — the entry point. Drives the compilation.
 *
 * It owns no compilation of its own. Its job is purely to coordinate:
 *
 *      1. Work out which .jack files to compile.
 *      2. For each one, build a JackTokenizer and a VMWriter.
 *      3. Hand both to a CompilationEngine, which does the work.
 *
 * Usage:
 *      java JackCompiler Prog.jack      ->  writes Prog.vm beside it
 *      java JackCompiler DirName        ->  compiles every .jack inside,
 *                                           each to its own .vm
 *
 * Note what does NOT happen here: the files are not linked, and nothing is
 * combined. Each class compiles to its own .vm file independently, and the
 * names are tied together only at run time, by the VM translator resolving
 * `call Main.main` against whatever file happens to define it. That is why a
 * class can call a subroutine of a class this compiler has never seen — the OS
 * classes, for instance.
 */
public class JackCompiler {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java JackCompiler <file.jack | directory>");
            System.exit(1);
        }

        try {
            compile(Paths.get(args[0]));
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            // Thrown by the tokenizer or the compilation engine when the source
            // is not valid Jack. The message already carries the file and line.
            System.err.println("Compilation error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println("Compilation error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Decides the input file list, then compiles each file in turn. */
    private static void compile(Path input) throws IOException {
        if (!Files.exists(input)) {
            throw new IOException("no such file or directory: " + input);
        }

        List<Path> jackFiles;
        if (Files.isDirectory(input)) {
            jackFiles = listJackFiles(input);
            if (jackFiles.isEmpty()) {
                throw new IOException("no .jack files found in " + input);
            }
        } else {
            if (!input.getFileName().toString().endsWith(".jack")) {
                throw new IllegalArgumentException("input file must have a .jack extension");
            }
            jackFiles = Collections.singletonList(input);
        }

        for (Path jackFile : jackFiles) {
            compileOneFile(jackFile);
        }
    }

    /** Compiles one .jack file to the .vm file beside it. */
    private static void compileOneFile(Path jackFile) throws IOException {
        String baseName = stripExtension(jackFile.getFileName().toString());
        Path parent = jackFile.toAbsolutePath().getParent();
        Path vmFile = parent.resolve(baseName + ".vm");

        JackTokenizer tokenizer = new JackTokenizer(jackFile);
        VMWriter writer = new VMWriter(vmFile);
        try {
            new CompilationEngine(tokenizer, writer).compileClass();
        } catch (IllegalArgumentException e) {
            // Prefix the file name, so an error inside a directory of classes
            // says which class it came from.
            throw new IllegalArgumentException(
                    jackFile.getFileName() + ": " + e.getMessage(), e);
        } finally {
            writer.close();
        }

        System.out.printf("%-20s -> %-20s %4d tokens%n",
                jackFile.getFileName(), vmFile.getFileName(),
                Integer.valueOf(tokenizer.allTokens().size()));
    }

    /**
     * Every .jack file directly inside a directory, sorted by name so that the
     * run is deterministic and its output is diffable between runs.
     */
    private static List<Path> listJackFiles(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> result = new ArrayList<Path>();
            entries.filter(Files::isRegularFile)
                   .filter(p -> p.getFileName().toString().endsWith(".jack"))
                   .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                   .forEach(result::add);
            return result;
        }
    }

    /** "Main.jack" -> "Main" */
    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(0, dot) : fileName;
    }
}
