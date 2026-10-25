import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * VMWriter — the only class that knows what a VM command looks like.
 *
 * Every method here writes exactly one line. Keeping the spelling of the VM
 * language in one place means CompilationEngine can be read as a statement
 * about the Jack language ("a while loop evaluates its condition, negates it
 * and jumps") without the text of the output getting in the way.
 *
 * The enums exist so that a wrong segment name is a compile error in this
 * program rather than a puzzling failure in the VM emulator an hour later.
 */
public class VMWriter implements Closeable {

    private final Writer out;

    public VMWriter(Path file) throws IOException {
        this.out = new BufferedWriter(
                Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    public void writePush(VMSegment segment, int index) throws IOException {
        line("push " + segment.text() + " " + index);
    }

    public void writePop(VMSegment segment, int index) throws IOException {
        line("pop " + segment.text() + " " + index);
    }

    public void writeArithmetic(VMCommand command) throws IOException {
        line(command.text());
    }

    public void writeLabel(String label) throws IOException {
        line("label " + label);
    }

    public void writeGoto(String label) throws IOException {
        line("goto " + label);
    }

    public void writeIf(String label) throws IOException {
        line("if-goto " + label);
    }

    public void writeCall(String name, int nArgs) throws IOException {
        line("call " + name + " " + nArgs);
    }

    public void writeFunction(String name, int nLocals) throws IOException {
        line("function " + name + " " + nLocals);
    }

    public void writeReturn() throws IOException {
        line("return");
    }

    /**
     * A comment naming the piece of Jack that the following commands came from.
     *
     * The VM emulator ignores these. They are for the person reading the output
     * and trying to work out which line of their program produced it, which
     * while writing a compiler is most of the job.
     */
    public void writeComment(String text) throws IOException {
        line("// " + text);
    }

    private void line(String text) throws IOException {
        out.write(text);
        out.write("\n");
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
