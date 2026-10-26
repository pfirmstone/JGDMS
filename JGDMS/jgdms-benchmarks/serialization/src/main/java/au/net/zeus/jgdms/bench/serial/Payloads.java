/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * The {@code @AtomicSerial} payloads both wire formats are measured on.
 *
 * <p>Every payload is encodable by both the Java Object Serialization Stream
 * (JOSS) atomic streams and the JGDMS-STD-006 DER streams, so each benchmark
 * compares the two formats on identical object graphs. The set spans the
 * shapes that dominate real JERI traffic:
 *
 * <ul>
 *   <li>{@link Point}: two {@code int}s. The smallest useful object; its cost is
 *       almost entirely per-call fixed overhead (stream header, class
 *       descriptor or schema).</li>
 *   <li>{@link Record}: a {@code long} and three {@code String}s, the shape of
 *       a typical lookup-service entry or event.</li>
 *   <li>{@link Batch}: a {@code List} of {@value #BATCH_SIZE} {@link Record}s,
 *       the shape of a lookup result or a bulk read. Shows how each format
 *       amortises per-class overhead across many instances of one class.</li>
 *   <li>{@link Blob}: a {@value #BLOB_SIZE}-byte {@code byte[]}. Measures the
 *       bulk-copy path, where framing should be negligible.</li>
 * </ul>
 *
 * <p>Payload content is deterministic (fixed seed) so that both formats, and
 * repeated runs, see byte-identical inputs.
 */
public final class Payloads {

    /** Number of records in a {@link Batch}. */
    public static final int BATCH_SIZE = 100;
    /** Number of bytes in a {@link Blob}. */
    public static final int BLOB_SIZE = 64 * 1024;

    private Payloads() {}

    /** Names accepted by {@link #create(String)}, used as JMH {@code @Param} values. */
    public static final String[] NAMES = { "point", "record", "batch100", "blob64k" };

    /** Builds the payload named by a JMH {@code @Param}. */
    public static Object create(String name) {
        switch (name) {
            case "point":    return new Point(7, -42);
            case "record":   return record(1);
            case "batch100": return batch(BATCH_SIZE);
            case "blob64k":  return blob(BLOB_SIZE);
            default: throw new IllegalArgumentException("unknown payload: " + name);
        }
    }

    static Record record(long id) {
        return new Record(id,
                "record-" + id,
                "A short description of record number " + id + " for the benchmark",
                "owner" + (id % 7) + "@example.org");
    }

    static Batch batch(int n) {
        List<Record> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(record(i));
        return new Batch(list);
    }

    static Blob blob(int n) {
        byte[] b = new byte[n];
        new Random(0x5EED).nextBytes(b);
        return new Blob(b);
    }

    // =========================================================================

    /** Two ints. */
    @AtomicSerial
    public static final class Point {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("x", int.class),
                new SerialForm("y", int.class)
            };
        }
        public static void serialize(PutArg arg, Point p) throws IOException {
            arg.put("x", p.x);
            arg.put("y", p.y);
            arg.writeArgs();
        }

        public final int x;
        public final int y;

        public Point(int x, int y) { this.x = x; this.y = y; }

        public Point(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("x", 0), arg.get("y", 0));
        }

        @Override public boolean equals(Object o) {
            return o instanceof Point p && p.x == x && p.y == y;
        }
        @Override public int hashCode() { return 31 * x + y; }
    }

    /** A long and three strings: the shape of an entry or event. */
    @AtomicSerial
    public static final class Record {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("id", long.class),
                new SerialForm("name", String.class),
                new SerialForm("description", String.class),
                new SerialForm("owner", String.class)
            };
        }
        public static void serialize(PutArg arg, Record r) throws IOException {
            arg.put("id", r.id);
            arg.put("name", r.name);
            arg.put("description", r.description);
            arg.put("owner", r.owner);
            arg.writeArgs();
        }

        public final long id;
        public final String name;
        public final String description;
        public final String owner;

        public Record(long id, String name, String description, String owner) {
            this.id = id;
            this.name = Objects.requireNonNull(name);
            this.description = Objects.requireNonNull(description);
            this.owner = Objects.requireNonNull(owner);
        }

        public Record(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("id", 0L),
                 arg.get("name", null, String.class),
                 arg.get("description", null, String.class),
                 arg.get("owner", null, String.class));
        }

        @Override public boolean equals(Object o) {
            return o instanceof Record r && r.id == id && r.name.equals(name)
                    && r.description.equals(description) && r.owner.equals(owner);
        }
        @Override public int hashCode() { return Long.hashCode(id) ^ name.hashCode(); }
    }

    /** A list of records: the shape of a lookup result. */
    @AtomicSerial
    public static final class Batch {
        public static SerialForm[] serialForm() {
            return new SerialForm[] { new SerialForm("items", List.class) };
        }
        public static void serialize(PutArg arg, Batch b) throws IOException {
            arg.put("items", b.items);
            arg.writeArgs();
        }

        private final List<Record> items;

        public Batch(List<Record> items) {
            this.items = List.copyOf(items);
        }

        @SuppressWarnings("unchecked")
        public Batch(GetArg arg) throws IOException, ClassNotFoundException {
            this((List<Record>) arg.get("items", null, List.class));
        }

        public List<Record> items() { return items; }

        @Override public boolean equals(Object o) {
            return o instanceof Batch b && b.items.equals(items);
        }
        @Override public int hashCode() { return items.hashCode(); }
    }

    /** A byte array: the bulk-copy path. */
    @AtomicSerial
    public static final class Blob {
        public static SerialForm[] serialForm() {
            return new SerialForm[] { new SerialForm("bytes", byte[].class) };
        }
        public static void serialize(PutArg arg, Blob b) throws IOException {
            arg.put("bytes", b.bytes);
            arg.writeArgs();
        }

        private final byte[] bytes;

        public Blob(byte[] bytes) { this.bytes = bytes.clone(); }

        public Blob(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("bytes", null, byte[].class));
        }

        public int size() { return bytes.length; }

        @Override public boolean equals(Object o) {
            return o instanceof Blob b && Arrays.equals(b.bytes, bytes);
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }
}
