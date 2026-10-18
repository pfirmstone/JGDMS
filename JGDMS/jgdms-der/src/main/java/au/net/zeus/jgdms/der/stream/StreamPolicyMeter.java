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

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One admission pass's running tally against a {@link DerStreamPolicy}: the distinct class names the stream has named so
 * far, the objects and elements it will build, and the schema each class name is bound to. Every {@code note} method
 * throws a {@link DerException} with a distinct message the moment a limit is crossed, so the walk stops there and the
 * whole stream is rejected before anything is built.
 *
 * <p>Thread-confined to the admitting thread; one instance per stream, shared by the stream's main walker and every
 * region verifier, so a name or element in a {@code [8]}/{@code [16]} region counts toward the same totals.
 * Memory is bounded by the policy: the name set and each scope's schema map hold at most {@code maxDistinctClasses} entries.
 */
final class StreamPolicyMeter {

    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.stream.policy");

    private final DerStreamPolicy policy;
    private long objects;
    private long elements;
    private final Set<String> names = new LinkedHashSet<>();

    StreamPolicyMeter(DerStreamPolicy policy) {
        this.policy = policy;
    }

    /**
     * A place where each class name may be bound to ONE schema: the stream's own dedup table, or a single forwarded
     * {@code [8]}/{@code [16]} region. They are separate because a region legitimately carries its ORIGIN's version of a class
     * (a narrowed proxy is re-forwarded with the sender's own bytes), which may differ from the local version of the same class
     * in the stream around it; within one scope there is one origin and so one schema per class. A scope's map is dropped with
     * the scope, and holds at most {@code maxDistinctClasses} entries, so rebinding detection costs memory only for the
     * region being walked.
     */
    static final class Scope {
        private final Map<String, ByteBuffer> schemaOf = new HashMap<>();
        /** Chains already bound in this scope (by identity: the dedup table hands back one list per distinct chain). */
        private final Set<Object> bound = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /** Chains whose class names have already been checked (by identity), so a chain repeated across sites is checked once. */
    private final Set<Object> notedChains = Collections.newSetFromMap(new IdentityHashMap<>());

    Scope newScope() {
        return new Scope();
    }

    long objects() { return objects; }
    long elements() { return elements; }
    int distinctClasses() { return names.size(); }
    Set<String> classNames() { return Collections.unmodifiableSet(names); }

    /** Logs the distinct names of an admitted stream, for diagnosing a policy (a cheap no-op unless FINE is on). */
    void logAdmitted() {
        if (LOGGER.isLoggable(Level.FINE)) {
            StringBuilder b = new StringBuilder();
            int n = 0;
            for (String name : names) {
                if (n++ == 200) {
                    b.append(", ... (").append(names.size() - 200).append(" more)");
                    break;
                }
                b.append(n > 1 ? ", " : "").append(DerStreamPolicy.printable(name, 200));
            }
            LOGGER.log(Level.FINE, "DER stream admitted: {0} objects, {1} elements, {2} classes: {3}",
                    new Object[] {objects, elements, names.size(), b});
        }
    }

    /** One more object the stream will build or ask the loader for (a record site, an enum constant, a proxy, an array, a collection). */
    void object() throws DerException {
        if (++objects > policy.maxObjects()) {
            throw reject("DER stream policy: the stream builds more than maxObjects (" + policy.maxObjects()
                    + ") objects (" + DerStreamPolicy.PROP_MAX_OBJECTS + ")");
        }
    }

    /** {@code n} more collection, map or array elements. */
    void elements(long n) throws DerException {
        elements += n;
        if (elements > policy.maxElements()) {
            throw reject("DER stream policy: the stream holds more than maxElements (" + policy.maxElements()
                    + ") collection and array elements (" + DerStreamPolicy.PROP_MAX_ELEMENTS + ")");
        }
    }

    /**
     * A class name the stream makes the loader load or constructs. Array classes count as their element class and
     * primitive names are not classes. The checks run once per distinct name.
     */
    void className(String raw) throws DerException {
        note(normalise(raw, false));
    }

    /**
     * The value of a {@code java.lang.Class} field or element. Delivery maps a primitive name ({@code int}, {@code void}) to
     * the primitive class without asking the loader, so only here is a primitive name not a class to load.
     */
    void classValue(String raw) throws DerException {
        note(normalise(raw, true));
    }

    private void note(String name) throws DerException {
        if (name == null) {
            return;
        }
        if (name.length() > DerStreamPolicy.MAX_CLASS_NAME_LENGTH) {
            throw reject("DER stream policy: a class name of " + name.length() + " characters is longer than any class can have");
        }
        if (!names.add(name)) {
            return;
        }
        if (names.size() > policy.maxDistinctClasses()) {
            throw reject("DER stream policy: the stream names more than maxDistinctClasses ("
                    + policy.maxDistinctClasses() + ") distinct classes (" + DerStreamPolicy.PROP_MAX_DISTINCT_CLASSES + ")");
        }
    }

    /**
     * A chain this stream has just verified (once per distinct chain): the class of every record, the classes its fields'
     * wire types name, and, when rebinding is refused, that no class is now bound to a schema other than the one it was
     * first seen with. The digest of record {@code i > 0} is the {@code parentSchemaHash} record {@code i - 1} carries, which
     * the chain verification has already cross-checked, so no record is re-encoded or re-hashed here.
     */
    void chain(Scope scope, List<AtomicSerialSchemaRecord> leafFirst, byte[] leafDigest) throws DerException {
        if (notedChains.add(leafFirst)) {                   // names and ceilings: once per distinct chain
            for (AtomicSerialSchemaRecord rec : leafFirst) {
                className(rec.className());
                for (AtomicSerialFieldDef f : rec.fields()) {
                    wireType(f.wireType());
                }
            }
        }
        if (policy.rejectRebinding() && scope.bound.add(leafFirst)) {   // bindings: once per chain per scope
            byte[] digest = leafDigest;
            for (int i = 0; i < leafFirst.size(); i++) {
                AtomicSerialSchemaRecord rec = leafFirst.get(i);
                if (i > 0) {
                    digest = leafFirst.get(i - 1).parentSchemaHashOrNull();
                }
                String bound = normalise(rec.className(), false);
                if (digest == null || bound == null) {
                    continue;
                }
                ByteBuffer d = ByteBuffer.wrap(digest);
                ByteBuffer previous = scope.schemaOf.putIfAbsent(bound, d);
                if (previous != null && !previous.equals(d)) {
                    throw reject("DER stream policy: class '" + DerStreamPolicy.printable(rec.className())
                            + "' is bound to two different schemas in one stream (rebinding; "
                            + DerStreamPolicy.PROP_REJECT_REBINDING + ")");
                }
            }
        }
    }

    /**
     * The class names inside a field, array or collection wire-type token: {@code @AtomicSerial:<class>} and
     * {@code enum:<class>} at any depth ({@code array:}, {@code list:}, {@code map:{k}{v}} ...). A single linear scan
     * with no recursion: the token is attacker-controlled and bounded only by the size of the item that carries it.
     */
    void wireType(String token) throws DerException {
        if (token.indexOf(':') < 0) {
            return;
        }
        scan(token, "@AtomicSerial:");
        scan(token, "enum:");
    }

    /**
     * Notes the name after each {@code marker}: everything up to the closing {@code '}'} of the enclosing map part, or the end
     * of the token, exactly as the decoder's {@code substring} hands it to the loader (so an array-class name such as
     * {@code [Lp.C;} is the name, not just its first letters).
     */
    private void scan(String token, String marker) throws DerException {
        int from = 0;
        while (true) {
            int at = token.indexOf(marker, from);
            if (at < 0) {
                return;
            }
            int start = at + marker.length();
            boolean boundary = at == 0 || token.charAt(at - 1) == ':' || token.charAt(at - 1) == '{';
            int end = start;
            while (end < token.length() && token.charAt(end) != '}' && token.charAt(end) != '{') {
                end++;
            }
            if (boundary && end > start) {
                className(token.substring(start, end));
            }
            from = Math.max(end, start);
        }
    }

    /**
     * Reduces a name to the class the loader would be asked for: an array class {@code [[Lp.C;} to {@code p.C} (the loader loads
     * the element class), {@code null} for a primitive array ({@code [I}: no class of ours is loaded), for an empty name and,
     * when {@code primitivesAreNotClasses}, for a primitive name. A malformed array name is kept as it is, so a list judges
     * exactly the string the loader receives.
     */
    static String normalise(String raw, boolean primitivesAreNotClasses) {
        int i = 0;
        while (i < raw.length() && raw.charAt(i) == '[') {
            i++;
        }
        String n = raw;
        if (i > 0) {
            String rest = raw.substring(i);
            if (rest.length() == 1 && "ZBCSIJFD".indexOf(rest.charAt(0)) >= 0) {
                return null;                               // a primitive array such as [I
            }
            if (rest.length() > 2 && rest.charAt(0) == 'L' && rest.charAt(rest.length() - 1) == ';') {
                n = rest.substring(1, rest.length() - 1);
            }
        }
        if (n.isEmpty()) {
            return null;
        }
        if (primitivesAreNotClasses) {
            switch (n) {
                case "boolean": case "byte": case "char": case "short": case "int": case "long": case "float":
                case "double": case "void":
                    return null;
                default:
                    break;
            }
        }
        return n;
    }

    // Rate limit for the rejection log line: an unauthenticated peer (the authorization-context block is admitted before
    // authorization) must not be able to flood the log. Every rejection still throws its full message to the caller.
    private static final Object LOG_LOCK = new Object();
    private static long logWindowStart;
    private static int logsInWindow;
    private static long logsSuppressed;
    private static final long LOG_WINDOW_NANOS = 1_000_000_000L;
    private static final int LOGS_PER_WINDOW = 5;

    /** Test seam: start a fresh rate-limit window. */
    static void resetLogLimiterForTest() {
        synchronized (LOG_LOCK) {
            logWindowStart = 0;
            logsInWindow = 0;
            logsSuppressed = 0;
        }
    }

    private static void warn(String message) {
        String line;
        synchronized (LOG_LOCK) {
            long now = System.nanoTime();
            if (logWindowStart == 0 || now - logWindowStart > LOG_WINDOW_NANOS) {
                logWindowStart = now;
                logsInWindow = 0;
            }
            if (logsInWindow >= LOGS_PER_WINDOW) {
                logsSuppressed++;
                return;
            }
            logsInWindow++;
            line = logsSuppressed == 0 ? message
                    : message + " (" + logsSuppressed + " earlier policy rejections were not logged)";
            logsSuppressed = 0;
        }
        LOGGER.log(Level.WARNING, line);
    }

    private static DerException reject(String message) {
        warn(message);
        return new DerException(message);
    }
}
