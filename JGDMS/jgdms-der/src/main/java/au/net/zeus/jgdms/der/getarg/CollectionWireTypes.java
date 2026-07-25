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

package au.net.zeus.jgdms.der.getarg;

import au.net.zeus.jgdms.der.DerException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central authority for the STD-006 §3.8 collection-ordering rule: the
 * declared-type → <em>discipline</em> discriminator, the collection wire-type
 * <em>token grammar</em>, and the X.690 §11.6 <em>octet-sort</em> comparator.
 *
 * <h2>The two disciplines (STD-006 §3.8 / memo §1)</h2>
 * <p>Whether an encoder <b>preserves</b> or <b>canonicalises</b> a collection
 * field's element order is decided solely by whether the <b>declared type
 * guarantees a deterministic iteration order</b> — never by sniffing the runtime
 * instance, and <b>never</b> from {@code hashCode}/{@code identityHashCode} or
 * hash-bucket order.
 * <ul>
 *   <li><b>PRESERVE</b> — the type's iterator yields a reproducible order that is
 *       part of the value: {@code List}/{@code Deque}/array (positional),
 *       {@code LinkedHashSet}/{@code LinkedHashMap} (non-concurrent insertion),
 *       {@code SequencedSet}/{@code SequencedMap} (the JDK 21+ interface way to
 *       declare encounter order — insertion order is part of the value),
 *       {@code SortedSet}/{@code TreeSet}/{@code SortedMap}/{@code TreeMap} and the
 *       concurrent-<em>sorted</em> {@code ConcurrentSkipListSet}/{@code Map}
 *       (comparator/element-derived), {@code EnumSet}/{@code EnumMap} (ordinal),
 *       and {@code CopyOnWriteArrayList} (a {@code List}).</li>
 *   <li><b>CANONICALISE</b> — the iterator's order is hash-derived, race-dependent,
 *       or explicitly disclaimed: {@code HashSet}/{@code HashMap}/
 *       {@code ConcurrentHashMap}/{@code Properties}, {@code CopyOnWriteArraySet},
 *       the concurrent insertion/FIFO queues &amp; deques, and
 *       {@code PriorityQueue}/{@code PriorityBlockingQueue}. The elements (or
 *       {@code {key,value}} entries) are octet-sorted per §11.6.</li>
 * </ul>
 *
 * <h2>Token grammar (STD-006 §7.6, memo §6.3 — build-later realised here)</h2>
 * <p>The discipline is derived from the declared collection {@link Class} at
 * schema-generation time and baked into the schema wire-type token, so it is
 * digest-covered and the decoder agrees without re-deriving it. The grammar
 * mirrors the existing {@code enum:} / {@code array:} style:
 * <pre>
 *   set:&lt;elemWT&gt;              -- CANONICALISE set  -- ASN.1 SET OF, outer tag 0x31; strictly-ascending §11.6 on decode
 *   bag:&lt;elemWT&gt;              -- CANONICALISE multiset (priority/concurrent queue) -- SET OF 0x31; non-decreasing §11.6, dups kept
 *   orderedset:&lt;elemWT&gt;       -- PRESERVE set   -- SEQUENCE OF, outer tag 0x30; iteration order kept, no order-check
 *   list:&lt;elemWT&gt;             -- PRESERVE list/deque -- SEQUENCE OF 0x30; order kept, dups allowed
 *   map:{&lt;keyWT&gt;}{&lt;valWT&gt;}     -- CANONICALISE map -- SET OF SEQUENCE{key,value}, outer 0x31 / inner entry 0x30; strictly-ascending by KEY
 *   orderedmap:{&lt;keyWT&gt;}{&lt;valWT&gt;} -- PRESERVE map -- SEQUENCE OF SEQUENCE{key,value}, outer 0x30 / inner 0x30; entry order kept
 * </pre>
 * <b>Option A tag rule (STD-006 §3.8):</b> the CANONICALISE disciplines carry the
 * real ASN.1 {@code SET OF} tag {@code 0x31}; the PRESERVE disciplines carry
 * {@code SEQUENCE OF} {@code 0x30}. A map's inner per-entry {@code {key,value}}
 * SEQUENCE is always {@code 0x30}. The decoder rejects the wrong outer tag for the
 * field's discipline. The map key/value sub-tokens are brace-delimited so a
 * sub-token that itself contains {@code ':'} (e.g. {@code enum:...}, a nested
 * {@code set:...} or {@code map:...}, or a bare {@code @AtomicSerial}) parses
 * unambiguously and recursion nests correctly.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class CollectionWireTypes {

    private CollectionWireTypes() {
        throw new AssertionError("no instances");
    }

    // Token prefixes.
    static final String SET          = "set:";
    static final String BAG          = "bag:";
    static final String ORDERED_SET  = "orderedset:";
    static final String LIST         = "list:";
    static final String MAP          = "map:";
    static final String ORDERED_MAP  = "orderedmap:";

    /** Whether a wire-type token is any of the collection tokens. */
    public static boolean isCollection(String wireType) {
        return wireType.startsWith(SET) || wireType.startsWith(BAG)
                || wireType.startsWith(ORDERED_SET)
                || wireType.startsWith(LIST) || wireType.startsWith(MAP)
                || wireType.startsWith(ORDERED_MAP);
    }

    /** Whether the token is one of the two map tokens. */
    public static boolean isMap(String wireType) {
        return wireType.startsWith(MAP) || wireType.startsWith(ORDERED_MAP);
    }

    /**
     * Whether the token's discipline is CANONICALISE (octet-sort). A collection
     * token is either {@code set:} / {@code map:} (canonicalise) or
     * {@code orderedset:} / {@code list:} / {@code orderedmap:} (preserve).
     *
     * @throws IllegalArgumentException if {@code wireType} is not a collection token
     */
    public static boolean isCanonicalise(String wireType) {
        if (wireType.startsWith(ORDERED_SET) || wireType.startsWith(LIST)
                || wireType.startsWith(ORDERED_MAP)) {
            return false;
        }
        if (wireType.startsWith(SET) || wireType.startsWith(BAG) || wireType.startsWith(MAP)) {
            return true;
        }
        throw new IllegalArgumentException("not a collection wire type: " + wireType);
    }

    /** Whether the token is the {@code bag:} canonicalise-multiset token. A {@code bag:} field
     *  is octet-sorted but may hold duplicates, so its decode order-check is NON-DECREASING
     *  ({@code <=}) rather than the strictly-ascending ({@code <}) check a {@code set:} uses. */
    public static boolean isMultiset(String wireType) {
        return wireType.startsWith(BAG);
    }

    /** Whether a decoded {@code set:}/{@code orderedset:} field must reject duplicate
     *  element encodings (a Set cannot hold post-canonical duplicates; §2). A
     *  {@code list:} field (positional) or {@code bag:} field (a canonicalised multiset,
     *  e.g. a priority/concurrent queue) allows them. */
    public static boolean isSetKind(String wireType) {
        return wireType.startsWith(SET) || wireType.startsWith(ORDERED_SET);
    }

    // =========================================================================
    // Token builders (used by SchemaGenerator, which lives in another package)
    // =========================================================================

    /**
     * Builds a set/collection token for the given declared collection class and
     * element wire-type, choosing the discipline from {@link #disciplineFor}.
     *
     * @param declaredClass the declared collection class (e.g. {@code HashSet.class})
     * @param elemWireType  the element wire-type (e.g. {@code "int"}, {@code "@AtomicSerial"})
     * @return {@code "set:"+elemWireType}, {@code "orderedset:"+elemWireType},
     *         or {@code "list:"+elemWireType}
     */
    public static String setToken(Class<?> declaredClass, String elemWireType) {
        Discipline d = disciplineFor(declaredClass);
        return switch (d) {
            case CANONICALISE           -> SET + elemWireType;
            case CANONICALISE_MULTISET  -> BAG + elemWireType;
            case PRESERVE_ORDERED       -> ORDERED_SET + elemWireType;
            case PRESERVE_LIST          -> LIST + elemWireType;
        };
    }

    /**
     * Builds a map token for the given declared map class and key/value wire-types.
     *
     * @param declaredClass the declared map class (e.g. {@code HashMap.class})
     * @param keyWireType   the key wire-type
     * @param valWireType   the value wire-type
     * @return {@code "map:{key}{val}"} (canonicalise) or {@code "orderedmap:{key}{val}"} (preserve)
     */
    public static String mapToken(Class<?> declaredClass, String keyWireType, String valWireType) {
        Discipline d = disciplineFor(declaredClass);
        String prefix = (d == Discipline.CANONICALISE) ? MAP : ORDERED_MAP;
        return prefix + "{" + keyWireType + "}{" + valWireType + "}";
    }

    // -------------------------------------------------------------------------
    // Discipline-keyed token constructors (used by the Any form, which knows a
    // collection's discipline from its wire tag, not from a declared Class).
    // -------------------------------------------------------------------------

    /** Builds a {@code set:}{@code <elemWT>} token (canonicalise set, strictly-ascending decode). */
    public static String setToken(String elemWireType) {
        return SET + elemWireType;
    }

    /** Builds a {@code list:}{@code <elemWT>} token (preserve list, no order check, dups allowed). */
    public static String listToken(String elemWireType) {
        return LIST + elemWireType;
    }

    /** Builds a {@code map:}{@code {keyWT}{valWT}} token (canonicalise map, key-ascending decode). */
    public static String mapToken(String keyWireType, String valWireType) {
        return MAP + "{" + keyWireType + "}{" + valWireType + "}";
    }

    /** Builds an {@code orderedmap:}{@code {keyWT}{valWT}} token (preserve map, no order check). */
    public static String orderedMapToken(String keyWireType, String valWireType) {
        return ORDERED_MAP + "{" + keyWireType + "}{" + valWireType + "}";
    }

    /** The element wire-type of a {@code set:}/{@code bag:}/{@code orderedset:}/{@code list:} token. */
    public static String elementWireType(String wireType) {
        if (wireType.startsWith(SET))         return wireType.substring(SET.length());
        if (wireType.startsWith(BAG))         return wireType.substring(BAG.length());
        if (wireType.startsWith(ORDERED_SET)) return wireType.substring(ORDERED_SET.length());
        if (wireType.startsWith(LIST))        return wireType.substring(LIST.length());
        throw new IllegalArgumentException("not a set/list wire type: " + wireType);
    }

    /** The {@code {keyWT, valWT}} pair of a {@code map:}/{@code orderedmap:} token. */
    public static String[] mapKeyValueWireTypes(String wireType) throws DerException {
        String body;
        if (wireType.startsWith(MAP)) {
            body = wireType.substring(MAP.length());
        } else if (wireType.startsWith(ORDERED_MAP)) {
            body = wireType.substring(ORDERED_MAP.length());
        } else {
            throw new IllegalArgumentException("not a map wire type: " + wireType);
        }
        // body := "{key}{val}" with brace matching (sub-tokens may themselves contain braces).
        String key = readBraced(body, 0, wireType);
        int next = 1 + key.length() + 1; // '{' + key + '}'
        String val = readBraced(body, next, wireType);
        return new String[]{ key, val };
    }

    /**
     * Reads a single brace-delimited sub-token starting at {@code pos} (which must
     * index the opening '{') and returns its inner content, honouring nested braces.
     */
    private static String readBraced(String body, int pos, String fullToken) throws DerException {
        if (pos >= body.length() || body.charAt(pos) != '{') {
            throw new DerException("malformed map wire type (expected '{' at " + pos
                    + "): " + fullToken);
        }
        int depth = 0;
        for (int i = pos; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(pos + 1, i);
                }
            }
        }
        throw new DerException("malformed map wire type (unbalanced braces): " + fullToken);
    }

    // =========================================================================
    // The discriminator (STD-006 §3.8 / memo §1, §6.1) — declared class → discipline
    // =========================================================================

    /** The encoder disciplines the token grammar distinguishes. */
    public enum Discipline {
        /** Octet-sort (X.690 §11.6): non-deterministic-order Set/Map; duplicates rejected on decode. */
        CANONICALISE,
        /** Octet-sort (X.690 §11.6) as a MULTISET: a priority/concurrent queue whose iterator
         *  disclaims order; duplicates are legitimate and retained (reconstructed as a List). */
        CANONICALISE_MULTISET,
        /** Preserve iteration order; reconstruct as a Set/Map (dups rejected for Set decode). */
        PRESERVE_ORDERED,
        /** Preserve iteration order; reconstruct as a List/Deque (dups allowed). */
        PRESERVE_LIST
    }

    /**
     * Maps a declared collection {@link Class} to its ordering discipline, per the
     * STD-006 §3.8 <b>deterministic-iteration-order</b> discriminator (memo §6.1
     * decision matrix). The class named must be the declared field type the
     * developer chose — the discriminator is a pure function of that type, so it is
     * reproducible and digest-stable.
     *
     * <p><b>Never</b> consults {@code hashCode}. The determination is by interface /
     * concrete-class membership only:
     * <ul>
     *   <li>{@code SortedSet}/{@code SortedMap} (incl. {@code NavigableSet}/{@code Map},
     *       {@code TreeSet}/{@code TreeMap}, {@code ConcurrentSkipListSet}/{@code Map}) —
     *       element-derived order → PRESERVE.</li>
     *   <li>{@code LinkedHashSet}/{@code LinkedHashMap}, {@code EnumSet}/{@code EnumMap} —
     *       deterministic order → PRESERVE.</li>
     *   <li>{@code List}/{@code Deque} — positional → PRESERVE (as a list).</li>
     *   <li>everything else that is a {@code Set}/{@code Map}/{@code Queue}/{@code Collection}
     *       (the hash types, {@code CopyOnWriteArraySet}, the concurrent queues/deques, the
     *       priority queues) — non-deterministic → CANONICALISE.</li>
     * </ul>
     *
     * @param declaredClass the declared collection class
     * @return the discipline
     * @throws IllegalArgumentException if {@code declaredClass} is not a
     *         {@code Collection} or {@code Map} type
     */
    public static Discipline disciplineFor(Class<?> declaredClass) {
        if (declaredClass == null) {
            throw new IllegalArgumentException("declaredClass is null");
        }
        // ---- Maps -----------------------------------------------------------
        if (Map.class.isAssignableFrom(declaredClass)) {
            // Element-derived (sorted) order is deterministic even when concurrent.
            if (SortedMap.class.isAssignableFrom(declaredClass)
                    || NavigableMap.class.isAssignableFrom(declaredClass)) {
                return Discipline.PRESERVE_ORDERED;
            }
            // A declared SequencedMap (JDK 21+) is the INTERFACE way to declare that encounter
            // (insertion) order is part of the value -> PRESERVE_ORDERED. (LinkedHashMap already
            // matches by name below; this additionally captures a field declared as the bare
            // SequencedMap interface, or any future SequencedMap impl. ConcurrentHashMap is NOT a
            // SequencedMap, so it still canonicalises.)
            if (SequencedMap.class.isAssignableFrom(declaredClass)) {
                return Discipline.PRESERVE_ORDERED;
            }
            // ConcurrentHashMap (and ConcurrentMap impls that are NOT the sorted skip-list)
            // are hash-order → canonicalise. The sorted ConcurrentNavigableMap is caught above.
            if (ConcurrentMap.class.isAssignableFrom(declaredClass)) {
                return Discipline.CANONICALISE;
            }
            // LinkedHashMap and EnumMap have a deterministic (insertion / ordinal) order.
            String n = declaredClass.getName();
            if (n.equals("java.util.LinkedHashMap") || n.equals("java.util.EnumMap")) {
                return Discipline.PRESERVE_ORDERED;
            }
            // HashMap, Hashtable, Properties, WeakHashMap, IdentityHashMap, and the bare
            // Map interface → non-deterministic → canonicalise.
            return Discipline.CANONICALISE;
        }

        // ---- Collections (Set / List / Queue / Deque) -----------------------
        if (!java.util.Collection.class.isAssignableFrom(declaredClass)) {
            throw new IllegalArgumentException(
                    "not a Collection or Map type: " + declaredClass.getName());
        }

        // List / Deque: positional / non-concurrent head-tail order is the value → preserve as list.
        // (ArrayDeque, LinkedList, ArrayList, Vector, CopyOnWriteArrayList.)
        // The CONCURRENT queues/deques are handled below (they are Queue but not List, and
        // their FIFO order is race-dependent → canonicalise). A concurrent Deque
        // (ConcurrentLinkedDeque, LinkedBlockingDeque) is a Deque yet canonicalises, so the
        // List/Deque preserve rule is scoped to the non-concurrent implementations by name.
        if (List.class.isAssignableFrom(declaredClass)) {
            return Discipline.PRESERVE_LIST;
        }

        // Sets.
        if (java.util.Set.class.isAssignableFrom(declaredClass)) {
            // SortedSet / NavigableSet (TreeSet, ConcurrentSkipListSet): element-derived → preserve.
            if (SortedSet.class.isAssignableFrom(declaredClass)
                    || NavigableSet.class.isAssignableFrom(declaredClass)) {
                return Discipline.PRESERVE_ORDERED;
            }
            // A declared SequencedSet (JDK 21+) is the INTERFACE way to declare that encounter
            // (insertion) order is part of the value -> PRESERVE_ORDERED. (LinkedHashSet already
            // matches by name below; this additionally captures a field declared as the bare
            // SequencedSet interface, or any future SequencedSet impl. HashSet/CopyOnWriteArraySet
            // are NOT SequencedSets, so they still canonicalise.)
            if (SequencedSet.class.isAssignableFrom(declaredClass)) {
                return Discipline.PRESERVE_ORDERED;
            }
            String n = declaredClass.getName();
            // LinkedHashSet (non-concurrent insertion) and EnumSet (ordinal): deterministic → preserve.
            if (n.equals("java.util.LinkedHashSet")
                    || n.startsWith("java.util.RegularEnumSet")   // EnumSet is abstract; concrete impls
                    || n.startsWith("java.util.JumboEnumSet")
                    || n.equals("java.util.EnumSet")) {
                return Discipline.PRESERVE_ORDERED;
            }
            // HashSet, CopyOnWriteArraySet, ConcurrentHashMap.KeySetView, the bare Set
            // interface → non-deterministic → canonicalise.
            return Discipline.CANONICALISE;
        }

        // Non-List, non-Set collections: Queue / Deque families.
        // Only the non-concurrent Deque (ArrayDeque) preserves; every concurrent
        // queue/deque and the priority queues canonicalise AS A MULTISET (they may
        // legitimately hold duplicates -- a priority queue of equal-priority items, a
        // FIFO queue of repeated values -- so duplicates are RETAINED, unlike a Set).
        String n = declaredClass.getName();
        if (n.equals("java.util.ArrayDeque")) {
            return Discipline.PRESERVE_LIST;
        }
        // PriorityQueue/PriorityBlockingQueue (iterator disclaims order), the concurrent
        // linked/blocking queues & deques, DelayQueue, LinkedTransferQueue, SynchronousQueue,
        // and the bare Queue/Deque/Collection interfaces → canonicalise as a multiset.
        if (Queue.class.isAssignableFrom(declaredClass)
                || java.util.Collection.class.isAssignableFrom(declaredClass)) {
            return Discipline.CANONICALISE_MULTISET;
        }
        // Unreachable (Collection was asserted above), but fail-secure.
        throw new IllegalArgumentException(
                "unclassifiable collection type: " + declaredClass.getName());
    }

    // =========================================================================
    // X.690 §11.6 octet-sort (SET OF canonical order)
    // =========================================================================

    /**
     * The X.690 §11.6 "SET OF" comparator over complete DER element encodings.
     *
     * <p>Two element (or map-entry) encodings are compared as octet strings in
     * ascending order, where — <b>for the comparison only</b> — the shorter
     * encoding is conceptually padded at its trailing end with {@code 0x00}
     * octets; the padding is never emitted. Equivalently: compare byte-by-byte as
     * unsigned; if one is a proper prefix of the other, the shorter sorts first.
     *
     * <p>This is a total order over <em>distinct</em> encodings. For a Set the
     * encodings are guaranteed distinct (distinct values → distinct canonical DER);
     * for a multiset (a priority-queue / concurrent-queue field) equal encodings
     * compare equal and their relative order is irrelevant (they are identical
     * bytes), so the sort remains deterministic.
     */
    public static final Comparator<byte[]> OCTET_SORT = CollectionWireTypes::compareOctets;

    /**
     * X.690 §11.6 octet-string comparison with trailing-{@code 0x00} padding of the
     * shorter operand (see {@link #OCTET_SORT}).
     *
     * @return negative, zero, or positive as {@code a} sorts before, equal to, or
     *         after {@code b}
     */
    public static int compareOctets(byte[] a, byte[] b) {
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if (ai != bi) {
                return ai - bi;
            }
        }
        // Common prefix equal. The shorter, padded with 0x00 at the tail, sorts first
        // iff the longer has any NON-zero octet beyond the prefix; a trailing run of
        // 0x00 in the longer would tie the padded comparison, but DER canonical TLVs
        // never differ only by trailing zero padding, so length order is the correct
        // and deterministic tie-break (and equal-length-equal-bytes => 0).
        if (a.length == b.length) {
            return 0;
        }
        // Padding the shorter with 0x00: any remaining octet of the longer that is
        // > 0x00 makes the longer greater; a remaining 0x00 keeps them tied so far.
        byte[] longer = a.length > b.length ? a : b;
        for (int i = min; i < longer.length; i++) {
            if ((longer[i] & 0xFF) != 0x00) {
                // longer > shorter+padding
                return a.length > b.length ? 1 : -1;
            }
        }
        // Longer is shorter followed only by 0x00 octets: padded-equal. Order by
        // length so the sort is still a strict, deterministic total order.
        return Integer.compare(a.length, b.length);
    }

    /**
     * Sorts a list of complete DER element (or entry) encodings in place into the
     * X.690 §11.6 canonical order. Returns the same list for convenience.
     */
    public static List<byte[]> octetSort(List<byte[]> encodings) {
        encodings.sort(OCTET_SORT);
        return encodings;
    }
}
