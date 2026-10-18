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

import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.*;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-bytes oracle for SOW-DER-Codec-Performance (sec.3.1): the encoder's
 * output for a fixed corpus, each payload alone in a stream and three of the
 * same class in one stream (so Appendix C {@code chainRef}s occur), must be
 * byte-identical to the fixtures under {@code src/test/resources/golden/}.
 *
 * <p>The fixtures were generated once from the unmodified encoder. A mismatch
 * means the wire format changed: stop and report, do not regenerate. Regeneration
 * (only for a deliberate, reviewed wire change) is {@code -Dgolden.generate=true}.
 *
 * <p>Fixture files hold one {@code single=} and one {@code triple=} line. Streams
 * up to {@value #FULL_HEX_LIMIT} bytes are stored as full hex; larger ones
 * (the 64 KiB blob, the 100-record batch) as {@code sha256:<length>:<hex digest>}
 * to keep the repository small.
 */
class DerGoldenBytesTest {

    private static final int FULL_HEX_LIMIT = 8192;
    private static final boolean GENERATE = Boolean.getBoolean("golden.generate");
    private static final Path DIR = Paths.get("src/test/resources/golden");

    // ---- benchmark-shaped payloads (same shapes as jgdms-benchmarks Payloads) ----

    @AtomicSerial
    public static final class Point {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("x", int.class),
                new AtomicSerial.SerialForm("y", int.class)
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Point p) throws IOException {
            arg.put("x", p.x);
            arg.put("y", p.y);
            arg.writeArgs();
        }
        final int x, y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        public Point(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("x", 0), arg.get("y", 0));
        }
    }

    @AtomicSerial
    public static final class Rec {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("id", long.class),
                new AtomicSerial.SerialForm("name", String.class),
                new AtomicSerial.SerialForm("description", String.class),
                new AtomicSerial.SerialForm("owner", String.class)
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Rec r) throws IOException {
            arg.put("id", r.id);
            arg.put("name", r.name);
            arg.put("description", r.description);
            arg.put("owner", r.owner);
            arg.writeArgs();
        }
        final long id;
        final String name, description, owner;
        public Rec(long id, String name, String description, String owner) {
            this.id = id; this.name = name; this.description = description; this.owner = owner;
        }
        public Rec(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("id", 0L), (String) arg.get("name", null),
                 (String) arg.get("description", null), (String) arg.get("owner", null));
        }
    }

    @AtomicSerial
    public static final class Batch {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", List.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Batch b) throws IOException {
            arg.put("items", b.items);
            arg.writeArgs();
        }
        final List<Rec> items;
        public Batch(List<Rec> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public Batch(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this(new ArrayList<>((List<Rec>) arg.get("items", null)));
        }
    }

    @AtomicSerial
    public static final class Blob {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("data", byte[].class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Blob b) throws IOException {
            arg.put("data", b.data);
            arg.writeArgs();
        }
        final byte[] data;
        public Blob(byte[] data) { this.data = data; }
        public Blob(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this((byte[]) arg.get("data", null));
        }
    }

    private static Rec rec(long id) {
        return new Rec(id, "record-" + id,
                "A short description of record number " + id + " for the benchmark",
                "owner" + (id % 7) + "@example.org");
    }

    private static Batch batch100() {
        List<Rec> l = new ArrayList<>();
        for (int i = 0; i < 100; i++) l.add(rec(i));
        return new Batch(l);
    }

    private static Blob blob64k() {
        byte[] b = new byte[64 * 1024];
        new Random(0x5EED).nextBytes(b);
        return new Blob(b);
    }


    // ---- structural fixtures for chain-site coverage (SOW T3: sets/maps/arrays/proxies/recursion) ----

    @AtomicSerial
    public static final class Node {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("next", Node.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Node n) throws IOException {
            arg.put("next", n.next);
            arg.writeArgs();
        }
        final Node next;
        public Node(Node next) { this.next = next; }
        public Node(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.next = (Node) arg.get("next", null);
        }
    }

    /** Set&lt;Rec&gt;: a CANONICALISE (octet-sorted SET OF) collection whose elements are chain sites. */
    @AtomicSerial
    public static final class RecSet {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Set.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, RecSet h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Set<Rec> items;
        public RecSet(Set<Rec> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public RecSet(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.items = new HashSet<>((Set<Rec>) arg.get("items", null));
        }
    }

    /** Set&lt;NestedValue&gt; holding BOTH NestedValue and NestedValueSub: two chains whose first-occurrence order is the sort order. */
    @AtomicSerial
    public static final class PolySet {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Set.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, PolySet h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Set<NestedValue> items;
        public PolySet(Set<NestedValue> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public PolySet(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.items = new HashSet<>((Set<NestedValue>) arg.get("items", null));
        }
    }

    /** Map&lt;String, Rec&gt;: map values are chain sites. */
    @AtomicSerial
    public static final class RecMap {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Map.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, RecMap h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Map<String, Rec> items;
        public RecMap(Map<String, Rec> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public RecMap(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.items = new TreeMap<>((Map<String, Rec>) arg.get("items", null));
        }
    }

    public interface Greet { String greet(); }

    /** InvocationHandler carrying an @AtomicSerial field: its interior sits inside a [8] proxy region. */
    @AtomicSerial
    public static final class GreetHandler implements java.lang.reflect.InvocationHandler {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("rec", VersionedRecord.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, GreetHandler h) throws IOException {
            arg.put("rec", h.rec);
            arg.writeArgs();
        }
        final VersionedRecord rec;
        public GreetHandler(VersionedRecord rec) { this.rec = rec; }
        public GreetHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.rec = (VersionedRecord) arg.get("rec", null);
        }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] a) {
            return rec == null ? "?" : rec.getLabel();
        }
    }

    /** Record with a Greet-proxy field and a plain nested record beside it. */
    @AtomicSerial
    public static final class ProxyHolder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("greet", Greet.class),
                new AtomicSerial.SerialForm("rec", VersionedRecord.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, ProxyHolder h) throws IOException {
            arg.put("greet", h.greet);
            arg.put("rec", h.rec);
            arg.writeArgs();
        }
        final Greet greet;
        final VersionedRecord rec;
        public ProxyHolder(Greet greet, VersionedRecord rec) { this.greet = greet; this.rec = rec; }
        public ProxyHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.greet = (Greet) arg.get("greet", null);
            this.rec = (VersionedRecord) arg.get("rec", null);
        }
    }

    private static Greet greetProxy() {
        return (Greet) java.lang.reflect.Proxy.newProxyInstance(
                Greet.class.getClassLoader(), new Class<?>[] {Greet.class},
                new GreetHandler(new VersionedRecord(3, "inside-proxy", "x")));
    }

    private static Set<Rec> recSet() {
        Set<Rec> s = new HashSet<>();
        for (int i = 0; i < 12; i++) s.add(rec(i * 37));
        return s;
    }


    // ---- A0 fixtures: boundaries, nesting, empties, nested sorted sets, key-order-sensitive maps ----

    /** Marker: write each item, in order, as consecutive stream items (a heterogeneous stream). */
    static final class MixedStream {
        final Object[] items;
        MixedStream(Object... items) { this.items = items; }
    }

    /** Marker: a top-level {@code [16]} collection item written with writeCollection(coll, token). */
    static final class TopLevelCollection {
        final Object coll;
        final String token;
        TopLevelCollection(Object coll, String token) { this.coll = coll; this.token = token; }
    }

    /** A record with no fields at all: its class SEQUENCE is the empty construct {@code 30 00}. */
    @AtomicSerial
    public static final class ZeroField {
        public static AtomicSerial.SerialForm[] serialForm() { return new AtomicSerial.SerialForm[0]; }
        public static void serialize(AtomicSerial.PutArg arg, ZeroField z) throws IOException { arg.writeArgs(); }
        public ZeroField() {}
        public ZeroField(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {}
    }

    /** Empty string, empty byte[], and an empty List / Set / Map in one record. */
    @AtomicSerial
    public static final class Empties {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("s", String.class),
                new AtomicSerial.SerialForm("b", byte[].class),
                new AtomicSerial.SerialForm("l", List.class),
                new AtomicSerial.SerialForm("st", Set.class),
                new AtomicSerial.SerialForm("m", Map.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Empties e) throws IOException {
            arg.put("s", e.s); arg.put("b", e.b); arg.put("l", e.l); arg.put("st", e.st); arg.put("m", e.m);
            arg.writeArgs();
        }
        final String s; final byte[] b; final List<Rec> l; final Set<Rec> st; final Map<String, Rec> m;
        public Empties() { this("", new byte[0], new ArrayList<>(), new HashSet<>(), new TreeMap<>()); }
        Empties(String s, byte[] b, List<Rec> l, Set<Rec> st, Map<String, Rec> m) {
            this.s = s; this.b = b; this.l = l; this.st = st; this.m = m;
        }
        @SuppressWarnings("unchecked")
        public Empties(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this((String) arg.get("s", null), (byte[]) arg.get("b", null),
                 new ArrayList<>((List<Rec>) arg.get("l", null)), new HashSet<>((Set<Rec>) arg.get("st", null)),
                 new TreeMap<>((Map<String, Rec>) arg.get("m", null)));
        }
    }

    /** A bag: a canonicalised multiset (PriorityQueue) whose duplicate encodings are kept. */
    @AtomicSerial
    public static final class StringBag {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", java.util.PriorityQueue.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, StringBag b) throws IOException {
            arg.put("items", b.items);
            arg.writeArgs();
        }
        final java.util.PriorityQueue<String> items;
        public StringBag(java.util.PriorityQueue<String> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public StringBag(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this(new java.util.PriorityQueue<>((java.util.Collection<String>) arg.get("items", null)));
        }
    }

    /** Set&lt;RecSet&gt;: a sorted set whose ELEMENTS each contain a sorted set. */
    @AtomicSerial
    public static final class SetOfRecSets {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Set.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, SetOfRecSets h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Set<RecSet> items;
        public SetOfRecSets(Set<RecSet> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public SetOfRecSets(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.items = new HashSet<>((Set<RecSet>) arg.get("items", null));
        }
    }

    /** Map&lt;K,V&gt; whose ITERATION order is deliberately not the canonical key order (insertion-ordered). */
    @AtomicSerial
    public static final class IterationOrderMap {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Map.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, IterationOrderMap h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Map<Object, String> items;
        public IterationOrderMap(Map<Object, String> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public IterationOrderMap(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.items = new LinkedHashMap<>((Map<Object, String>) arg.get("items", null));
        }
    }

    /** Map&lt;String, RecSet&gt;: map VALUES that each contain a sorted set. */
    @AtomicSerial
    public static final class MapOfRecSets {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Map.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, MapOfRecSets h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Map<String, RecSet> items;
        public MapOfRecSets(Map<String, RecSet> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public MapOfRecSets(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.items = new TreeMap<>((Map<String, RecSet>) arg.get("items", null));
        }
    }

    /** A Rec whose three strings are {@code n} characters each, so entry sizes can be controlled. */
    private static Rec bigRec(long id, int n) {
        return new Rec(id, "n".repeat(n), "d".repeat(n), "o".repeat(n));
    }

    /**
     * Blob sizes chosen so that SOME enclosing construct (the field OCTET STRING, the class SEQUENCE, the
     * hierarchy SEQUENCE, the record, the array) has a content length of exactly 127/128, 255/256 or
     * 65535/65536 octets, and so that those constructs sit inside others that straddle a boundary too.
     */
    private static final int[] BOUNDARY_BLOB_SIZES = boundarySizes();

    private static int[] boundarySizes() {
        List<Integer> l = new ArrayList<>();
        l.add(0); l.add(1);
        for (int n = 118; n <= 136; n++) l.add(n);
        for (int n = 248; n <= 264; n++) l.add(n);
        for (int n = 65524; n <= 65546; n++) l.add(n);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Blob[] boundaryBlobs() {
        Blob[] out = new Blob[BOUNDARY_BLOB_SIZES.length];
        for (int i = 0; i < out.length; i++) {
            byte[] b = new byte[BOUNDARY_BLOB_SIZES[i]];
            for (int j = 0; j < b.length; j++) b[j] = (byte) (j * 31 + i);
            out[i] = new Blob(b);
        }
        return out;
    }

    private static Chain deepChain(int objects) {
        Chain c = null;
        for (int i = 0; i < objects; i++) c = new Chain(c);
        return c;
    }

    /** Entries whose triple-in-a-stream form is skipped (their single form is already very large). */
    private static final java.util.Set<String> SINGLE_ONLY =
            java.util.Set.of("blob-16MiB-minus-1", "list-65536");


    // ---- corpus ----

    private static Map<String, Supplier<Object>> corpus() {
        Map<String, Supplier<Object>> c = new LinkedHashMap<>();
        c.put("bench-point", () -> new Point(7, -42));
        c.put("bench-record", () -> rec(1));
        c.put("bench-batch100", DerGoldenBytesTest::batch100);
        c.put("bench-blob64k", DerGoldenBytesTest::blob64k);
        c.put("simple-record", () -> new SimpleRecord(true, 42, "name", new byte[] {1, 2, 3, (byte) 0xFF}));
        c.put("int-box", () -> new IntBox(-123456));
        c.put("multi-type", () -> new MultiTypeRecord(true, (byte) -5, (short) 300, 70000,
                1L << 40, "text", new byte[] {9, 8, 7}));
        c.put("array-record", () -> new ArrayRecord(new int[] {1, -2, 3}, new long[] {Long.MIN_VALUE, 0, Long.MAX_VALUE},
                new short[] {5, -6}, new boolean[] {true, false}, new String[] {"a", null, "c"}));
        c.put("enum-record", () -> new EnumRecord("lbl", Status.values()[0]));
        c.put("chain-3", () -> new Chain(new Chain(new Chain((Chain) null))));
        c.put("nested-value", () -> new NestedValue(5, "five"));
        c.put("outer-nested", () -> new OuterWithNested("tag", new NestedValue(1, "one")));
        c.put("nested-array", () -> new NestedArrayHolder("t",
                new NestedValue[] {new NestedValue(1, "a"), new NestedValueSub(2, "b", "extra")}));
        c.put("poly-list", () -> new PolySetHolder(new ArrayList<>(Arrays.asList(
                new NestedValue(1, "a"), new NestedValueSub(2, "b", "x")))));
        c.put("counter", () -> new CounterRecord(99L, "c"));
        c.put("foo", () -> new Foo(1, "foo"));
        c.put("alpha", () -> new Alpha(1, "alpha"));
        c.put("beta", () -> new Beta(1, "alpha", 2, "beta"));
        c.put("versioned-record", () -> new VersionedRecord(7, "label", "extra"));
        c.put("plain-set", () -> new PlainSetFieldRecord(new TreeSet<>(Arrays.asList(5, 4, 9))));
        c.put("sorted-map", () -> {
            TreeMap<Integer, String> m = new TreeMap<>();
            m.put(2, "b"); m.put(1, "a");
            return new SortedMapFieldRecord(m);
        });
        c.put("node-recursion", () -> new Node(new Node(new Node(new Node((Node) null)))));
        c.put("rec-set", DerGoldenBytesTest::recSet0);
        c.put("poly-set", () -> {
            Set<NestedValue> s = new HashSet<>();
            for (int i = 0; i < 6; i++) {
                s.add(new NestedValue(i, "v" + i));
                s.add(new NestedValueSub(i, "w" + i, "extra" + i));
            }
            return new PolySet(s);
        });
        c.put("rec-map", () -> {
            Map<String, Rec> m = new TreeMap<>();
            for (int i = 0; i < 8; i++) m.put("key" + i, rec(i));
            return new RecMap(m);
        });
        c.put("autowired-set", () -> {
            Set<Foo> s = new HashSet<>();
            for (int i = 0; i < 5; i++) s.add(new Foo(i, "f" + i));
            return new AutoWiredSetRecord(s);
        });
        c.put("chain-array-holder", () -> new ChainArrayHolder(new Chain[] {
                new Chain((Chain) null), new Chain(new Chain((Chain) null)), new Chain((Chain) null)}));
        c.put("proxy-holder", () -> new ProxyHolder(greetProxy(), new VersionedRecord(1, "beside", "y")));
        c.put("top-level-array-rec", () -> new Rec[] {rec(1), rec(2), rec(3)});
        c.put("top-level-array-nested", () -> new NestedValue[] {
                new NestedValue(1, "a"), new NestedValueSub(2, "b", "x"), new NestedValue(3, "c")});
        c.put("top-level-proxy", DerGoldenBytesTest::greetProxy);
        // ---- A0 ----
        c.put("deep-chain-max-nesting", () -> deepChain(ObjectCodec.MAX_NESTING));
        c.put("list-65536", () -> {   // exactly maxCollection: still decodable, content length > 65535
            List<Rec> l = new ArrayList<>(65536);
            for (int i = 0; i < 65536; i++) l.add(rec(i));
            return new Batch(l);
        });
        c.put("boundary-blob-array", DerGoldenBytesTest::boundaryBlobs);
        c.put("blob-16MiB-minus-1", () -> {
            byte[] b = new byte[16 * 1024 * 1024 - 1];
            for (int i = 0; i < b.length; i++) b[i] = (byte) (i * 31);
            return new Blob(b);
        });
        c.put("empties", Empties::new);
        c.put("zero-field-record", ZeroField::new);
        c.put("string-bag-duplicates", () -> new StringBag(
                new java.util.PriorityQueue<>(Arrays.asList("b", "a", "b", "a", "c", "b"))));
        c.put("map-key-order-differs-from-entry-order", () -> {
            // keys sort k0 < k1 < ... but the values shrink, so whole-entry order (dominated by the entry's
            // length octets) is the REVERSE of key order: a sort on the wrong key changes the bytes
            Map<String, Rec> m = new TreeMap<>();
            for (int i = 0; i < 6; i++) m.put("k" + i, bigRec(i, 60 - i * 10));
            return new RecMap(m);
        });
        c.put("map-iteration-order-differs-from-key-order", () -> {
            // insertion order is neither natural nor encoded key order: canonical output must not depend on it
            Map<Object, String> m = new LinkedHashMap<>();
            for (String k : new String[] {"bb", "a", "ccc", "b", "aa", "", "c"}) m.put(k, "v".repeat(k.length() * 40 + 1));
            return new IterationOrderMap(m);
        });
        c.put("map-integer-keys-across-length-boundaries", () -> {
            Map<Object, String> m = new LinkedHashMap<>();
            for (long k : new long[] {256, -1, 128, 127, -129, 0, 65536, -128, 32768}) m.put(k, "v" + k);
            return new IterationOrderMap(m);
        });
        c.put("set-of-sets", () -> {
            Set<RecSet> outer = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                Set<Rec> inner = new HashSet<>();
                for (int j = 0; j <= i; j++) inner.add(rec(i * 10 + j));
                outer.add(new RecSet(inner));
            }
            return new SetOfRecSets(outer);
        });
        c.put("map-of-sets", () -> {
            Map<String, RecSet> m = new TreeMap<>();
            for (int i = 0; i < 4; i++) {
                Set<Rec> inner = new HashSet<>();
                for (int j = 0; j <= 3 - i; j++) inner.add(rec(i * 10 + j));
                m.put("key" + i, new RecSet(inner));
            }
            return new MapOfRecSets(m);
        });
        c.put("top-level-scalars-and-null", () -> new MixedStream(
                Integer.valueOf(5), Long.valueOf(-9L), Boolean.TRUE, Double.valueOf(1.5), Character.valueOf('x'),
                "", "plain string", new byte[0], new byte[] {1, 2, 3}, null, Status.values()[1]));
        c.put("top-level-collection-16-set", () -> new TopLevelCollection(
                new HashSet<>(Arrays.asList(3, 1, 2, 10, 200)), "set:int"));
        c.put("top-level-collection-16-list-atomic", () -> new TopLevelCollection(
                new ArrayList<>(Arrays.asList(rec(1), rec(2), rec(3))), "list:@AtomicSerial"));
        c.put("mixed-top-level-then-nested", () -> new MixedStream(
                new NestedValue(1, "first"), new OuterWithNested("t", new NestedValue(2, "second")),
                new NestedValue(3, "third"), rec(7)));
        return c;
    }

    private static Object recSet0() { return new RecSet(recSet()); }

    // ---- encoding / fixture IO ----

    private static void writeOne(DerObjectStreamCodec c, Object o) throws IOException {
        if (o instanceof TopLevelCollection t) {
            c.writeCollection(t.coll, t.token);
        } else if (o instanceof MixedStream m) {
            for (Object item : m.items) writeOne(c, item);
        } else {
            c.writeObject(o);
        }
    }

    /** Number of stream items a corpus object contributes per repetition. */
    private static int itemCount(Object o) {
        return o instanceof MixedStream m ? m.items.length : 1;
    }

    private static byte[] encode(Supplier<Object> s, int count) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (int i = 0; i < count; i++) writeOne(c, s.get());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    private static String render(byte[] b) throws Exception {
        if (b.length <= FULL_HEX_LIMIT) return hex(b);
        return "sha256:" + b.length + ":" + hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static Map<String, String> read(Path p) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.US_ASCII)) {
            int i = line.indexOf('=');
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }

    @TestFactory
    Stream<DynamicTest> goldenBytes() {
        return corpus().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            String single = render(encode(e.getValue(), 1));
            String triple = SINGLE_ONLY.contains(e.getKey()) ? "single-only" : render(encode(e.getValue(), 3));
            // the encoder must be deterministic within a run before it can be golden
            if (!SINGLE_ONLY.contains(e.getKey())) {
                assertEquals(single, render(encode(e.getValue(), 1)), "non-deterministic single");
            }
            Path f = DIR.resolve(e.getKey() + ".hex");
            if (GENERATE) {
                Files.createDirectories(DIR);
                Files.write(f, ("single=" + single + "\ntriple=" + triple + "\n")
                        .getBytes(StandardCharsets.US_ASCII));
                return;
            }
            assertTrue(Files.exists(f), "missing golden fixture " + f);
            Map<String, String> g = read(f);
            assertEquals(g.get("single"), single, e.getKey() + ": single-object stream bytes changed");
            assertEquals(g.get("triple"), triple, e.getKey() + ": three-object stream bytes changed");
        }));
    }

    /**
     * Decode-side oracle (SOW T3): every corpus stream (alone and three-in-a-stream, so chainRefs
     * resolve against the per-stream table) must decode, and re-encoding the decoded objects must
     * reproduce the stream byte-for-byte. Exercises the verified-chain hand-off to the record
     * codec and the nested-record lookup across sets, maps, arrays, hierarchies and proxies.
     */
    @TestFactory
    Stream<DynamicTest> decodeRoundTripReproducesBytes() {
        return corpus().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            Object sample = e.getValue().get();
            for (int count : SINGLE_ONLY.contains(e.getKey()) ? new int[] {1} : new int[] {1, 3}) {
                byte[] bytes = encode(e.getValue(), count);
                DerObjectStreamCodec in = new DerObjectStreamCodec();
                in.initReader(bytes);
                DerObjectStreamCodec out = new DerObjectStreamCodec();
                for (int i = 0; i < count; i++) {
                    if (sample instanceof TopLevelCollection t) {
                        out.writeCollection(in.readObject(), t.token);
                    } else {
                        for (int k = 0; k < itemCount(sample); k++) out.writeObject(in.readObject());
                    }
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                out.drainTo(bos);
                assertEquals(render(bytes), render(bos.toByteArray()),
                        e.getKey() + " x" + count + ": decode then re-encode changed the bytes");
            }
        }));
    }

    // ---- the encoder enforces the decoder's maxCollection ceiling (STD-006 sec.4.5) ----

    private static List<Rec> recs(int n) {
        List<Rec> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Rec(i, "n", "d", "o"));
        return l;
    }

    private static void assertCeilingRejectedOnEncode(Object o) {
        Exception e = assertThrows(Exception.class, () -> encode(() -> o, 1));
        StringBuilder chain = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) chain.append(t).append(" <- ");
        assertTrue(chain.toString().contains("maxCollection"), chain.toString());
    }

    @org.junit.jupiter.api.Test
    void encoderRejectsAListOverTheDecodersMaxCollection() throws Exception {
        assertCeilingRejectedOnEncode(new Batch(recs(65537)));          // list field
        assertCeilingRejectedOnEncode(new Batch(recs(70000)));
        // exactly at the ceiling still encodes AND decodes (list-65536 in the corpus round-trips)
        assertDoesNotThrow(() -> encode(() -> new Batch(recs(65536)), 1));
    }

    @org.junit.jupiter.api.Test
    void encoderRejectsASetAMapAndATopLevelCollectionOverTheCeiling() throws Exception {
        Set<Rec> bigSet = new HashSet<>();
        for (int i = 0; i < 65537; i++) bigSet.add(new Rec(i, "n", "d", "o"));
        assertCeilingRejectedOnEncode(new RecSet(bigSet));
        Map<String, Rec> bigMap = new TreeMap<>();
        for (int i = 0; i < 65537; i++) bigMap.put(String.format("k%06d", i), new Rec(i, "n", "d", "o"));
        assertCeilingRejectedOnEncode(new RecMap(bigMap));
        assertCeilingRejectedOnEncode(new TopLevelCollection(recs(65537), "list:@AtomicSerial"));
        assertDoesNotThrow(() -> encode(() -> new TopLevelCollection(recs(65536), "list:@AtomicSerial"), 1));
    }
}
