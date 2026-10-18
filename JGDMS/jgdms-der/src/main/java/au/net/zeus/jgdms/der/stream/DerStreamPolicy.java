/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.stream;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The whole-stream limits a DER object stream is admitted against (SOW-DER-Streaming-Encoder B5).
 *
 * <p>Because admission sees the WHOLE stream before anything is built, it can apply checks no item-by-item reader can:
 * a bound on how many objects and elements the stream will build, a bound on how many distinct classes it names, and the
 * refusal of a class name bound to two different schemas. Each rejects the entire stream before a class is loaded or a
 * constructor runs. These are <b>new rejects</b>: a stream valid before may be refused, so they are configurable, and the
 * defaults sit far above anything the golden corpus and the benchmark payloads produce. They run in the FULL admission
 * stage, after the caller is authorized.
 *
 * <p><b>There are deliberately no class-name allow or deny lists.</b> A list of names breaks encapsulation (a service's
 * implementation classes would have to be published to whoever configures the policy) and cannot see codebase or signer.
 * Which classes may be reconstructed is decided by the existing permission checks, {@code DeSerializationPermission}
 * ({@code ATOMIC} and {@code PROXY}) and the preferred-class and integrity machinery of the
 * {@link au.net.zeus.jgdms.der.getarg.ResolutionContext}, which run when each object is read.
 *
 * <h2>What is counted</h2>
 * <b>Objects:</b> every record site (stream, nested or a {@code [8]} handler), enum constant, proxy, non-null array and
 * collection the stream builds. <b>Elements:</b> the children of every collection, map and reference-component array (an
 * array of {@code int}, {@code long} ... is bounded by the input size and delivery has no cap on it, so it is not
 * counted). <b>Distinct classes:</b> the names of every schema-chain record (leaf and ancestors), {@code [7]} enum,
 * {@code [8]} proxy interface, array component, wire-type class ({@code @AtomicSerial:}, {@code enum:}) and
 * {@code java.lang.Class} value, matched as the loader would receive them (an array-class name by its element class).
 * <b>Rebinding:</b> one class name bound to two schemas within one scope, the scope being the stream's own dedup table or
 * one forwarded {@code [8]}/{@code [16]} region (a re-forwarded proxy legitimately carries its origin's version of a
 * class). It is a tripwire, not a boundary: a sender can always open another {@code [8]} region.
 *
 * <p>Payloads the stream carries as opaque bytes and that are decoded LATER (a {@code MarshalledInstance} or
 * {@code MarshalledObject} body, an {@code Entry} representation) are outside the policy, as SOW B.2.3 says of all of
 * Part B.
 *
 * <h2>System properties</h2>
 * Read once, on first use. A value that is not a positive integer (ASCII digits, at most {@code Integer.MAX_VALUE}) or not
 * {@code true}/{@code false} is replaced by the default with a warning, so a mistake can never switch a limit off.
 * <table>
 *   <caption>Policy properties</caption>
 *   <tr><th>Property</th><th>Meaning</th><th>Default</th></tr>
 *   <tr><td>{@code au.net.zeus.jgdms.der.stream.policy.maxObjects}</td><td>Most objects one stream may build</td>
 *       <td>{@value #DEFAULT_MAX_OBJECTS}</td></tr>
 *   <tr><td>{@code ...maxElements}</td><td>Most collection, map and reference-array elements in one stream</td>
 *       <td>{@value #DEFAULT_MAX_ELEMENTS}</td></tr>
 *   <tr><td>{@code ...maxDistinctClasses}</td><td>Most distinct class names one stream may name</td>
 *       <td>{@value #DEFAULT_MAX_DISTINCT_CLASSES}</td></tr>
 *   <tr><td>{@code ...rejectRebinding}</td><td>{@code true}: refuse a class name bound to two schemas in one scope</td>
 *       <td>{@code true}</td></tr>
 * </table>
 * Nesting depth is already bounded by the fixed {@code MAX_NESTING}; it has no knob. Rejections are logged at
 * {@code WARNING} by logger {@code au.net.zeus.jgdms.der.stream.policy}, at most five lines a second per JVM, with names
 * truncated and made printable; at {@code FINE} the distinct class names of each admitted stream are logged. Instances are
 * immutable.
 */
public final class DerStreamPolicy {

    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.stream.policy");

    /** Prefix of every policy property. */
    public static final String PROP_PREFIX = "au.net.zeus.jgdms.der.stream.policy.";
    public static final String PROP_MAX_OBJECTS = PROP_PREFIX + "maxObjects";
    public static final String PROP_MAX_ELEMENTS = PROP_PREFIX + "maxElements";
    public static final String PROP_MAX_DISTINCT_CLASSES = PROP_PREFIX + "maxDistinctClasses";
    public static final String PROP_REJECT_REBINDING = PROP_PREFIX + "rejectRebinding";

    /**
     * Default object budget. A 16 MiB stream (the default input cap) can hold at most about 400 000 record sites (the
     * smallest nested site is about 42 bytes), so this binds at the default cap while being more than two orders of
     * magnitude above the largest stream measured (see the SOW B5 outcome).
     */
    public static final int DEFAULT_MAX_OBJECTS = 262_144;

    /**
     * Default element budget (about 4 million collection, map and reference-array elements in total): 64 collections at the
     * per-collection ceiling of 65 536, and below the 5.6 million elements of the smallest encoding that fit the default
     * 16 MiB input cap.
     */
    public static final int DEFAULT_MAX_ELEMENTS = 4_194_304;

    /** Default distinct-class cap; the per-stream chain table already holds at most 256 chains. */
    public static final int DEFAULT_MAX_DISTINCT_CLASSES = 1_024;

    /** A class name longer than this cannot be a loadable class (the constant-pool limit); such a name is refused. */
    static final int MAX_CLASS_NAME_LENGTH = 65_535;

    /** The policy with every default, ignoring system properties. */
    public static final DerStreamPolicy BUILT_IN =
            new DerStreamPolicy(DEFAULT_MAX_OBJECTS, DEFAULT_MAX_ELEMENTS, DEFAULT_MAX_DISTINCT_CLASSES, true);

    private static volatile DerStreamPolicy configured;

    /**
     * The policy used by every stream-format reader built without an explicit one: the defaults, overridden by system
     * properties, read once.
     */
    public static DerStreamPolicy defaults() {
        DerStreamPolicy p = configured;
        if (p == null) {
            synchronized (DerStreamPolicy.class) {
                p = configured;
                if (p == null) {
                    p = fromProperties(DerStreamPolicy::systemProperty);
                    configured = p;
                }
            }
        }
        return p;
    }

    /** Test seam: forget the cached reading so the next {@link #defaults()} re-reads the system properties. */
    static synchronized void resetDefaultsForTest() {
        configured = null;
    }

    private final int maxObjects;
    private final int maxElements;
    private final int maxDistinctClasses;
    private final boolean rejectRebinding;

    private DerStreamPolicy(int maxObjects, int maxElements, int maxDistinctClasses, boolean rejectRebinding) {
        this.maxObjects = maxObjects;
        this.maxElements = maxElements;
        this.maxDistinctClasses = maxDistinctClasses;
        this.rejectRebinding = rejectRebinding;
    }

    public int maxObjects() { return maxObjects; }
    public int maxElements() { return maxElements; }
    public int maxDistinctClasses() { return maxDistinctClasses; }
    public boolean rejectRebinding() { return rejectRebinding; }

    public DerStreamPolicy withMaxObjects(int n) {
        return new DerStreamPolicy(positive(n, "maxObjects"), maxElements, maxDistinctClasses, rejectRebinding);
    }

    public DerStreamPolicy withMaxElements(int n) {
        return new DerStreamPolicy(maxObjects, positive(n, "maxElements"), maxDistinctClasses, rejectRebinding);
    }

    public DerStreamPolicy withMaxDistinctClasses(int n) {
        return new DerStreamPolicy(maxObjects, maxElements, positive(n, "maxDistinctClasses"), rejectRebinding);
    }

    public DerStreamPolicy withRejectRebinding(boolean reject) {
        return new DerStreamPolicy(maxObjects, maxElements, maxDistinctClasses, reject);
    }

    private static int positive(int n, String what) {
        if (n <= 0) {
            throw new IllegalArgumentException(what + " must be positive, got " + n);
        }
        return n;
    }

    @Override
    public String toString() {
        return "DerStreamPolicy[maxObjects=" + maxObjects + ", maxElements=" + maxElements
                + ", maxDistinctClasses=" + maxDistinctClasses + ", rejectRebinding=" + rejectRebinding + "]";
    }

    // =========================================================================
    // Property parsing (pure, so it can be tested without touching system state)
    // =========================================================================

    @SuppressWarnings("removal")
    private static String systemProperty(String name) {
        try {
            // privileged, so the answer does not depend on which caller's protection domain happened to read it first
            return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(name));
        } catch (SecurityException e) {
            LOGGER.log(Level.WARNING, "Cannot read system property {0} ({1}); using the default",
                    new Object[] {name, e.toString()});
            return null;
        }
    }

    /** Builds a policy from a property source, applying the validation rules in the class Javadoc. */
    static DerStreamPolicy fromProperties(Function<String, String> source) {
        return new DerStreamPolicy(
                parseBudget(source.apply(PROP_MAX_OBJECTS), DEFAULT_MAX_OBJECTS, PROP_MAX_OBJECTS),
                parseBudget(source.apply(PROP_MAX_ELEMENTS), DEFAULT_MAX_ELEMENTS, PROP_MAX_ELEMENTS),
                parseBudget(source.apply(PROP_MAX_DISTINCT_CLASSES), DEFAULT_MAX_DISTINCT_CLASSES, PROP_MAX_DISTINCT_CLASSES),
                parseBoolean(source.apply(PROP_REJECT_REBINDING), true, PROP_REJECT_REBINDING));
    }

    /** Positive integer, else {@code def} with a warning: a bad value can never turn a budget off. */
    static int parseBudget(String raw, int def, String propName) {
        if (raw == null) {
            return def;
        }
        String t = raw.trim();
        // ASCII digits only: Integer.parseInt also accepts digits of other scripts, and a sign has no place in a budget
        boolean digits = !t.isEmpty() && t.length() <= 10 && t.chars().allMatch(c -> c >= '0' && c <= '9');
        if (digits) {
            long v = Long.parseLong(t);
            if (v > Integer.MAX_VALUE) {
                LOGGER.log(Level.WARNING, "Value {0} for {1} is larger than {2}; using the default {3}",
                        new Object[] {v, propName, Integer.MAX_VALUE, def});
            } else if (v > 0) {
                return (int) v;
            } else {
                LOGGER.log(Level.WARNING, "Zero for {0} would disable the limit; using the default {1}",
                        new Object[] {propName, def});
            }
        } else {
            LOGGER.log(Level.WARNING, "Value \"{0}\" for {1} is not a positive integer; using the default {2}",
                    new Object[] {printable(raw, 40), propName, def});
        }
        return def;
    }

    /** {@code true} or {@code false} (any case), else {@code def} with a warning. */
    static boolean parseBoolean(String raw, boolean def, String propName) {
        if (raw == null) {
            return def;
        }
        String v = raw.trim();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        LOGGER.log(Level.WARNING, "Value \"{0}\" for {1} is neither true nor false; using the default {2}",
                new Object[] {printable(raw, 40), propName, def});
        return def;
    }

    /** A name made safe to put in a message or a log line: printable ASCII only, truncated. */
    static String printable(String s) {
        return printable(s, 120);
    }

    static String printable(String s, int limit) {
        int n = Math.min(s.length(), limit);
        StringBuilder b = new StringBuilder(n + 3);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            b.append(c >= 0x20 && c < 0x7F ? c : '?');
        }
        if (s.length() > n) {
            b.append("...");
        }
        return b.toString();
    }
}
