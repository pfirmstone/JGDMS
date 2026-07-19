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
package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression coverage for a silent field-loss bug found during the
 * 2026-07-20 adversarial board review of JGDMS-Board-tracked task T2
 * (smart-proxy isolation wire-handoff): decoding a plain {@code
 * java.io.Externalizable} object (no {@code @AtomicExternal} annotation)
 * whose {@code readExternal()} reads a reference-typed field via {@code
 * ObjectInput.readObject()} silently dropped that field -- {@code
 * readExternal()} was never even entered, the object came back constructed
 * via its no-arg constructor with the field at its default value, and the
 * decode was reported as fully successful (no exception at all). Confirmed
 * specific to {@code AtomicMarshalInputStream.create(..., readAnnotations =
 * false)} (the configuration {@code WireHandoffCodec} and, more generally,
 * any {@code ObjectOutputStream}-compatible use of this codec relies on);
 * the same class round-trips correctly through vanilla {@code
 * java.io.ObjectOutputStream}/{@code ObjectInputStream}.
 *
 * <p>Two compounding bugs, both fixed:
 * <ol>
 *   <li>{@code ObjOutputStream.writeNewClassDesc} only set the class
 *       descriptor's {@code SC_EXTERNALIZABLE} flag for {@code
 *       @AtomicExternal}-annotated classes, even though {@code
 *       writeNewObject} unconditionally calls {@code writeExternal()} on
 *       <em>any</em> {@code Externalizable} class regardless of that
 *       annotation. Real instance data was written under a class
 *       descriptor whose flags byte falsely claimed "not serializable, not
 *       externalizable, zero declared fields" -- on read, {@code
 *       AtomicMarshalInputStream} faithfully believed the (wrong) flags,
 *       took the field-table path instead of calling {@code readExternal},
 *       found zero fields to read, and silently left the real written data
 *       on the wire unconsumed.</li>
 *   <li>Once (1) was fixed, a second, previously-latent bug surfaced:
 *       {@code AtomicMarshalInputStream.readyPrimitiveData} unconditionally
 *       consumed the next stream tag, assuming it was always a {@code
 *       TC_BLOCKDATA}/{@code TC_BLOCKDATALONG}/{@code TC_RESET} marker --
 *       but {@code ObjOutputStream.drain()} only emits one of those when
 *       primitive data was actually buffered during {@code writeExternal}.
 *       An {@code Externalizable} class whose {@code writeExternal}'s
 *       <em>first</em> call is an object write (no preceding primitive
 *       write) has no such marker; the real first tag was being silently
 *       swallowed instead of being left for {@code readExternal}'s own
 *       read.</li>
 * </ol>
 *
 * <p>Each case below is asserted twice: once against vanilla {@code
 * java.io.ObjectOutputStream}/{@code ObjectInputStream} (the ground truth
 * this codec claims general compatibility with), and once against this
 * codec in the exact configuration the wire-handoff path uses.
 */
public class ExternalizableFieldLossRegressionTest {

    /** {@code readExternal}'s first (and only) read is an object -- no
     *  leading primitive write. This is the shape that silently lost its
     *  field before both fixes. */
    public static final class ObjectOnlyWrapper implements Externalizable {
        Object v;
        public ObjectOnlyWrapper() { }
        public ObjectOnlyWrapper(Object v) { this.v = v; }
        @Override public void writeExternal(ObjectOutput out) throws java.io.IOException {
            out.writeObject(v);
        }
        @Override public void readExternal(ObjectInput in)
                throws java.io.IOException, ClassNotFoundException {
            this.v = in.readObject();
        }
    }

    /** {@code readExternal} reads a primitive first, then an object --
     *  proves the fix does not regress the (previously-working) case with
     *  real primitive block data. */
    public static final class PrimitiveThenObjectWrapper implements Externalizable {
        Object v;
        public PrimitiveThenObjectWrapper() { }
        public PrimitiveThenObjectWrapper(Object v) { this.v = v; }
        @Override public void writeExternal(ObjectOutput out) throws java.io.IOException {
            out.writeByte(1);
            out.writeObject(v);
        }
        @Override public void readExternal(ObjectInput in)
                throws java.io.IOException, ClassNotFoundException {
            in.readByte();
            this.v = in.readObject();
        }
    }

    /** A field-less Externalizable is deliberately NOT a useful regression
     *  probe for this bug class -- it has nothing to lose, and this exact
     *  class of bug reported "success" either way. Included only as a
     *  negative control confirming the fix does not introduce new failures
     *  for the trivial case. */
    public static final class EmptyWrapper implements Externalizable {
        public EmptyWrapper() { }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] marshalAtomic(Object value) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new AtomicMarshalOutputStream(bos, null);
        out.writeObject(value);
        out.flush();
        return bos.toByteArray();
    }

    private static Object unmarshalAtomic(byte[] bytes, ClassLoader loader) throws Exception {
        ObjectInputStream in = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(bytes), loader, false, null, null, false);
        return in.readObject();
    }

    private static byte[] marshalVanilla(Object value) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(value);
        }
        return bos.toByteArray();
    }

    private static Object unmarshalVanilla(byte[] bytes) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void objectOnlyWrapper_roundTripsThroughVanillaJavaIo() throws Exception {
        ObjectOnlyWrapper w = new ObjectOnlyWrapper("hello-field");
        ObjectOnlyWrapper w2 = (ObjectOnlyWrapper) unmarshalVanilla(marshalVanilla(w));
        assertEquals("ground truth: vanilla java.io must round-trip this shape",
                "hello-field", w2.v);
    }

    @Test
    public void objectOnlyWrapper_roundTripsThroughAtomicMarshalStreams() throws Exception {
        ObjectOnlyWrapper w = new ObjectOnlyWrapper("hello-field");
        byte[] bytes = marshalAtomic(w);
        ObjectOnlyWrapper w2 = (ObjectOnlyWrapper)
                unmarshalAtomic(bytes, getClass().getClassLoader());
        assertNotNull("must decode to a real instance, not null", w2);
        assertEquals("the Externalizable field must not be silently dropped --"
                + " this is the exact board-reported failure mode (no"
                + " exception, field left at its default value)",
                "hello-field", w2.v);
    }

    @Test
    public void primitiveThenObjectWrapper_roundTripsThroughAtomicMarshalStreams() throws Exception {
        PrimitiveThenObjectWrapper w = new PrimitiveThenObjectWrapper("hello-field-2");
        byte[] bytes = marshalAtomic(w);
        PrimitiveThenObjectWrapper w2 = (PrimitiveThenObjectWrapper)
                unmarshalAtomic(bytes, getClass().getClassLoader());
        assertEquals("hello-field-2", w2.v);
    }

    @Test
    public void objectOnlyWrapper_nullField_roundTrips() throws Exception {
        // Degenerate/boundary case (G11): a null field value must not be
        // confused with "field silently lost."
        ObjectOnlyWrapper w = new ObjectOnlyWrapper(null);
        byte[] bytes = marshalAtomic(w);
        ObjectOnlyWrapper w2 = (ObjectOnlyWrapper)
                unmarshalAtomic(bytes, getClass().getClassLoader());
        assertNull(w2.v);
    }

    @Test
    public void objectOnlyWrapper_arrayField_roundTrips() throws Exception {
        ObjectOnlyWrapper w = new ObjectOnlyWrapper(new String[]{"a", "b", "c"});
        byte[] bytes = marshalAtomic(w);
        ObjectOnlyWrapper w2 = (ObjectOnlyWrapper)
                unmarshalAtomic(bytes, getClass().getClassLoader());
        assertArrayEquals(new String[]{"a", "b", "c"}, (String[]) w2.v);
    }

    @Test
    public void emptyWrapper_negativeControl_stillDecodes() throws Exception {
        byte[] bytes = marshalAtomic(new EmptyWrapper());
        Object result = unmarshalAtomic(bytes, getClass().getClassLoader());
        assertTrue(result instanceof EmptyWrapper);
    }

    /**
     * The board's original repro shape: a 2-element reference array with a
     * non-last {@code Externalizable} element (stateful, not the field-less
     * probe the board's own repro happened to use -- this additionally
     * proves the field itself survives, not merely that decoding no longer
     * throws).
     */
    @Test
    public void nonLastExternalizableArrayElement_roundTripsWithFieldIntact() throws Exception {
        Object[] arr = new Object[]{ new ObjectOnlyWrapper("carried"), "trailing" };
        byte[] bytes = marshalAtomic(arr);
        Object[] result = (Object[]) unmarshalAtomic(bytes, getClass().getClassLoader());
        assertEquals(2, result.length);
        assertTrue(result[0] instanceof ObjectOnlyWrapper);
        assertEquals("carried", ((ObjectOnlyWrapper) result[0]).v);
        assertEquals("trailing", result[1]);
    }
}
