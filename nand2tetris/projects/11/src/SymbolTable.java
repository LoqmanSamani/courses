import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * SymbolTable — what every identifier in the program means.
 *
 * The analyzer of project 10 treated every identifier alike: a name was a name.
 * A compiler cannot. To emit code for `let count = count + 1` it has to know
 * whether `count` is a static variable, a field of the current object, an
 * argument or a local, because each lives in a different VM segment, and it has
 * to know which slot of that segment. That is all this class records.
 *
 * TWO SCOPES, NOT MORE
 * Jack has exactly two levels of scope, which is why this is a pair of maps
 * rather than a stack of them:
 *
 *   class scope        static and field variables, alive for the whole class
 *   subroutine scope   arguments and locals, thrown away and rebuilt at the
 *                      start of every subroutine
 *
 * A lookup tries the subroutine scope first, so an argument named `x` hides a
 * field named `x` — which is the usual rule, and the reason the order matters.
 *
 * The running indices are the other half of the job. Each kind is numbered from
 * zero independently, because each kind maps to its own VM segment: the third
 * argument is `argument 2` no matter how many fields the class has.
 */
public class SymbolTable {

    /** One variable: its declared type and where it lives. */
    private static final class Entry {
        final String type;
        final VarKind kind;
        final int index;

        Entry(String type, VarKind kind, int index) {
            this.type = type;
            this.kind = kind;
            this.index = index;
        }
    }

    private final Map<String, Entry> classScope = new HashMap<String, Entry>();
    private Map<String, Entry> subroutineScope = new HashMap<String, Entry>();
    private final Map<VarKind, Integer> counts = new EnumMap<VarKind, Integer>(VarKind.class);

    public SymbolTable() {
        resetCount(VarKind.STATIC);
        resetCount(VarKind.FIELD);
        startSubroutine();
    }

    /**
     * Begins a new subroutine: forgets every argument and local.
     *
     * The class scope survives, which is the whole point of keeping them apart.
     */
    public void startSubroutine() {
        subroutineScope = new HashMap<String, Entry>();
        resetCount(VarKind.ARG);
        resetCount(VarKind.VAR);
    }

    /** Records a new variable and gives it the next index of its kind. */
    public void define(String name, String type, VarKind kind) {
        int index = varCount(kind);
        counts.put(kind, index + 1);
        Entry entry = new Entry(type, kind, index);
        if (kind == VarKind.STATIC || kind == VarKind.FIELD) {
            classScope.put(name, entry);
        } else {
            subroutineScope.put(name, entry);
        }
    }

    /**
     * How many variables of this kind have been defined.
     *
     * Used for two things that look unrelated but are the same question: how
     * many locals a subroutine needs (`function Foo.bar 3`) and how many words
     * an object of this class occupies (the argument to Memory.alloc).
     */
    public int varCount(VarKind kind) {
        Integer n = counts.get(kind);
        return n == null ? 0 : n.intValue();
    }

    public VarKind kindOf(String name) {
        Entry e = lookup(name);
        return e == null ? VarKind.NONE : e.kind;
    }

    /** The declared type, or null if the name is not a variable. */
    public String typeOf(String name) {
        Entry e = lookup(name);
        return e == null ? null : e.type;
    }

    /** The index within its segment, or -1 if the name is not a variable. */
    public int indexOf(String name) {
        Entry e = lookup(name);
        return e == null ? -1 : e.index;
    }

    /** Subroutine scope first, so a local or argument hides a field. */
    private Entry lookup(String name) {
        Entry e = subroutineScope.get(name);
        return e != null ? e : classScope.get(name);
    }

    private void resetCount(VarKind kind) {
        counts.put(kind, Integer.valueOf(0));
    }
}
