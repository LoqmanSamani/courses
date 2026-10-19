import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser — breaks a .vm file into a stream of commands and splits each command
 * into its lexical parts.
 *
 * This class does NO translation. It only answers questions about the command we
 * are currently looking at:
 *
 *      "What kind of command is this?"   -> commandType()
 *      "What is its first argument?"     -> arg1()
 *      "What is its second argument?"    -> arg2()
 *
 * Producing assembly from those pieces is CodeWriter's job. Keeping the two apart
 * is the point of the design: Parser understands VM syntax, CodeWriter understands
 * Hack assembly, and neither knows anything about the other.
 *
 * VM command grammar is extremely simple — every command is one line of
 * whitespace-separated words, with at most three words:
 *
 *      add                     one word   -> arithmetic/logical
 *      push constant 7         three words
 *      pop local 0             three words
 */
public class Parser {

    /**
     * The kinds of command a .vm file can contain.
     *
     * Project 7 only needs the first three. The rest are part of the standard VM
     * language and are recognised here so that Project 8 can simply add handling
     * for them in CodeWriter without touching the Parser.
     */
    public enum CommandType {
        C_ARITHMETIC,   // add, sub, neg, eq, gt, lt, and, or, not
        C_PUSH,         // push <segment> <index>
        C_POP,          // pop  <segment> <index>
        C_LABEL,        // label <name>          (Project 8)
        C_GOTO,         // goto <name>           (Project 8)
        C_IF,           // if-goto <name>        (Project 8)
        C_FUNCTION,     // function <name> <nVars>  (Project 8)
        C_RETURN,       // return                (Project 8)
        C_CALL          // call <name> <nArgs>   (Project 8)
    }

    /** The nine arithmetic/logical commands, which all take no arguments. */
    private static final List<String> ARITHMETIC_COMMANDS = List.of(
            "add", "sub", "neg", "eq", "gt", "lt", "and", "or", "not");

    /** Every meaningful line of the file, already stripped of comments/whitespace. */
    private final List<String> commands = new ArrayList<>();

    /** Index of the command we are currently positioned on. */
    private int cursor = -1;

    /** The current command, pre-split into its words. Refreshed by advance(). */
    private String[] parts = new String[0];

    /**
     * Reads the file and pre-cleans it.
     *
     * Cleaning removes everything the VM does not care about:
     *   - whole-line comments and trailing comments  (// ...)
     *   - blank lines
     *   - leading/trailing whitespace
     *
     * Note that unlike the Hack assembler, we must NOT strip whitespace *inside*
     * a line: the spaces are what separate "push", "constant" and "7".
     */
    public Parser(Path vmFile) throws IOException {
        for (String rawLine : Files.readAllLines(vmFile, StandardCharsets.UTF_8)) {

            // Keep only the text before the first "//".
            int commentStart = rawLine.indexOf("//");
            String line = (commentStart >= 0) ? rawLine.substring(0, commentStart) : rawLine;

            // trim() also removes the stray \r that Windows line endings leave behind.
            line = line.trim();

            if (!line.isEmpty()) {
                commands.add(line);
            }
        }
    }

    /** True while there is still a command after the current one. */
    public boolean hasMoreCommands() {
        return cursor + 1 < commands.size();
    }

    /**
     * Moves to the next command and makes it current.
     *
     * Splitting into words happens here, once, so that commandType(), arg1() and
     * arg2() are all cheap lookups. "\\s+" splits on any run of spaces or tabs.
     */
    public void advance() {
        cursor++;
        parts = commands.get(cursor).split("\\s+");
    }

    /** The current command exactly as written in the source, e.g. "push constant 7". */
    public String currentCommand() {
        return commands.get(cursor);
    }

    /**
     * Classifies the current command by looking at its first word.
     *
     * @throws IllegalArgumentException if the word is not a known VM command.
     */
    public CommandType commandType() {
        String keyword = parts[0];

        if (ARITHMETIC_COMMANDS.contains(keyword)) {
            return CommandType.C_ARITHMETIC;
        }

        switch (keyword) {
            case "push":     return CommandType.C_PUSH;
            case "pop":      return CommandType.C_POP;
            case "label":    return CommandType.C_LABEL;
            case "goto":     return CommandType.C_GOTO;
            case "if-goto":  return CommandType.C_IF;
            case "function": return CommandType.C_FUNCTION;
            case "return":   return CommandType.C_RETURN;
            case "call":     return CommandType.C_CALL;
            default:
                throw new IllegalArgumentException("Unknown VM command: '" + keyword + "'");
        }
    }

    /**
     * The first argument of the current command.
     *
     * For C_ARITHMETIC the command *is* its own argument, so "add" returns "add".
     * That convention comes from the standard VM API and lets CodeWriter take the
     * operation name through the same accessor as a segment name.
     *
     *      "add"              -> "add"
     *      "push constant 7"  -> "constant"
     *      "pop local 0"      -> "local"
     *
     * Must not be called on C_RETURN, which has no arguments.
     */
    public String arg1() {
        if (commandType() == CommandType.C_ARITHMETIC) {
            return parts[0];
        }
        return parts[1];
    }

    /**
     * The second argument of the current command, as an int.
     *
     *      "push constant 7"  -> 7
     *      "pop local 0"      -> 0
     *
     * Only valid for C_PUSH, C_POP, C_FUNCTION and C_CALL.
     */
    public int arg2() {
        return Integer.parseInt(parts[2]);
    }
}
