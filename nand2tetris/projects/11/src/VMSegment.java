/**
 * VMSegment — the eight memory segments of the VM language.
 *
 * An enum rather than a string so that a misspelled segment is an error in this
 * program rather than a puzzling failure in the VM emulator an hour later.
 */
public enum VMSegment {
    CONSTANT, ARGUMENT, LOCAL, STATIC, THIS, THAT, POINTER, TEMP;

    /** The segment's name as the VM language spells it. */
    public String text() {
        return name().toLowerCase();
    }
}
