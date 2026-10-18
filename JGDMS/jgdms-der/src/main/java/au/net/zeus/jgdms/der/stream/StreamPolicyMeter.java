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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    }

    Scope newScope() {
        return new Scope();
    }

    long objects() { return objects; }
    long elements() { return elements; }
    int distinctClasses() { return names.size(); }
    Set<String> classNames() { return java.util.Collections.unmodifiableSet(names); }

    /** Logs the distinct names of an admitted stream, for building an allow-list (a cheap no-op unless FINE is on). */
    void logAdmitted() {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "DER stream admitted: {0} objects, {1} elements, classes {2}",
                    new Object[] {objects, elements, DerStreamPolicy.printable(names.toString(), 4000)});
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
        String name = normalise(raw);
        if (name == null) {
            return;
        }
        if (name.length() > DerStreamPolicy.MAX_CLASS_NAME_LENGTH) {
            throw reject("DER stream policy: a class name of " + name.length() + " characters is longer than any class can have");
        }
        if (!names.add(name)) {
            return;
        }
        String denied = policy.deniedBy(name);
        if (denied != null) {
            throw reject("DER stream policy: class '" + DerStreamPolicy.printable(name) + "' is denied by "
                    + DerStreamPolicy.PROP_DENY_CLASSES + " (matched '" + denied + "')");
        }
        if (!policy.allowed(name)) {
            throw reject("DER stream policy: class '" + DerStreamPolicy.printable(name) + "' is not permitted by "
                    + DerStreamPolicy.PROP_ALLOW_CLASSES);
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
        byte[] digest = leafDigest;
        for (int i = 0; i < leafFirst.size(); i++) {
            AtomicSerialSchemaRecord rec = leafFirst.get(i);
            if (i > 0) {
                digest = leafFirst.get(i - 1).parentSchemaHashOrNull();
            }
            className(rec.className());
            String bound = normalise(rec.className());
            if (policy.rejectRebinding() && digest != null && bound != null) {
                ByteBuffer d = ByteBuffer.wrap(digest);
                ByteBuffer previous = scope.schemaOf.putIfAbsent(bound, d);
                if (previous != null && !previous.equals(d)) {
                    throw reject("DER stream policy: class '" + DerStreamPolicy.printable(rec.className())
                            + "' is bound to two different schemas in one stream (rebinding; "
                            + DerStreamPolicy.PROP_REJECT_REBINDING + ")");
                }
            }
            for (AtomicSerialFieldDef f : rec.fields()) {
                wireType(f.wireType());
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
            while (end < token.length() && isNameChar(token.charAt(end))) {
                end++;
            }
            if (boundary && end > start) {
                className(token.substring(start, end));
            }
            from = Math.max(end, start);
        }
    }

    private static boolean isNameChar(char c) {
        return c == '.' || c == '$' || c == '_' || Character.isLetterOrDigit(c);
    }

    /**
     * Reduces a class name to the class the loader would be asked for: an array class {@code [[Lp.C;} to {@code p.C}, and
     * {@code null} for a primitive name, a primitive array, or an empty name.
     */
    static String normalise(String raw) {
        int i = 0;
        while (i < raw.length() && raw.charAt(i) == '[') {
            i++;
        }
        String n = raw;
        if (i > 0) {
            if (i + 1 < raw.length() && raw.charAt(i) == 'L' && raw.charAt(raw.length() - 1) == ';') {
                n = raw.substring(i + 1, raw.length() - 1);
            } else {
                return null;                               // a primitive array such as [I
            }
        }
        if (n.isEmpty()) {
            return null;
        }
        switch (n) {
            case "boolean": case "byte": case "char": case "short": case "int": case "long": case "float":
            case "double": case "void":
                return null;
            default:
                return n;
        }
    }

    private static DerException reject(String message) {
        LOGGER.log(Level.WARNING, message);
        return new DerException(message);
    }
}
