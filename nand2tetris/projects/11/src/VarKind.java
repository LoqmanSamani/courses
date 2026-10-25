/**
 * VarKind — what sort of variable a name denotes.
 *
 * The four real kinds are exactly the four variable scopes Jack has, and each
 * corresponds to a VM segment: static to `static`, field to `this`, argument to
 * `argument`, local to `local`.
 *
 * NONE is not a kind of variable. It is the answer to "I looked this name up
 * and it is not a variable at all", which happens constantly and is not an
 * error: in `Screen.drawLine(...)` the name `Screen` is a class, and it is
 * precisely by failing this lookup that the compiler tells a class name from an
 * object held in a variable.
 */
public enum VarKind {
    STATIC, FIELD, ARG, VAR, NONE
}
