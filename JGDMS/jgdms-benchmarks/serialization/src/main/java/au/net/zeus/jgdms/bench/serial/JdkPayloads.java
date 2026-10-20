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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The payloads of {@link Payloads} as plain {@link Serializable} classes, for the
 * JDK serialization and Java RMI baselines: the same fields and the same values, so
 * the bytes carry the same data. ({@code Payloads} is not made Serializable itself,
 * which could change what the JOSS and DER results measure.)
 */
public final class JdkPayloads {

    private JdkPayloads() {}

    /** Builds the payload named by a JMH {@code @Param}, as {@link Payloads#create} does. */
    public static Object create(String name) {
        switch (name) {
            case "point":    return new Point(7, -42);
            case "record":   return record(1);
            case "batch100": return batch(Payloads.BATCH_SIZE);
            case "blob64k":  return blob(Payloads.BLOB_SIZE);
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

    public static final class Point implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int x;
        public final int y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) { return o instanceof Point p && p.x == x && p.y == y; }
        @Override public int hashCode() { return 31 * x + y; }
    }

    public static final class Record implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long id;
        public final String name;
        public final String description;
        public final String owner;
        public Record(long id, String name, String description, String owner) {
            this.id = id; this.name = name; this.description = description; this.owner = owner;
        }
        @Override public boolean equals(Object o) {
            return o instanceof Record r && r.id == id && r.name.equals(name)
                    && r.description.equals(description) && r.owner.equals(owner);
        }
        @Override public int hashCode() { return Long.hashCode(id) ^ name.hashCode(); }
    }

    public static final class Batch implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Record> items;
        public Batch(List<Record> items) { this.items = new ArrayList<>(items); }
        @Override public boolean equals(Object o) { return o instanceof Batch b && b.items.equals(items); }
        @Override public int hashCode() { return items.hashCode(); }
    }

    public static final class Blob implements Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] bytes;
        public Blob(byte[] bytes) { this.bytes = bytes.clone(); }
        @Override public boolean equals(Object o) { return o instanceof Blob b && Arrays.equals(b.bytes, bytes); }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }
}
