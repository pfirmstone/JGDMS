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

package au.net.zeus.jgdms.showcase.demo;

import org.apache.river.api.io.AtomicSerial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Demonstration: "Two different collection classes, one value, one encoding."
 *
 * <p>The canonical wire format (STD-006 §3.8) serialises a collection field's
 * <em>value</em>, mirroring the collection's {@code .equals} contract — never the
 * concrete implementation class that happens to hold the value in memory. A field
 * declared as {@code Set} does not guarantee any iteration order, so the encoder
 * octet-sorts the element encodings (X.690 §11.6): a {@code HashSet}, a
 * {@code TreeSet}, and a {@code LinkedHashSet} holding the same members all encode
 * to byte-identical DER. Stock Java serialization ({@code java.io.ObjectOutputStream})
 * records the concrete class and its internal layout, so the same three values come
 * out as three different byte strings.
 *
 * <p>Honesty note on the contrast: the JOSS side below uses the stock JDK
 * {@code java.io.ObjectOutputStream} on the collections themselves, not this
 * project's compatibility stream ({@code AtomicMarshalOutputStream}). That stream
 * already substitutes any {@code Set}/{@code Map} with a neutral serial form
 * ({@code SetSerializer}/{@code MapSerializer}) that drops the implementation
 * class — but it still writes the runtime instance's ITERATION order, which for a
 * hash container is bucket order, not a canonical function of the value. Only the
 * canonical DER path makes equal values byte-identical by construction.
 *
 * <p>The discipline is a pure function of the DECLARED field type, decided at
 * schema-generation time and baked into the schema token — never sniffed from the
 * runtime instance:
 * <ul>
 *   <li>{@code Set}/{@code Map} (no order guarantee) — CANONICALISE: octet-sort
 *       elements / entries-by-key ({@code set:}/{@code map:} tokens, real ASN.1
 *       {@code SET OF} tag 0x31).</li>
 *   <li>{@code List} (positional; order IS the value) — PRESERVE iteration order
 *       ({@code list:} token, {@code SEQUENCE OF} 0x30). Two implementations holding
 *       the same elements in the same order encode identically.</li>
 *   <li>{@code LinkedHashSet} declared as itself (deterministic insertion order) —
 *       PRESERVE. Here byte-equality is deliberately STRICTER than {@code .equals}:
 *       the documented §3a tension, shown honestly at the end.</li>
 * </ul>
 */
public final class CollectionEqualityDemo {

    // =========================================================================
    // The value types. Each holder declares its collection field by the
    // INTERFACE (or, for the tension exhibit, deliberately by the concrete
    // class), and stores exactly the collection instance it was given -- no
    // defensive copy into a fixed implementation, because the whole point is
    // that the caller's choice of implementation must vanish on the wire.
    // =========================================================================

    /** A set-valued record: field declared {@code Set} → CANONICALISE (octet-sort). */
    @AtomicSerial
    public static final class StationRoster {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("stationNames", Set.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, StationRoster o) throws IOException {
            arg.put("stationNames", o.stationNames);
            arg.writeArgs();
        }
        private final Set<String> stationNames;
        public StationRoster(Set<String> stationNames) { this.stationNames = stationNames; }
        @SuppressWarnings("unchecked")
        public StationRoster(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.stationNames = (Set<String>) arg.get("stationNames", null);
        }
        @Override public boolean equals(Object o) {
            return o instanceof StationRoster that && Objects.equals(stationNames, that.stationNames);
        }
        @Override public int hashCode() { return Objects.hashCode(stationNames); }
    }

    /** A list-valued record: field declared {@code List} → PRESERVE (positional). */
    @AtomicSerial
    public static final class MaintenanceLog {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("entries", List.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, MaintenanceLog o) throws IOException {
            arg.put("entries", o.entries);
            arg.writeArgs();
        }
        private final List<String> entries;
        public MaintenanceLog(List<String> entries) { this.entries = entries; }
        @SuppressWarnings("unchecked")
        public MaintenanceLog(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.entries = (List<String>) arg.get("entries", null);
        }
        @Override public boolean equals(Object o) {
            return o instanceof MaintenanceLog that && Objects.equals(entries, that.entries);
        }
        @Override public int hashCode() { return Objects.hashCode(entries); }
    }

    /** A map-valued record: field declared {@code Map} → CANONICALISE (octet-sort by key). */
    @AtomicSerial
    public static final class SensorSerialNumbers {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("serialByStation", Map.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, SensorSerialNumbers o) throws IOException {
            arg.put("serialByStation", o.serialByStation);
            arg.writeArgs();
        }
        private final Map<String, Long> serialByStation;
        public SensorSerialNumbers(Map<String, Long> serialByStation) { this.serialByStation = serialByStation; }
        @SuppressWarnings("unchecked")
        public SensorSerialNumbers(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.serialByStation = (Map<String, Long>) arg.get("serialByStation", null);
        }
        @Override public boolean equals(Object o) {
            return o instanceof SensorSerialNumbers that && Objects.equals(serialByStation, that.serialByStation);
        }
        @Override public int hashCode() { return Objects.hashCode(serialByStation); }
    }

    /**
     * The tension exhibit: the field is deliberately declared as the CONCRETE
     * {@code LinkedHashSet} — a type that DOES guarantee a deterministic
     * (insertion) iteration order — so the encoder PRESERVES that order. Two
     * routes that are {@code .equals} (Set equality ignores order) but were
     * built in different insertion orders encode to DIFFERENT bytes: serialized
     * byte-equality is stricter than {@code .equals}. Documented, not hidden.
     */
    @AtomicSerial
    public static final class PatrolRoute {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("waypoints", LinkedHashSet.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, PatrolRoute o) throws IOException {
            arg.put("waypoints", o.waypoints);
            arg.writeArgs();
        }
        private final LinkedHashSet<String> waypoints;
        public PatrolRoute(LinkedHashSet<String> waypoints) { this.waypoints = waypoints; }
        @SuppressWarnings("unchecked")
        public PatrolRoute(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            Object v = arg.get("waypoints", null);
            this.waypoints = (v == null) ? null : new LinkedHashSet<>((Set<String>) v);
        }
        @Override public boolean equals(Object o) {
            return o instanceof PatrolRoute that && Objects.equals(waypoints, that.waypoints);
        }
        @Override public int hashCode() { return Objects.hashCode(waypoints); }
    }

    // =========================================================================
    // The demonstration
    // =========================================================================

    private static boolean allHeld = true;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================================================");
        System.out.println(" Two different collection classes, one value, one encoding");
        System.out.println("========================================================================");
        System.out.println(" Source: https://github.com/pfirmstone/JGDMS/blob/trunk/JGDMS/examples/wire-protocol-showcase/src/main/java/au/net/zeus/jgdms/showcase/demo/CollectionEqualityDemo.java");
        System.out.println();
        System.out.println("The canonical format serialises a collection's VALUE (what .equals");
        System.out.println("compares), never the implementation class holding it. Stock Java");
        System.out.println("serialization (java.io.ObjectOutputStream) records the implementation");
        System.out.println("class too, so the same value comes out as different bytes from");
        System.out.println("different implementations.");
        System.out.println();

        // ---- Category 1: Set (declared Set -> canonicalise, octet-sorted) ----
        List<String> members = Arrays.asList("Station-North", "Station-East", "Station-West");
        Set<String> hashSet   = new HashSet<>(members);
        Set<String> treeSet   = new TreeSet<>(members);
        Set<String> linkedSet = new LinkedHashSet<>(members);
        compareThree("Set field (declared java.util.Set -> canonical, octet-sorted)",
                "HashSet", new StationRoster(hashSet), hashSet,
                "TreeSet", new StationRoster(treeSet), treeSet,
                "LinkedHashSet", new StationRoster(linkedSet), linkedSet);

        // ---- Category 2: List (declared List -> preserve positional order) ----
        List<String> log = Arrays.asList("inspected", "cleaned optics", "re-levelled");
        List<String> arrayList  = new ArrayList<>(log);
        List<String> linkedList = new LinkedList<>(log);
        compareTwo("List field (declared java.util.List -> order preserved; same order in both)",
                "ArrayList", new MaintenanceLog(arrayList), arrayList,
                "LinkedList", new MaintenanceLog(linkedList), linkedList);

        // ---- Category 3: Map (declared Map -> canonicalise, sorted by encoded key) ----
        Map<String, Long> serials = new HashMap<>();
        serials.put("Station-North", 100_042L);
        serials.put("Station-East",  100_043L);
        serials.put("Station-West",  100_044L);
        Map<String, Long> hashMap   = new HashMap<>(serials);
        Map<String, Long> treeMap   = new TreeMap<>(serials);
        Map<String, Long> linkedMap = new LinkedHashMap<>(serials);
        compareThree("Map field (declared java.util.Map -> canonical, entries sorted by encoded key)",
                "HashMap", new SensorSerialNumbers(hashMap), hashMap,
                "TreeMap", new SensorSerialNumbers(treeMap), treeMap,
                "LinkedHashMap", new SensorSerialNumbers(linkedMap), linkedMap);

        // ---- The honest exception: a declared LinkedHashSet preserves insertion order ----
        LinkedHashSet<String> northFirst = new LinkedHashSet<>(
                Arrays.asList("Station-North", "Station-East", "Station-West"));
        LinkedHashSet<String> westFirst = new LinkedHashSet<>(
                Arrays.asList("Station-West", "Station-East", "Station-North"));
        PatrolRoute routeA = new PatrolRoute(northFirst);
        PatrolRoute routeB = new PatrolRoute(westFirst);
        byte[] derA = ShowcaseSupport.canonicalBytes(routeA);
        byte[] derB = ShowcaseSupport.canonicalBytes(routeB);
        System.out.println("The honest exception -- a field declared as LinkedHashSet itself:");
        System.out.println("  LinkedHashSet GUARANTEES a deterministic (insertion) iteration order,");
        System.out.println("  so the canonical format PRESERVES that order instead of sorting it.");
        System.out.println("  Two routes with the same waypoints inserted in different orders:");
        System.out.println("    equal by value (.equals)?         " + routeA.equals(routeB));
        System.out.println("    canonical bytes identical?        " + Arrays.equals(derA, derB));
        System.out.println("    north-first: " + derA.length + " bytes, SHA-256 "
                + ShowcaseSupport.sha256Hex(derA).substring(0, 16) + "...");
        System.out.println("    west-first : " + derB.length + " bytes, SHA-256 "
                + ShowcaseSupport.sha256Hex(derB).substring(0, 16) + "...");
        System.out.println("  => Intended, and documented in the wire-format spec: for insertion-");
        System.out.println("     ordered types the insertion order is part of what you declared, so");
        System.out.println("     byte-equality is deliberately STRICTER than .equals. Declare the");
        System.out.println("     field as Set if only membership is the value.");
        check(routeA.equals(routeB), "PatrolRoute .equals precondition");
        check(!Arrays.equals(derA, derB), "LinkedHashSet insertion order preserved (bytes differ)");
        System.out.println();

        printCrossLanguageTable();

        System.out.println(allHeld
                ? "ALL CLAIMS HELD: the wire encodes collection VALUES; the implementation"
                + System.lineSeparator()
                + "class never reaches the wire (and insertion order is kept exactly where"
                + System.lineSeparator()
                + "the declared type makes it part of the value)."
                : "A CLAIM DID NOT HOLD -- see above.");
        if (!allHeld) System.exit(1);
    }

    // -------------------------------------------------------------------------

    private static void compareThree(String title,
                                     String n1, Object v1, Object coll1,
                                     String n2, Object v2, Object coll2,
                                     String n3, Object v3, Object coll3) throws Exception {
        System.out.println(title + ":");
        boolean eq = v1.equals(v2) && v2.equals(v3);
        System.out.println("  the three records are equal by value (.equals)? " + eq);
        check(eq, title + " .equals precondition");

        byte[] d1 = ShowcaseSupport.canonicalBytes(v1);
        byte[] d2 = ShowcaseSupport.canonicalBytes(v2);
        byte[] d3 = ShowcaseSupport.canonicalBytes(v3);
        printRow("canonical ", n1, d1);
        printRow("canonical ", n2, d2);
        printRow("canonical ", n3, d3);
        boolean derSame = Arrays.equals(d1, d2) && Arrays.equals(d2, d3);
        System.out.println("    -> canonical bytes identical across all three? " + derSame);
        check(derSame, title + " canonical byte-identity");

        byte[] j1 = stockJavaSerialization(coll1);
        byte[] j2 = stockJavaSerialization(coll2);
        byte[] j3 = stockJavaSerialization(coll3);
        printRow("stock Java", n1, j1);
        printRow("stock Java", n2, j2);
        printRow("stock Java", n3, j3);
        boolean jossAllDiffer = !Arrays.equals(j1, j2) && !Arrays.equals(j2, j3) && !Arrays.equals(j1, j3);
        System.out.println("    -> stock Java serialization bytes all mutually DIFFERENT? " + jossAllDiffer);
        check(jossAllDiffer, title + " stock Java serialization difference");
        System.out.println();
    }

    private static void compareTwo(String title,
                                   String n1, Object v1, Object coll1,
                                   String n2, Object v2, Object coll2) throws Exception {
        System.out.println(title + ":");
        boolean eq = v1.equals(v2);
        System.out.println("  the two records are equal by value (.equals)? " + eq);
        check(eq, title + " .equals precondition");

        byte[] d1 = ShowcaseSupport.canonicalBytes(v1);
        byte[] d2 = ShowcaseSupport.canonicalBytes(v2);
        printRow("canonical ", n1, d1);
        printRow("canonical ", n2, d2);
        boolean derSame = Arrays.equals(d1, d2);
        System.out.println("    -> canonical bytes identical? " + derSame);
        check(derSame, title + " canonical byte-identity");

        byte[] j1 = stockJavaSerialization(coll1);
        byte[] j2 = stockJavaSerialization(coll2);
        printRow("stock Java", n1, j1);
        printRow("stock Java", n2, j2);
        boolean jossDiffer = !Arrays.equals(j1, j2);
        System.out.println("    -> stock Java serialization bytes DIFFERENT? " + jossDiffer);
        check(jossDiffer, title + " stock Java serialization difference");
        System.out.println();
    }

    /**
     * Stock JDK Java serialization ({@code java.io.ObjectOutputStream}) of the
     * collection itself. Used for the contrast because it shows exactly what the
     * default wire format records: the concrete implementation class and its
     * internal layout. (See the class javadoc for why this is NOT the project's
     * {@code AtomicMarshalOutputStream}.)
     */
    private static byte[] stockJavaSerialization(Object o) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(b)) {
            oos.writeObject(o);
        }
        return b.toByteArray();
    }

    private static void printRow(String kind, String implName, byte[] bytes) throws Exception {
        System.out.printf("    %-10s | %-13s : %4d bytes, SHA-256 %s...%n",
                kind, implName, bytes.length, ShowcaseSupport.sha256Hex(bytes).substring(0, 16));
    }

    private static void check(boolean held, String what) {
        if (!held) {
            System.out.println("    CLAIM FAILED: " + what);
            allHeld = false;
        }
    }

    // -------------------------------------------------------------------------

    /**
     * INFORMATIVE. The same value-not-implementation rule, mapped onto Rust and
     * Haskell collection types by this repository's cross-language research notes:
     * docs/der-rust-collection-mapping.md, docs/der-haskell-collection-mapping.md
     * (companions to docs/der-collection-ordering-research.md and
     * docs/der-type-model-and-element-rule.md). Those notes derive, from each
     * type's documented iteration-order guarantee, which types map onto the SAME
     * canonical DER form. The Rust and Haskell DER codecs themselves are still
     * future work, so this table states the documented mapping -- what WOULD
     * encode byte-equal -- not a live cross-runtime byte comparison.
     */
    private static void printCrossLanguageTable() {
        System.out.println("The same rule across languages (informative -- see note below):");
        System.out.println();
        System.out.println("  Java declared type        | Wire form (discipline)          | Rust                          | Haskell");
        System.out.println("  --------------------------+---------------------------------+-------------------------------+---------------------------------");
        System.out.println("  Set / HashSet             | SET OF, octet-sorted (canonical)| std HashSet<T>                | Data.HashSet (unordered-containers)");
        System.out.println("  Map / HashMap             | SET OF entries, key-sorted      | std HashMap<K,V>              | Data.HashMap (unordered-containers)");
        System.out.println("  List (ArrayList, ...)     | SEQUENCE OF, order preserved    | std Vec<T>/VecDeque/LinkedList| [a], Data.Sequence.Seq (containers)");
        System.out.println("  SortedSet / TreeSet       | SEQUENCE OF, sorted-preserved   | std BTreeSet<T>               | Data.Set (containers)");
        System.out.println("  SortedMap / TreeMap       | SEQUENCE OF, sorted-preserved   | std BTreeMap<K,V>             | Data.Map (containers)");
        System.out.println("  LinkedHashSet/-Map        | SEQUENCE OF, insertion-preserved| indexmap IndexSet/IndexMap    | OSet/OMap (ordered-containers)");
        System.out.println("                            |                                 |   (crate -- none in std)      |   (package -- none in base)");
        System.out.println("  PriorityQueue             | SET OF, octet-sorted multiset   | std BinaryHeap<T>             | MinQueue (pqueue) / Heap (heaps)");
        System.out.println();
        System.out.println("  The insertion-ordered row carries the same honest exception shown above,");
        System.out.println("  and it reproduces in Rust: indexmap's == ignores insertion order while its");
        System.out.println("  iteration (and so its bytes) preserve it -- byte-equality stricter than ==.");
        System.out.println("  Haskell's ordered-containers instead defines == order-SENSITIVELY, so there");
        System.out.println("  bytes and == agree exactly. That the same fork appears, for the same reason,");
        System.out.println("  in languages designed independently of this wire format is the evidence the");
        System.out.println("  ordering rule is a property of collection semantics, not a Java quirk.");
        System.out.println();
        System.out.println("  NOTE: the Rust and Haskell columns are the documented canonical-form mapping");
        System.out.println("  from docs/der-rust-collection-mapping.md and docs/der-haskell-collection-");
        System.out.println("  mapping.md -- research grounded in those languages' own documentation. The");
        System.out.println("  Rust and Haskell encoders are future work, so this is NOT a live measured");
        System.out.println("  cross-runtime byte comparison; the Java bytes above are the measured part.");
        System.out.println();
    }

    private CollectionEqualityDemo() {}
}
