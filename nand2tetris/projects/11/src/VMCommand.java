/**
 * VMCommand — the nine arithmetic and logical commands of the VM language.
 *
 * Multiplication and division are deliberately absent: the Hack ALU cannot do
 * them, so Jack's * and / become calls to Math.multiply and Math.divide. That
 * omission is a fact about the hardware showing through two levels of
 * abstraction.
 */
public enum VMCommand {
    ADD, SUB, NEG, EQ, GT, LT, AND, OR, NOT;

    /** The command's name as the VM language spells it. */
    public String text() {
        return name().toLowerCase();
    }
}
