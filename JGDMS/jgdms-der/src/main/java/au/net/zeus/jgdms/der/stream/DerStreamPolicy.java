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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The whole-stream security policy a DER object stream is admitted against (SOW-DER-Streaming-Encoder B5).
 *
 * <p>Because admission sees the WHOLE stream before anything is built, it can apply checks no item-by-item reader can:
 * a class the stream names that the operator has not permitted, a number of objects or elements the operator does not
 * want constructed, a class name bound to two different schemas. Each rejects the entire stream before a class is loaded
 * or a constructor runs. These are <b>new rejects</b>: a stream valid before may be refused, so they are configurable, and
 * the defaults are chosen to sit above anything the golden corpus and the benchmark payloads produce (see the SOW's B5
 * outcome for the measurement). They run in the FULL admission stage, after the caller is authorized.
 *
 * <h2>What a "class name" is</h2>
 * Every name the stream can make the {@link au.net.zeus.jgdms.der.getarg.ResolutionContext} load, or constructs, in the
 * order it appears, without loading any of them: the class of every schema-chain record (leaf and ancestors) in the
 * stream, in a {@code [8]}/{@code [16]} region, or nested in a record; the class named by a {@code [7]} enum item; every
 * interface a {@code [8]} proxy names; the classes a field or collection wire type names ({@code @AtomicSerial:<class>},
 * {@code enum:<class>}, array components, in a schema or in a {@code [9]}/{@code [16]} token); and the value of every
 * {@code java.lang.Class} field or element. Array-class names are reduced to their element class, and primitive names
 * are not classes to load.
 *
 * <h2>System properties</h2>
 * Read once, when this class is initialised. A malformed number, a non-positive number or an unknown boolean is
 * replaced by the default with a warning (a mistake can never switch a limit off). <b>A malformed allow/deny entry
 * makes this class refuse to initialise</b> (an {@link ExceptionInInitializerError}, so every DER stream fails loudly):
 * silently dropping a deny entry would be a weaker policy than the operator wrote, and there is no safe recovery that
 * respects the other entries.
 * <table>
 *   <caption>Policy properties</caption>
 *   <tr><th>Property</th><th>Meaning</th><th>Default</th></tr>
 *   <tr><td>{@code au.net.zeus.jgdms.der.stream.policy.maxObjects}</td>
 *       <td>Most objects one stream may build: record sites (a constructor each), enum constants, proxies, arrays and
 *       collections</td><td>{@value #DEFAULT_MAX_OBJECTS}</td></tr>
 *   <tr><td>{@code ...maxElements}</td><td>Most collection, map and array elements in one stream, in total</td>
 *       <td>{@value #DEFAULT_MAX_ELEMENTS}</td></tr>
 *   <tr><td>{@code ...maxDistinctClasses}</td><td>Most distinct class names one stream may name</td>
 *       <td>{@value #DEFAULT_MAX_DISTINCT_CLASSES}</td></tr>
 *   <tr><td>{@code ...rejectRebinding}</td><td>{@code true}: refuse a stream in which one class name is bound to two
 *       different schemas</td><td>{@code true}</td></tr>
 *   <tr><td>{@code ...allowClasses}</td><td>Comma-separated names; when set, every class the stream names must match
 *       one</td><td>unset (no restriction)</td></tr>
 *   <tr><td>{@code ...denyClasses}</td><td>Comma-separated names; a stream naming a matching class is refused (wins
 *       over {@code allowClasses})</td><td>unset</td></tr>
 * </table>
 * An allow/deny entry is an exact binary name ({@code a.b.C}, {@code a.b.C$D}), {@code a.b.*} (classes directly in
 * package {@code a.b}) or {@code a.b.**} (package {@code a.b} and every package below it). Names are case-sensitive.
 * An allow-list must name the framework classes the deployment's own traffic carries (the remote-invocation and
 * lookup types, the serializers); set {@code java.util.logging} for {@code au.net.zeus.jgdms.der.stream.policy} to
 * {@code FINE} to see the distinct names of each admitted stream while building one.
 *
 * <p>Nesting depth is already bounded by the fixed {@code MAX_NESTING}; it has no knob.
 *
 * <p>Rejections are logged at {@code WARNING} by logger {@code au.net.zeus.jgdms.der.stream.policy}, once per rejected
 * stream, with names truncated and made printable. Instances are immutable.
 */
public final class DerStreamPolicy {

    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.stream.policy");

    /** Prefix of every policy property. */
    public static final String PROP_PREFIX = "au.net.zeus.jgdms.der.stream.policy.";
    public static final String PROP_MAX_OBJECTS = PROP_PREFIX + "maxObjects";
    public static final String PROP_MAX_ELEMENTS = PROP_PREFIX + "maxElements";
    public static final String PROP_MAX_DISTINCT_CLASSES = PROP_PREFIX + "maxDistinctClasses";
    public static final String PROP_REJECT_REBINDING = PROP_PREFIX + "rejectRebinding";
    public static final String PROP_ALLOW_CLASSES = PROP_PREFIX + "allowClasses";
    public static final String PROP_DENY_CLASSES = PROP_PREFIX + "denyClasses";

    /**
     * Default object budget. A 16 MiB stream (the default input cap) can hold at most about 400 000 record sites
     * (the smallest nested site is about 42 bytes), so this is a real bound at the default cap while being more than two
     * orders of magnitude above the largest stream measured (see the SOW B5 outcome).
     */
    public static final int DEFAULT_MAX_OBJECTS = 262_144;

    /** Default element budget (one million collection, map and array elements in total). */
    public static final int DEFAULT_MAX_ELEMENTS = 1_048_576;

    /** Default distinct-class cap; the per-stream chain table already holds at most 256 chains. */
    public static final int DEFAULT_MAX_DISTINCT_CLASSES = 1_024;

    /** A class name longer than this cannot be a loadable class (the constant-pool limit); such a name is refused. */
    static final int MAX_CLASS_NAME_LENGTH = 65_535;

    private static final Pattern RULE = Pattern.compile(
            "[\\p{L}_$][\\p{L}\\p{N}_$]*(\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(\\.\\*{1,2})?");

    /** The policy with every default and no allow/deny list, ignoring system properties. */
    public static final DerStreamPolicy BUILT_IN =
            new DerStreamPolicy(DEFAULT_MAX_OBJECTS, DEFAULT_MAX_ELEMENTS, DEFAULT_MAX_DISTINCT_CLASSES, true,
                    Collections.emptyList(), Collections.emptyList());

    /** The policy used by every stream-format reader built without an explicit one: the defaults, overridden by system properties. */
    public static final DerStreamPolicy DEFAULT = fromProperties(DerStreamPolicy::systemProperty);

    private final int maxObjects;
    private final int maxElements;
    private final int maxDistinctClasses;
    private final boolean rejectRebinding;
    private final List<String> allow;
    private final List<String> deny;
    private final Rules allowRules;
    private final Rules denyRules;

    private DerStreamPolicy(int maxObjects, int maxElements, int maxDistinctClasses, boolean rejectRebinding,
                            List<String> allow, List<String> deny) {
        this.maxObjects = maxObjects;
        this.maxElements = maxElements;
        this.maxDistinctClasses = maxDistinctClasses;
        this.rejectRebinding = rejectRebinding;
        this.allow = List.copyOf(allow);
        this.deny = List.copyOf(deny);
        this.allowRules = new Rules(this.allow);
        this.denyRules = new Rules(this.deny);
    }

    public int maxObjects() { return maxObjects; }
    public int maxElements() { return maxElements; }
    public int maxDistinctClasses() { return maxDistinctClasses; }
    public boolean rejectRebinding() { return rejectRebinding; }
    /** The allow entries as written (empty: no restriction). */
    public List<String> allowClasses() { return allow; }
    /** The deny entries as written. */
    public List<String> denyClasses() { return deny; }

    public DerStreamPolicy withMaxObjects(int n) {
        return new DerStreamPolicy(positive(n, "maxObjects"), maxElements, maxDistinctClasses, rejectRebinding, allow, deny);
    }

    public DerStreamPolicy withMaxElements(int n) {
        return new DerStreamPolicy(maxObjects, positive(n, "maxElements"), maxDistinctClasses, rejectRebinding, allow, deny);
    }

    public DerStreamPolicy withMaxDistinctClasses(int n) {
        return new DerStreamPolicy(maxObjects, maxElements, positive(n, "maxDistinctClasses"), rejectRebinding, allow, deny);
    }

    public DerStreamPolicy withRejectRebinding(boolean reject) {
        return new DerStreamPolicy(maxObjects, maxElements, maxDistinctClasses, reject, allow, deny);
    }

    /** @throws IllegalArgumentException if an entry is malformed */
    public DerStreamPolicy withAllowClasses(String commaSeparated) {
        return new DerStreamPolicy(maxObjects, maxElements, maxDistinctClasses, rejectRebinding,
                parseRules(commaSeparated, PROP_ALLOW_CLASSES), deny);
    }

    /** @throws IllegalArgumentException if an entry is malformed */
    public DerStreamPolicy withDenyClasses(String commaSeparated) {
        return new DerStreamPolicy(maxObjects, maxElements, maxDistinctClasses, rejectRebinding,
                allow, parseRules(commaSeparated, PROP_DENY_CLASSES));
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
                + ", maxDistinctClasses=" + maxDistinctClasses + ", rejectRebinding=" + rejectRebinding
                + ", allow=" + allow + ", deny=" + deny + "]";
    }

    // =========================================================================
    // Matching (used by the meter)
    // =========================================================================

    /** The first deny entry matching {@code name}, or {@code null}. */
    String deniedBy(String name) {
        return denyRules.firstMatch(name);
    }

    boolean hasAllowList() {
        return !allow.isEmpty();
    }

    boolean allowed(String name) {
        return allow.isEmpty() || allowRules.firstMatch(name) != null;
    }

    /** Compiled form of an allow or deny list. */
    private static final class Rules {
        private final List<String> entries;
        private final Set<String> exact = new LinkedHashSet<>();
        private final Set<String> packages = new LinkedHashSet<>();
        private final List<String> trees = new ArrayList<>();

        Rules(List<String> entries) {
            this.entries = entries;
            for (String e : entries) {
                if (e.endsWith(".**")) {
                    trees.add(e.substring(0, e.length() - 3));
                } else if (e.endsWith(".*")) {
                    packages.add(e.substring(0, e.length() - 2));
                } else {
                    exact.add(e);
                }
            }
        }

        String firstMatch(String name) {
            if (entries.isEmpty()) {
                return null;
            }
            if (exact.contains(name)) {
                return name;
            }
            int dot = name.lastIndexOf('.');
            if (dot > 0 && packages.contains(name.substring(0, dot))) {
                return name.substring(0, dot) + ".*";
            }
            for (String t : trees) {
                if (name.length() > t.length() && name.startsWith(t) && name.charAt(t.length()) == '.') {
                    return t + ".**";
                }
            }
            return null;
        }
    }

    // =========================================================================
    // Property parsing (pure, so it can be tested without touching system state)
    // =========================================================================

    private static String systemProperty(String name) {
        try {
            return System.getProperty(name);
        } catch (SecurityException e) {
            LOGGER.log(Level.WARNING, "Cannot read system property {0} ({1}); using the default",
                    new Object[] {name, e.toString()});
            return null;
        }
    }

    /**
     * Builds a policy from a property source, applying the validation rules in the class Javadoc.
     *
     * @throws IllegalStateException if an allow or deny entry is malformed
     */
    static DerStreamPolicy fromProperties(Function<String, String> source) {
        Objects.requireNonNull(source, "source");
        int objects = parseBudget(source.apply(PROP_MAX_OBJECTS), DEFAULT_MAX_OBJECTS, PROP_MAX_OBJECTS);
        int elements = parseBudget(source.apply(PROP_MAX_ELEMENTS), DEFAULT_MAX_ELEMENTS, PROP_MAX_ELEMENTS);
        int classes = parseBudget(source.apply(PROP_MAX_DISTINCT_CLASSES), DEFAULT_MAX_DISTINCT_CLASSES,
                PROP_MAX_DISTINCT_CLASSES);
        boolean rebinding = parseBoolean(source.apply(PROP_REJECT_REBINDING), true, PROP_REJECT_REBINDING);
        List<String> allow;
        List<String> deny;
        try {
            allow = parseRules(source.apply(PROP_ALLOW_CLASSES), PROP_ALLOW_CLASSES);
            deny = parseRules(source.apply(PROP_DENY_CLASSES), PROP_DENY_CLASSES);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage()
                    + " -- refusing to start with a policy weaker than the one written", e);
        }
        return new DerStreamPolicy(objects, elements, classes, rebinding, allow, deny);
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

    /**
     * Comma-separated allow/deny entries; blank entries are ignored (a trailing comma is not a mistake).
     *
     * @throws IllegalArgumentException if an entry is not an exact name, {@code pkg.*} or {@code pkg.**}
     */
    static List<String> parseRules(String raw, String propName) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String e = part.trim();
            if (e.isEmpty()) {
                continue;
            }
            if (!RULE.matcher(e).matches()) {
                throw new IllegalArgumentException("Malformed entry \"" + printable(e) + "\" in " + propName
                        + " (expected a binary class name, pkg.* or pkg.**)");
            }
            out.add(e);
        }
        return out;
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
