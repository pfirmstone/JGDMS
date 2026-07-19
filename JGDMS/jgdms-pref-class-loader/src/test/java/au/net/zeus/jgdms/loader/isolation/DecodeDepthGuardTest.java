/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Direct, fast unit tests of {@link DecodeDepthGuard} in isolation --
 * complementing the full-stack proof in {@code
 * SubProcessWireHandoffEndToEndTest} (2026-07-20 board review, Finding 2:
 * unbounded decode-recursion depth in {@code AtomicMarshalInputStream}, no
 * ceiling of its own).
 *
 * <p>For the deep (thousands-of-levels) cases, the payload bytes are
 * <strong>hand-crafted directly</strong> rather than built by round-tripping
 * a real Java object graph through the real, recursive {@code
 * AtomicMarshalOutputStream} encoder -- because that encoder has exactly the
 * same unbounded-recursion property on the write side (confirmed: attempting
 * to marshal a 20,000-deep chain via the real encoder itself throws {@code
 * StackOverflowError}, in {@code ObjOutputStream.writeNewArray}, before this
 * scanner is ever reached). This is not a workaround for a test
 * inconvenience -- it is the more faithful adversarial shape anyway: a real
 * attacker crafts wire bytes directly and has no reason to go through a
 * conforming encoder at all. The hand-crafted byte layout matches the
 * standard {@code java.io.ObjectStreamConstants} tag protocol, verified
 * empirically against this codec's own encoder output for small cases (see
 * this task's development history) before being generalised here.
 */
public class DecodeDepthGuardTest {

    private static byte[] marshal(Object value) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new AtomicMarshalOutputStream(bos, null);
        out.writeObject(value);
        out.flush();
        return bos.toByteArray();
    }

    private static Object nestedArrayChain(int depth) {
        Object cur = null;
        for (int i = 0; i < depth; i++) cur = new Object[]{ cur };
        return cur;
    }

    @Test(timeout = 5_000)
    public void shallowChain_notRejected() throws Exception {
        DecodeDepthGuard.bestEffortCheck(marshal(nestedArrayChain(10)));
        // no exception -> pass
    }

    /**
     * The maximum recursion depth {@link DecodeDepthGuard}'s walker actually
     * reaches while walking an N-level single-element {@code Object[]}
     * chain is {@code N+1} (the deepest {@code TC_NULL} leaf read, one level
     * past the Nth array's own element read) -- so the largest chain that
     * stays within {@code MAX_DEPTH} is {@code MAX_DEPTH - 1} levels.
     * Verified empirically, not just derived: see the boundary pair below.
     */
    @Test(timeout = 5_000)
    public void chainJustUnderCeiling_notRejected() throws Exception {
        DecodeDepthGuard.bestEffortCheck(marshal(nestedArrayChain(DecodeDepthGuard.MAX_DEPTH - 1)));
    }

    @Test(timeout = 5_000)
    public void chainAtCeiling_rejected() throws Exception {
        try {
            DecodeDepthGuard.bestEffortCheck(marshal(nestedArrayChain(DecodeDepthGuard.MAX_DEPTH)));
            fail("expected DepthExceededException");
        } catch (DecodeDepthGuard.DepthExceededException expected) {
            // good
        }
    }

    /**
     * The board's exact repro shape: a ~20,000-deep chain of nested
     * single-element {@code Object[]} wrappers, well under {@code
     * MAX_PAYLOAD_LEN} (a few hundred KB). Hand-crafted bytes -- see class
     * javadoc for why.
     */
    @Test(timeout = 5_000)
    public void twentyThousandDeepArrayChain_rejectedFast() throws Exception {
        byte[] payload = craftArrayChainBytes(20_000);
        assertTrue("sanity: payload must stay well under the frame ceiling",
                payload.length < 1_000_000);
        long start = System.nanoTime();
        try {
            DecodeDepthGuard.bestEffortCheck(payload);
            fail("expected DepthExceededException");
        } catch (DecodeDepthGuard.DepthExceededException expected) {
            assertTrue(expected.getMessage().contains(Integer.toString(DecodeDepthGuard.MAX_DEPTH)));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("must reject in O(MAX_DEPTH), not proportional to the attacker's chain"
                + " length -- took " + elapsedMs + "ms", elapsedMs < 500);
    }

    /**
     * The same depth attack via {@code TC_OBJECT} field recursion (a linked
     * chain of single-field wrapper objects) rather than {@code TC_ARRAY} --
     * proves the guard walks declared field tables generically, not only
     * arrays. Hand-crafted bytes (see class javadoc).
     */
    @Test(timeout = 5_000)
    public void twentyThousandDeepObjectFieldChain_rejectedFast() throws Exception {
        byte[] payload = craftObjectChainBytes(20_000);
        long start = System.nanoTime();
        try {
            DecodeDepthGuard.bestEffortCheck(payload);
            fail("expected DepthExceededException");
        } catch (DecodeDepthGuard.DepthExceededException expected) {
            assertTrue(expected.getMessage().contains(Integer.toString(DecodeDepthGuard.MAX_DEPTH)));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("must reject in O(MAX_DEPTH) -- took " + elapsedMs + "ms", elapsedMs < 500);
    }

    @Test(timeout = 5_000)
    public void shallowObjectFieldChain_notRejected() throws Exception {
        DecodeDepthGuard.bestEffortCheck(craftObjectChainBytes(5));
    }

    @Test(timeout = 5_000)
    public void emptyArray_notRejected() throws Exception {
        DecodeDepthGuard.bestEffortCheck(marshal(new Object[0]));
    }

    @Test(timeout = 5_000)
    public void nullTopLevel_notRejected() throws Exception {
        DecodeDepthGuard.bestEffortCheck(marshal(null));
    }

    @Test(timeout = 5_000)
    public void plainString_notRejected() throws Exception {
        DecodeDepthGuard.bestEffortCheck(marshal("hello"));
    }

    @Test(timeout = 5_000)
    public void primitiveByteArray_notRejected() throws Exception {
        // A large primitive-component array must never be walked element-by-element
        // (it isn't -- primitive arrays are packed raw bytes with no per-element
        // tags/recursion) and must not be mistaken for a deep structure.
        byte[] big = new byte[500_000];
        DecodeDepthGuard.bestEffortCheck(marshal(big));
    }

    @Test(timeout = 5_000)
    public void wideNotDeepArray_notRejected() throws Exception {
        // Many SIBLING elements (breadth), not nested (depth) -- must not
        // trip the depth ceiling; only actual nesting should.
        Object[] wide = new Object[5000];
        for (int i = 0; i < wide.length; i++) wide[i] = "s" + i;
        DecodeDepthGuard.bestEffortCheck(marshal(wide));
    }

    /** Externalizable content must never cause a false-positive rejection
     *  (this scanner deliberately does not fully model it -- see
     *  DecodeDepthGuard's javadoc -- so it must stay silent, not throw). */
    @Test(timeout = 5_000)
    public void externalizableObject_neverFalsePositive() throws Exception {
        DecodeDepthGuard.bestEffortCheck(marshal(new Probe()));
        DecodeDepthGuard.bestEffortCheck(marshal(new Object[]{ new Probe(), "tail" }));
    }

    public static final class Probe implements Externalizable {
        public Probe() { }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    // ------------------------------------------------- hand-crafted byte builders

    private static final byte TC_NULL = 0x70;
    private static final byte TC_REFERENCE = 0x71;
    private static final byte TC_CLASSDESC = 0x72;
    private static final byte TC_OBJECT = 0x73;
    private static final byte TC_STRING = 0x74;
    private static final byte TC_ARRAY = 0x75;
    private static final byte TC_ENDBLOCKDATA = 0x78;
    private static final int BASE_WIRE_HANDLE = 0x7E0000;

    private static void u8(ByteArrayOutputStream b, int v) { b.write(v & 0xff); }
    private static void u16(ByteArrayOutputStream b, int v) { u8(b, v >>> 8); u8(b, v); }
    private static void u32(ByteArrayOutputStream b, int v) {
        u8(b, v >>> 24); u8(b, v >>> 16); u8(b, v >>> 8); u8(b, v);
    }
    private static void utf(ByteArrayOutputStream b, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        u16(b, bytes.length);
        b.write(bytes, 0, bytes.length);
    }

    /**
     * Builds a raw {@code N}-level nested single-element {@code Object[]}
     * chain -- {@code Object[]{ Object[]{ ... Object[]{ null } ... } } }' --
     * directly as wire bytes, without recursively invoking the real
     * encoder.
     */
    private static byte[] craftArrayChainBytes(int levels) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u32(b, 0xACED0005); // STREAM_MAGIC + STREAM_VERSION
        u8(b, TC_ARRAY);
        u8(b, TC_CLASSDESC);
        utf(b, "[Ljava.lang.Object;");
        for (int i = 0; i < 8; i++) u8(b, 0); // serialVersionUID (content irrelevant to the scanner)
        u8(b, 0x00); // flags (arrays: no SC_SERIALIZABLE/SC_WRITE_METHOD bits needed)
        u16(b, 0);   // numFields = 0
        u8(b, TC_ENDBLOCKDATA);
        u8(b, TC_NULL); // superClassDesc = null
        // classDesc handle now BASE_WIRE_HANDLE; array instance handle BASE_WIRE_HANDLE+1
        u32(b, 1); // size = 1
        for (int level = 1; level < levels; level++) {
            u8(b, TC_ARRAY);
            u8(b, TC_REFERENCE);
            u32(b, BASE_WIRE_HANDLE); // reuse the one classDesc
            u32(b, 1); // size = 1
        }
        u8(b, TC_NULL); // innermost element
        return b.toByteArray();
    }

    /**
     * Builds a raw {@code N}-level linked chain of single-field wrapper
     * objects -- {@code Wrapper{next=Wrapper{next=...next=null...}}} --
     * directly as wire bytes: {@code TC_OBJECT} + a one-field ({@code
     * "next"}, object-typed) {@code SC_SERIALIZABLE} class descriptor,
     * reused by reference at every level after the first.
     */
    private static byte[] craftObjectChainBytes(int levels) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u32(b, 0xACED0005);
        u8(b, TC_OBJECT);
        u8(b, TC_CLASSDESC);
        utf(b, "Wrapper");
        for (int i = 0; i < 8; i++) u8(b, 0); // serialVersionUID
        u8(b, 0x02); // SC_SERIALIZABLE, no write method
        u16(b, 1);   // numFields = 1
        u8(b, 'L');  // object-typed field
        utf(b, "next");
        u8(b, TC_STRING); // field type name, itself a handle-bearing value
        utf(b, "Ljava/lang/Object;");
        u8(b, TC_ENDBLOCKDATA);
        u8(b, TC_NULL); // superClassDesc = null
        // Handles consumed so far: classDesc(base+0), field-type string(base+1).
        // Object instance handles start at base+2, one per TC_OBJECT.
        for (int level = 1; level < levels; level++) {
            u8(b, TC_OBJECT);
            u8(b, TC_REFERENCE);
            u32(b, BASE_WIRE_HANDLE); // reuse the one classDesc
        }
        u8(b, TC_NULL); // innermost "next" value
        return b.toByteArray();
    }
}
