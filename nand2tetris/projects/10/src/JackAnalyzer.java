import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * JackAnalyzer — the entry point. Drives the syntax analysis.
 *
 * It owns no analysis of its own. Its job is purely to coordinate:
 *
 *      1. Work out which .jack files to read.
 *      2. For each one, build a JackTokenizer.
 *      3. Write the token stream to XxxT.xml.
 *      4. Hand the tokens to a CompilationEngine, which writes the parse tree
 *         to Xxx.xml.
 *
 * Usage:
 *      java JackAnalyzer Prog.jack      ->  writes Prog.xml and ProgT.xml
 *      java JackAnalyzer DirName        ->  does that for every .jack inside
 *
 * The output always lands beside the input, which is what the project's test
 * procedure expects: the supplied .xml files sit next to the .jack files they
 * describe, so the two can be compared with the TextComparer tool.
 *
 * A NOTE ON THE TOKEN FILE
 * XxxT.xml is not part of the final compiler — it is scaffolding. It exists
 * because the project supplies a comparison file for it, and that is worth
 * having: it lets the tokenizer be checked on its own, so that when the parse
 * tree is wrong you already know whether the tokens underneath it were right.
 */
public class JackAnalyzer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java JackAnalyzer <file.jack | directory>");
            System.exit(1);
        }

        try {
            analyze(Paths.get(args[0]));
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Thrown by the tokenizer or the parser when the source is not
            // valid Jack. The message already carries the file and line.
            System.err.println("Syntax error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Decides the input file list, then analyzes each file in turn. */
    private static void analyze(Path input) throws IOException {
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
            jackFiles = List.of(input);
        }

        for (Path jackFile : jackFiles) {
            analyzeOneFile(jackFile);
        }
    }

    /**
     * Analyzes one .jack file, producing the two .xml files beside it.
     *
     * The file is tokenized once and the token list is walked twice: once to
     * dump the tokens, once to parse them. That is why the tokenizer keeps the
     * whole list rather than streaming — a second pass costs nothing.
     */
    private static void analyzeOneFile(Path jackFile) throws IOException {
        String baseName = stripExtension(jackFile.getFileName().toString());
        Path parent = jackFile.toAbsolutePath().getParent();

        JackTokenizer tokenizer = new JackTokenizer(jackFile);
        int tokenCount = tokenizer.allTokens().size();

        // Pass 1: the flat token stream.
        Path tokenFile = parent.resolve(baseName + "T.xml");
        writeTokenFile(tokenizer, tokenFile);

        // Pass 2: the parse tree. A fresh tokenizer, so the cursor starts at
        // the beginning again.
        Path treeFile = parent.resolve(baseName + ".xml");
        JackTokenizer parseTokens = new JackTokenizer(jackFile);
        try (XmlWriter xml = new XmlWriter(treeFile)) {
            new CompilationEngine(parseTokens, xml).compileClass();
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Prefix the file name so an error in a directory of classes says
            // which class it came from.
            throw new IllegalArgumentException(
                    jackFile.getFileName() + ": " + e.getMessage(), e);
        }

        System.out.printf("%-22s -> %-18s %-18s %4d tokens%n",
                jackFile.getFileName(),
                tokenFile.getFileName(),
                treeFile.getFileName(),
                tokenCount);
    }

    /**
     * Writes the token stream as XxxT.xml.
     *
     * The format is deliberately flat — one element per token, wrapped in a
     * single &lt;tokens&gt; element — because at this stage no structure has
     * been worked out yet. That is the next pass's job.
     */
    private static void writeTokenFile(JackTokenizer tokenizer, Path file) throws IOException {
        try (XmlWriter xml = new XmlWriter(file)) {
            // <tokens> is written without indenting its contents, matching the
            // supplied comparison files.
            xml.rawLine("<tokens>");
            for (JackTokenizer.Token token : tokenizer.allTokens()) {
                xml.token(token);
            }
            xml.rawLine("</tokens>");
        }
    }

    /**
     * Every .jack file directly inside a directory, sorted by name so that the
     * run is deterministic and its output is diffable between runs.
     */
    private static List<Path> listJackFiles(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> result = new ArrayList<>();
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
