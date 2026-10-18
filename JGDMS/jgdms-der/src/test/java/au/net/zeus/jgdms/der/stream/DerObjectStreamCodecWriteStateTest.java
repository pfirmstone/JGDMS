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

import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A4: the write side keeps ONE encoder across the items of a stream, so an item that fails must not leave a partial item
 * in it, or (A3 review) a schema chain recorded as sent that never was. An item that failed AFTER changing the stream
 * poisons it; one that failed before touching it does not.
 */
class DerObjectStreamCodecWriteStateTest {

    /** A record whose serialize() can be made to fail. */
    @AtomicSerial
    public static final class Boom {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("n", int.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Boom b) throws IOException {
            if (b.explode) throw new IOException("serialize failed on purpose");
            arg.put("n", b.n);
            arg.writeArgs();
        }
        final int n;
        final boolean explode;
        public Boom(int n, boolean explode) { this.n = n; this.explode = explode; }
        public Boom(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.n = arg.get("n", 0);
            this.explode = false;
        }
    }

    private static byte[] drain(DerObjectStreamCodec c) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    @Test
    void anItemRejectedBeforeItTouchedTheStreamLeavesItUsable() throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject("first");
        assertThrows(UnsupportedOperationException.class, () -> c.writeObject(new Object()));
        c.writeObject("second");
        DerObjectStreamCodec ref = new DerObjectStreamCodec();
        ref.writeObject("first");
        ref.writeObject("second");
        assertArrayEquals(drain(ref), drain(c), "the rejected item must leave no trace");
    }

    @Test
    void anItemThatFailedAfterChangingTheStreamPoisonsIt() throws Exception {
        // record-capture format writes the record straight into the shared output, so serialize() failing leaves a
        // half-built [1] item behind
        DerObjectStreamCodec c = new DerObjectStreamCodec(false);
        c.writeObject("kept");
        assertThrows(IOException.class, () -> c.writeObject(new Boom(1, true)));
        IOException e = assertThrows(IOException.class, () -> c.writeObject("after"));
        assertTrue(e.getMessage().contains("unusable"), e.getMessage());
        assertThrows(IOException.class, () -> c.drainTo(new ByteArrayOutputStream()),
                "a partial item must never be drained");
    }

    @Test
    void aTopLevelCollectionThatFailsMidwayPoisonsTheStream() throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        List<Boom> list = new ArrayList<>(List.of(new Boom(1, false), new Boom(2, true)));
        assertThrows(IOException.class, () -> c.writeCollection(list, "list:@AtomicSerial"));
        assertThrows(IOException.class, () -> c.writeObject("after"));
        assertThrows(IOException.class, () -> c.drainTo(new ByteArrayOutputStream()));
    }

    @Test
    void aFailedEncodeOfAnAtomicRecordInStreamFormatLeavesNothingBehind() throws Exception {
        // stream format encodes the canonical record into a scratch encoder first: a serialize() failure there touches
        // neither the output nor the dedup table, so the stream stays usable and byte-identical to one without the item
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(new Boom(7, false));
        assertThrows(IOException.class, () -> c.writeObject(new Boom(8, true)));
        c.writeObject(new Boom(9, false));
        DerObjectStreamCodec ref = new DerObjectStreamCodec();
        ref.writeObject(new Boom(7, false));
        ref.writeObject(new Boom(9, false));
        assertArrayEquals(drain(ref), drain(c));
    }

    @Test
    void aSinkThatFailedMayHaveTakenAPrefixSoTheStreamIsDeadNotRetried() throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject("one");
        ByteArrayOutputStream taken = new ByteArrayOutputStream();
        OutputStream prefixThenFail = new OutputStream() {
            @Override public void write(int b) throws IOException { taken.write(b); }
            @Override public void write(byte[] b, int o, int l) throws IOException {
                taken.write(b, o, Math.min(l, 2));
                throw new IOException("sink down after a prefix");
            }
        };
        assertThrows(IOException.class, () -> c.drainTo(prefixThenFail));
        assertEquals(2, taken.size(), "the sink really did take a prefix");
        assertThrows(IOException.class, () -> c.drainTo(new ByteArrayOutputStream()),
                "resending the whole buffer would duplicate that prefix");
        assertThrows(IOException.class, () -> c.writeObject("two"));
    }

    @Test
    void afterADrainTheNextDrainDoesNotRepeatTheVersionOctet() throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject("one");
        byte[] first = drain(c);
        c.writeObject("two");
        DerObjectStreamCodec ref = new DerObjectStreamCodec();
        ref.writeObject("one");
        drain(ref);
        ref.writeObject("two");
        assertEquals(first.length > 0, true);
        assertArrayEquals(drain(ref), drain(c));
    }

    /** serialize() reaching the same stream: a user-supplied handler holding the codec. */
    @AtomicSerial
    public static final class Reentrant {
        static volatile DerObjectStreamCodec target;
        static volatile boolean positional;
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("n", int.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Reentrant r) throws IOException {
            if (positional) target.writeInt(5); else target.writeObject("sneaky");
            arg.put("n", r.n);
            arg.writeArgs();
        }
        final int n;
        public Reentrant(int n) { this.n = n; }
        public Reentrant(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException { this.n = arg.get("n", 0); }
    }

    @Test
    void aWriteFromInsideSerializeIsRefusedAndKillsTheStream() throws Exception {
        for (boolean streamFormat : new boolean[] {true, false}) {
            for (boolean positional : new boolean[] {false, true}) {
                DerObjectStreamCodec c = new DerObjectStreamCodec(streamFormat);
                Reentrant.target = c;
                Reentrant.positional = positional;
                assertThrows(Exception.class, () -> c.writeObject(new Reentrant(1)),
                        "streamFormat=" + streamFormat + " positional=" + positional);
                assertThrows(IOException.class, () -> c.writeObject("after"));
                assertThrows(IOException.class, () -> c.drainTo(new ByteArrayOutputStream()));
            }
        }
        Reentrant.target = null;
    }

    @Test
    void closeStillClosesTheUnderlyingStreamWhenTheFlushFails() throws Exception {
        boolean[] closed = {false};
        OutputStream sink = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("sink down"); }
            @Override public void write(byte[] b, int o, int l) throws IOException { throw new IOException("sink down"); }
            @Override public void close() { closed[0] = true; }
        };
        DerMarshalOutputStream dmos = new DerMarshalOutputStream(sink);
        dmos.writeObject("x");
        assertThrows(IOException.class, dmos::close);
        assertTrue(closed[0], "a failed flush must not leave the underlying stream open");
    }

    @Test
    void severalDrainsConcatenateToTheSameStreamAsOne() throws Exception {
        Set<Object> items = new TreeSet<>(List.of("a", "b", "c"));
        DerObjectStreamCodec once = new DerObjectStreamCodec(), many = new DerObjectStreamCodec();
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (Object o : items) {
            once.writeObject(o);
            many.writeObject(o);
            many.writeObject(new Boom(o.hashCode(), false));
            once.writeObject(new Boom(o.hashCode(), false));
            many.drainTo(joined);
        }
        assertArrayEquals(drain(once), joined.toByteArray());
    }

    @Test
    void theDedupTableCountsOnlyInsertions() throws Exception {
        StreamSchemaDedup w = new StreamSchemaDedup(true);
        assertEquals(0, w.mutationCount());
        byte[] rec = canonical(new Boom(1, false));
        w.dedupTopLevelAtomic(rec, Boom.class);
        assertEquals(1, w.mutationCount(), "a first occurrence tables the chain");
        w.dedupTopLevelAtomic(canonical(new Boom(2, false)), Boom.class);
        assertEquals(1, w.mutationCount(), "a repeat occurrence changes nothing");
    }

    private static byte[] canonical(Object o) throws Exception {
        au.net.zeus.jgdms.der.schema.SchemaChain.Result chain =
                au.net.zeus.jgdms.der.schema.SchemaGenerator.generateChain(o.getClass());
        return au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord.fromChain(chain,
                au.net.zeus.jgdms.der.object.ObjectCodec.encodeHierarchy(o, chain)).encode();
    }
}
