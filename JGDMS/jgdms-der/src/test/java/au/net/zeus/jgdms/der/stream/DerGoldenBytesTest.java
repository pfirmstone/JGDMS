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
import java.util.LinkedHashMap;
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
        return c;
    }

    // ---- encoding / fixture IO ----

    private static byte[] encode(Supplier<Object> s, int count) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (int i = 0; i < count; i++) c.writeObject(s.get());
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
            String triple = render(encode(e.getValue(), 3));
            // the encoder must be deterministic within a run before it can be golden
            assertEquals(single, render(encode(e.getValue(), 1)), "non-deterministic single");
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
}
