/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * The pure-DER invocation-layer fault carrier: the JGDMS-STD-006 sec.7.6
 * {@code ThrowableRecord} <em>safe subset</em> (RATIFIED 2026-07-06), realised
 * as the structurally re-typed serial form of {@link ThrowableSerializer}.
 *
 * <h2>Why this exists</h2>
 * <p>The pure DER wire format is declaration-driven (schema generation walks
 * {@code serialForm()} declared types), so {@link ThrowableSerializer}'s
 * {@code cause: Throwable} / {@code suppressed: Throwable[]} /
 * {@code stack: StackTraceElement[]} fields are not schema-typable, and the
 * closed DER registry deliberately defers {@code Throwable}. A remote fault
 * that is not itself {@code @AtomicSerial} therefore cannot cross a pure DER
 * stream at all. This class re-types that form structurally -- every field a
 * schema-typable inert value -- with recursive conversion performed at
 * <em>capture</em> time (the stream-level {@code replaceObject} recursion the
 * atomic JOSS layer relies on does not exist under DER's declaration-driven
 * schema).
 *
 * <h2>Serial form (STD-006 sec.7.6 {@code ThrowableRecord} safe subset)</h2>
 * <pre>
 *   className  : String                        -- the throwable's class NAME (never a Class object)
 *   message    : String (nullable)             -- best-effort original detailMessage
 *   stack      : StackTraceElementSerializer[] -- top-of-stack first, bounded by maxStackFrames
 *   suppressed : DerThrowableForm[]            -- bounded by maxSuppressed; nested safe subset
 *   cause      : DerThrowableForm (nullable)   -- acyclic; bounded by maxCauseDepth
 * </pre>
 * Per the ratified exclusion note, the {@code clazz} ({@code Class}),
 * {@code perm} ({@code Permission}), {@code classname}, {@code length} and
 * {@code eof} fields of the as-built {@link ThrowableSerializer} do <b>not</b>
 * travel: they exist solely to feed reflective reconstruction of specific JDK
 * subclasses and are gadget-adjacent as wire state.
 *
 * <h2>Decode is inert; the typed rebuild is a trusted-seam operation</h2>
 * <p>The DER decoder only ever constructs <em>this fixed carrier type</em> from
 * the inert fields -- this class deliberately does NOT implement {@link Resolve},
 * so no layer of the codec resolves {@link #className()} to a {@code Class} or
 * invokes a constructor from it. The best-effort typed rebuild
 * ({@link #toThrowable(ClassLoader)}) is performed only by trusted local code at
 * the invocation-layer seam ({@code net.jini.jeri.AtomicDerInvocationHandler.unmarshalThrow}),
 * exactly the allowance STD-006 sec.7.6 makes ("a best-effort typed rebuild by
 * trusted local code"). The rebuild reuses {@link ThrowableSerializer}'s
 * battle-tested constructor-matching ({@link ThrowableSerializer#init}): public
 * constructors of shape {@code (String,Exception)} / {@code (String,Error)} /
 * {@code (String,Throwable)} / {@code (Throwable,String)} / {@code (Throwable)} /
 * {@code (String)} / {@code ()} only, with the attach-cause-as-suppressed
 * fallback when {@code initCause} fails, restricted to {@link Throwable}
 * subtypes.
 *
 * <h2>Ceilings and truncation-with-marker (STD-006 sec.4.5)</h2>
 * <p>Capture bounds the carried tree; this is a FAULT path, where silent loss is
 * the worst outcome and a hard in-band reject (which would degrade to a
 * connection abort -- the very failure this class removes) is second-worst, so
 * over-ceiling input is <em>truncated with an explicit marker</em> in the
 * carried data [flagged for review]:
 * <ul>
 *   <li>{@link #MAX_STACK_FRAMES} = 2048 -- STD-006 sec.4.5 {@code maxStackFrames}
 *       [PROPOSED, open item 21]. Over-limit stacks keep the first 2047 frames
 *       plus one synthetic marker frame recording the dropped count.</li>
 *   <li>{@link #MAX_CAUSE_DEPTH} = 12 -- the cause/suppressed tree nesting
 *       ceiling. NOTE: STD-006 sec.4.5 proposes {@code maxCauseDepth} = 64, but
 *       under the nested-{@code @AtomicSerial} representation each tree level
 *       consumes one codec nesting level and {@code ObjectCodec.MAX_NESTING} is
 *       16 (the leaf's stack frames need one more), so 64 is structurally
 *       unreachable; 12 leaves margin. [PROPOSED -- flagged: either sec.4.5's
 *       number comes down for this record or a flattened representation is
 *       adopted.] Deeper nodes are replaced by a marker node preserving the
 *       boundary throwable's class name and message.</li>
 *   <li>{@link #MAX_SUPPRESSED} = 32 per node [PROPOSED -- sec.4.5 names
 *       {@code maxCollection} without a confirmed number]. Over-limit lists keep
 *       the first 31 plus one marker node recording the dropped count.</li>
 *   <li>{@link #MAX_NODES} = 128 total carried nodes per fault [PROPOSED] --
 *       bounds the cause x suppressed fan-out product.</li>
 * </ul>
 * The {@code (GetArg)} constructor REJECTS (fail-secure) input exceeding the
 * per-node ceilings: a conforming encoder truncates at capture, so over-ceiling
 * wire data is non-conformant, not evolution.
 *
 * <p>The cause chain is acyclic on the wire (STD-006 sec.3.7): capture carries a
 * path-based identity guard, truncating (with marker) if a cycle is ever
 * encountered; the JDK {@code cause == this} self-sentinel never enters the form
 * because capture reads {@link Throwable#getCause()}, which maps the sentinel to
 * {@code null}.
 *
 * @author peter
 * @since 4.0
 * @see ThrowableSerializer
 */
@AtomicSerial
public final class DerThrowableForm {

    /** STD-006 sec.4.5 {@code maxStackFrames} [PROPOSED, open item 21]. */
    public static final int MAX_STACK_FRAMES = 2048;
    /**
     * Cause/suppressed tree nesting ceiling. Structurally bounded well below
     * {@code au.net.zeus.jgdms.der.object.ObjectCodec.MAX_NESTING} (16): a
     * carrier node at tree depth d encodes at codec nesting depth d and its
     * stack frames at d+1. [PROPOSED -- deviates from STD-006 sec.4.5's
     * proposed 64; see class javadoc.]
     */
    public static final int MAX_CAUSE_DEPTH = 12;
    /** Per-node suppressed-list ceiling, marker included [PROPOSED]. */
    public static final int MAX_SUPPRESSED = 32;
    /** Total carried nodes per fault (bounds cause x suppressed fan-out) [PROPOSED]. */
    public static final int MAX_NODES = 128;

    private static final String CLASS_NAME = "className";
    private static final String MESSAGE = "message";
    private static final String STACK = "stack";
    private static final String SUPPRESSED = "suppressed";
    private static final String CAUSE = "cause";

    private static final StackTraceElementSerializer[] NO_FRAMES = {};
    private static final DerThrowableForm[] NO_SUPPRESSED = {};

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(CLASS_NAME, String.class),
            new SerialForm(MESSAGE, String.class),
            new SerialForm(STACK, StackTraceElementSerializer[].class),
            new SerialForm(SUPPRESSED, DerThrowableForm[].class),
            new SerialForm(CAUSE, DerThrowableForm.class),
        };
    }

    public static void serialize(PutArg arg, DerThrowableForm obj) throws IOException {
        arg.put(CLASS_NAME, obj.className);
        arg.put(MESSAGE, obj.message);
        arg.put(STACK, obj.stack);
        arg.put(SUPPRESSED, obj.suppressed);
        arg.put(CAUSE, obj.cause);
        arg.writeArgs();
    }

    private final String className;
    private final String message;
    private final StackTraceElementSerializer[] stack;
    private final DerThrowableForm[] suppressed;
    private final DerThrowableForm cause;

    // =========================================================================
    // Capture (server side -- trusted: the server captures its own fault)
    // =========================================================================

    /**
     * Captures {@code t} (message, stack, suppressed and cause chain,
     * recursively) into the bounded safe-subset carrier, truncating with
     * explicit markers at the ceilings.
     *
     * @param t the fault to carry (must not be {@code null})
     * @return the carrier
     * @throws NullPointerException if {@code t} is {@code null}
     */
    public static DerThrowableForm capture(Throwable t) {
        Objects.requireNonNull(t, "throwable");
        return capture(t, 0, new IdentityHashMap<Throwable, Boolean>(), new int[]{0});
    }

    private static DerThrowableForm capture(Throwable t, int depth,
            IdentityHashMap<Throwable, Boolean> path, int[] nodeCount) {
        nodeCount[0]++;
        if (depth >= MAX_CAUSE_DEPTH) {
            return marker(t, "[JGDMS DER fault: cause/suppressed tree truncated at depth "
                    + MAX_CAUSE_DEPTH + "; deeper throwables dropped]");
        }
        if (nodeCount[0] > MAX_NODES) {
            return marker(t, "[JGDMS DER fault: throwable tree truncated at "
                    + MAX_NODES + " nodes; further throwables dropped]");
        }
        if (path.containsKey(t)) {
            // A cyclic cause/suppressed graph (only constructible by initCause
            // abuse); the wire form is acyclic (STD-006 sec.3.7).
            return marker(t, "[JGDMS DER fault: cyclic cause/suppressed chain truncated]");
        }
        path.put(t, Boolean.TRUE);
        try {
            String message = ThrowableSerializer.captureMessage(t);
            StackTraceElementSerializer[] stack = captureStack(t.getStackTrace());
            // getCause() maps the JDK cause == this self-sentinel to null, so the
            // sentinel never enters the form (reconstruction leaves cause unset).
            Throwable tCause = t.getCause();
            DerThrowableForm cause = tCause == null
                    ? null : capture(tCause, depth + 1, path, nodeCount);
            Throwable[] tSup = t.getSuppressed();
            DerThrowableForm[] suppressed;
            if (tSup == null || tSup.length == 0) {
                suppressed = NO_SUPPRESSED;
            } else {
                int kept = Math.min(tSup.length,
                        tSup.length > MAX_SUPPRESSED ? MAX_SUPPRESSED - 1 : MAX_SUPPRESSED);
                boolean truncated = kept < tSup.length;
                suppressed = new DerThrowableForm[truncated ? kept + 1 : kept];
                for (int i = 0; i < kept; i++) {
                    suppressed[i] = capture(tSup[i], depth + 1, path, nodeCount);
                }
                if (truncated) {
                    suppressed[kept] = new DerThrowableForm(
                            "java.lang.Throwable",
                            "[JGDMS DER fault: " + (tSup.length - kept)
                            + " suppressed exception(s) dropped (max "
                            + MAX_SUPPRESSED + " per node)]",
                            NO_FRAMES, NO_SUPPRESSED, null);
                }
            }
            return new DerThrowableForm(t.getClass().getName(), message, stack,
                    suppressed, cause);
        } finally {
            path.remove(t);
        }
    }

    /**
     * A truncation marker node: preserves the boundary throwable's class name
     * and (marker-prefixed) message; carries no stack and no children. Loss is
     * explicit -- never silent -- on the fault path.
     */
    private static DerThrowableForm marker(Throwable t, String markerPrefix) {
        String original = ThrowableSerializer.captureMessage(t);
        return new DerThrowableForm(t.getClass().getName(),
                original == null ? markerPrefix : markerPrefix + " " + original,
                NO_FRAMES, NO_SUPPRESSED, null);
    }

    private static StackTraceElementSerializer[] captureStack(StackTraceElement[] frames) {
        if (frames == null || frames.length == 0) return NO_FRAMES;
        int kept = Math.min(frames.length,
                frames.length > MAX_STACK_FRAMES ? MAX_STACK_FRAMES - 1 : MAX_STACK_FRAMES);
        boolean truncated = kept < frames.length;
        StackTraceElementSerializer[] result =
                new StackTraceElementSerializer[truncated ? kept + 1 : kept];
        for (int i = 0; i < kept; i++) {
            result[i] = new StackTraceElementSerializer(frames[i]);
        }
        if (truncated) {
            result[kept] = new StackTraceElementSerializer(new StackTraceElement(
                    "[JGDMS-DER-truncated]",
                    "dropped_" + (frames.length - kept) + "_frames", null, -1));
        }
        return result;
    }

    private DerThrowableForm(String className, String message,
            StackTraceElementSerializer[] stack, DerThrowableForm[] suppressed,
            DerThrowableForm cause) {
        this.className = className;
        this.message = message;
        this.stack = stack;
        this.suppressed = suppressed;
        this.cause = cause;
    }

    // =========================================================================
    // Deserialization (untrusted wire input -- inert fields only, fail-secure)
    // =========================================================================

    /**
     * {@code @AtomicSerial} deserialization constructor. Validates the inert
     * fields against the per-node ceilings (a conforming encoder truncates at
     * capture, so over-ceiling wire data is rejected as non-conformant, never
     * tolerated). Performs NO class resolution and NO construction of the named
     * type -- see {@link #toThrowable(ClassLoader)}.
     */
    public DerThrowableForm(GetArg arg) throws IOException, ClassNotFoundException {
        this(check(arg));
    }

    private DerThrowableForm(DerThrowableForm checked) {
        this(checked.className, checked.message, checked.stack,
                checked.suppressed, checked.cause);
    }

    private static DerThrowableForm check(GetArg arg)
            throws IOException, ClassNotFoundException {
        String className = Valid.notNull(arg.get(CLASS_NAME, null, String.class),
                "className cannot be null");
        if (className.isEmpty() || className.length() > 2048) {
            throw new InvalidObjectException(
                    "className length out of bounds: " + className.length());
        }
        String message = arg.get(MESSAGE, null, String.class);
        StackTraceElementSerializer[] stack =
                arg.get(STACK, null, StackTraceElementSerializer[].class);
        if (stack == null) {
            stack = NO_FRAMES;
        } else if (stack.length > MAX_STACK_FRAMES) {
            throw new InvalidObjectException("stack frame count " + stack.length
                    + " exceeds maxStackFrames (" + MAX_STACK_FRAMES + ")");
        } else {
            stack = stack.clone();
            for (int i = 0; i < stack.length; i++) {
                if (stack[i] == null) {
                    throw new InvalidObjectException("null stack frame at index " + i);
                }
            }
        }
        DerThrowableForm[] suppressed =
                arg.get(SUPPRESSED, null, DerThrowableForm[].class);
        if (suppressed == null) {
            suppressed = NO_SUPPRESSED;
        } else if (suppressed.length > MAX_SUPPRESSED) {
            throw new InvalidObjectException("suppressed count " + suppressed.length
                    + " exceeds maxSuppressed (" + MAX_SUPPRESSED + ")");
        } else {
            suppressed = suppressed.clone();
            for (int i = 0; i < suppressed.length; i++) {
                if (suppressed[i] == null) {
                    throw new InvalidObjectException("null suppressed element at index " + i);
                }
            }
        }
        DerThrowableForm cause = arg.get(CAUSE, null, DerThrowableForm.class);
        // Tree depth beyond this node is structurally bounded by the DER codec's
        // nesting ceiling (ObjectCodec.MAX_NESTING); no re-count is needed here.
        return new DerThrowableForm(className, message, stack, suppressed, cause);
    }

    // =========================================================================
    // Trusted-seam typed rebuild (STD-006 sec.7.6: "best-effort typed rebuild
    // by trusted local code")
    // =========================================================================

    /**
     * Rebuilds the carried fault as its named type, best-effort, via
     * {@link ThrowableSerializer}'s constructor-matching reconstruction.
     *
     * <p><b>Trusted-seam operation ONLY.</b> This method resolves the carried
     * class name and invokes a public constructor of the resolved class; it must
     * be invoked only by trusted invocation-layer code on the receiving side
     * (e.g. {@code AtomicDerInvocationHandler.unmarshalThrow}), never by the
     * decoder itself and never with an ambient/thread-context loader. The
     * resolved class must be a {@link Throwable} subtype; constructor selection
     * is restricted to {@link ThrowableSerializer#init}'s fixed public shapes
     * with inert arguments (the carried message string and the recursively
     * rebuilt cause).
     *
     * @param resolver the endpoint-assigned loader to resolve the carried class
     *                 names against (the proxy's own loader on the client seam);
     *                 {@code null} uses this class's defining loader
     * @return the rebuilt throwable, with stack trace and suppressed list applied
     * @throws ClassNotFoundException if a carried class name does not resolve --
     *         the caller surfaces this loudly (an {@code UnmarshalException} at
     *         the invocation layer), matching the atomic layer's behaviour
     * @throws InvalidObjectException if a resolved class is not a Throwable
     * @throws IOException if construction fails
     */
    public Throwable toThrowable(ClassLoader resolver)
            throws IOException, ClassNotFoundException {
        ClassLoader loader = resolver != null
                ? resolver : DerThrowableForm.class.getClassLoader();
        Class<?> clas = Class.forName(className, false, loader);
        if (!Throwable.class.isAssignableFrom(clas)) {
            throw new InvalidObjectException(
                    "carried fault class is not a Throwable: " + className);
        }
        Throwable rebuiltCause = cause == null ? null : cause.toThrowable(resolver);
        @SuppressWarnings("unchecked")
        Throwable result = ThrowableSerializer.init(
                (Class<? extends Throwable>) clas, message, rebuiltCause);
        StackTraceElement[] frames = new StackTraceElement[stack.length];
        for (int i = 0; i < stack.length; i++) {
            frames[i] = stack[i].toStackTraceElement();
        }
        result.setStackTrace(frames);
        for (int i = 0; i < suppressed.length; i++) {
            // Only adds if enabled (not disabled by Throwable's protected ctor).
            result.addSuppressed(suppressed[i].toThrowable(resolver));
        }
        return result;
    }

    // =========================================================================
    // Accessors (inert data -- diagnostics and tests)
    // =========================================================================

    /** The carried throwable's class name (data, not an instruction to instantiate). */
    public String className() { return className; }

    /** The carried best-effort original detail message; may be {@code null}. */
    public String message() { return message; }

    /** The carried cause node; may be {@code null}. */
    public DerThrowableForm cause() { return cause; }

    /** The carried suppressed nodes (defensive copy). */
    public DerThrowableForm[] suppressed() { return suppressed.clone(); }

    /** The carried stack frame count. */
    public int stackFrameCount() { return stack.length; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DerThrowableForm)) return false;
        DerThrowableForm that = (DerThrowableForm) o;
        if (!className.equals(that.className)) return false;
        if (!Objects.equals(message, that.message)) return false;
        if (stack.length != that.stack.length) return false;
        for (int i = 0; i < stack.length; i++) {
            if (!stack[i].equals(that.stack[i])) return false;
        }
        if (suppressed.length != that.suppressed.length) return false;
        for (int i = 0; i < suppressed.length; i++) {
            if (!suppressed[i].equals(that.suppressed[i])) return false;
        }
        return Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, message, stack.length, suppressed.length,
                cause);
    }

    @Override
    public String toString() {
        return "DerThrowableForm{" + className
                + (message != null ? ": " + message : "")
                + ", frames=" + stack.length
                + ", suppressed=" + suppressed.length
                + (cause != null ? ", cause=" + cause.className : "") + '}';
    }
}
