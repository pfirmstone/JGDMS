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

import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adversarial-board-required fix (2026-07-20 board review of T4): a
 * codec-level, structure-aware pre-scan that bounds decode-recursion depth
 * on the wire-handoff decode surface -- {@code AtomicMarshalInputStream} /
 * {@code ObjOutputStream} (the codec this wire path actually uses, distinct
 * from the DER {@code Any}/collection codec, which has its own, unrelated
 * depth counter) has <strong>no nesting-depth ceiling at all</strong>. A
 * ~20,000-deep nested single-element {@code Object[]} chain (a few hundred
 * KB, trivially under the 64&nbsp;MiB frame ceiling) drives a live {@code
 * StackOverflowError} deep inside that class's recursive {@code readObject}/
 * {@code readNewArray}/{@code readNewObject} call chain.
 *
 * <h2>What this class guarantees, precisely</h2>
 * {@link #bestEffortCheck(byte[])} walks the standard Java object
 * serialization stream tag protocol (the same
 * {@code java.io.ObjectStreamConstants} tag values {@code
 * AtomicMarshalInputStream} itself uses -- verified empirically against this
 * codec's own encoder output, not assumed) by hand, counting recursive
 * nesting (array elements, object fields, class-hierarchy chains) as it
 * goes. If it can <em>fully and confidently</em> parse a structure whose
 * nesting exceeds {@link #MAX_DEPTH}, it throws -- a cheap, clean rejection
 * that costs at most {@code O(MAX_DEPTH)} work, long before the real decoder
 * would ever be invoked.
 *
 * <p><strong>This is a best-effort, defence-in-depth pre-check, not a
 * complete parser, and it must never be treated as a safety proof on its
 * own.</strong> Some shapes are not fully bounded here on purpose: {@code
 * Externalizable} content is only self-describing by convention (the
 * writer/reader agree out-of-band on what {@code writeExternal}/{@code
 * readExternal} exchange -- there is no grammar-level guarantee it is pure
 * block data), and dynamic-proxy class descriptors ({@code
 * TC_PROXYCLASSDESC}) are deliberately not modelled, to avoid any risk of
 * this scanner mis-parsing a legitimate {@code CodebaseAccessor} stub or a
 * genuine business {@code Externalizable} argument and rejecting valid
 * traffic (a false positive here would be a functional regression, not just
 * a missed catch). When this scanner cannot fully account for a shape, it
 * returns <strong>silently and without complaint</strong> -- it claims
 * <em>no</em> safety guarantee for that payload, and the caller's own
 * bounded, catch-{@code Throwable} decode path (see {@code
 * SubProcessWireHandoffImpl.ForwardingInvocationHandler#invoke} and {@code
 * SubProcessReconstructionServer#dispatchInvoke}) is the unconditional
 * backstop regardless of what this scanner did or did not confirm.
 *
 * @since 3.1.1
 */
final class DecodeDepthGuard {

    /**
     * Generous for any realistic business object graph (which in practice is
     * essentially never more than a few dozen levels deep), while cheap
     * enough that an adversarial chain is rejected in O(MAX_DEPTH) -- a
     * handful of microseconds -- long before the real, expensive/dangerous
     * recursive decode would ever run.
     */
    static final int MAX_DEPTH = 64;

    private static final byte TC_NULL = 0x70;
    private static final byte TC_REFERENCE = 0x71;
    private static final byte TC_CLASSDESC = 0x72;
    private static final byte TC_OBJECT = 0x73;
    private static final byte TC_STRING = 0x74;
    private static final byte TC_ARRAY = 0x75;
    private static final byte TC_CLASS = 0x76;
    private static final byte TC_BLOCKDATA = 0x77;
    private static final byte TC_ENDBLOCKDATA = 0x78;
    private static final byte TC_RESET = 0x79;
    private static final byte TC_BLOCKDATALONG = 0x7A;
    private static final byte TC_EXCEPTION = 0x7B;
    private static final byte TC_LONGSTRING = 0x7C;
    private static final byte TC_PROXYCLASSDESC = 0x7D;
    private static final byte TC_ENUM = 0x7E;

    private static final int SC_WRITE_METHOD = 0x01;
    private static final int SC_SERIALIZABLE = 0x02;
    private static final int SC_EXTERNALIZABLE = 0x04;

    private DecodeDepthGuard() { }

    /** Thrown ONLY when a fully, confidently parsed structure exceeds {@link #MAX_DEPTH}. */
    static final class DepthExceededException extends IOException {
        DepthExceededException(String message) {
            super(message);
        }
    }

    /** Internal signal: "this scanner does not fully model this shape" -- never a rejection. */
    private static final class Inconclusive extends RuntimeException {
        Inconclusive(String message) {
            super(message);
        }
    }

    /**
     * Pre-scans one independently-marshalled wire-handoff field (a
     * {@code AtomicMarshalOutputStream}-produced byte array: {@code
     * STREAM_MAGIC}+{@code STREAM_VERSION} header followed by exactly one
     * top-level value, exactly the shape {@code WireHandoffCodec}'s
     * per-field sub-streams produce).
     *
     * @throws DepthExceededException if a confidently-parsed structure's
     *         nesting exceeds {@link #MAX_DEPTH}
     */
    static void bestEffortCheck(byte[] payload) throws DepthExceededException {
        try {
            new Walker(payload).run();
        } catch (DepthExceededException e) {
            throw e;
        } catch (RuntimeException | IOException e) {
            // Inconclusive (malformed-looking to this scanner, truncated, or
            // a shape it deliberately does not model). Never rejected here
            // -- see class javadoc: this is a best-effort optimisation, not
            // the safety boundary.
        }
    }

    private static final class ClassDescInfo {
        final int flags;
        final String className;
        final List<Character> fieldTypeCodes = new ArrayList<Character>();
        ClassDescInfo superDesc;

        ClassDescInfo(int flags, String className) {
            this.flags = flags;
            this.className = className;
        }
    }

    /**
     * The standard {@code java.io.ObjectStreamConstants.baseWireHandle}
     * (0x7E0000): the stream protocol's handle numbering starts here, not at
     * 0 -- confirmed empirically against this codec's own encoder output (a
     * {@code TC_REFERENCE}'s 4-byte value for the first-assigned handle is
     * {@code 0x007E0000}). Handle bookkeeping below must use the same base,
     * or every {@code TC_REFERENCE} lookup misses and this scanner silently
     * gives up (fail-safe, but pointless) on the second occurrence of any
     * repeated type -- exactly the case a nested-array chain hits at its
     * second level onward.
     */
    private static final int BASE_WIRE_HANDLE = 0x7E0000;

    private static final class Walker {
        private final byte[] buf;
        private int pos;
        private int nextHandleValue = BASE_WIRE_HANDLE;
        private final Map<Integer, ClassDescInfo> classDescByHandle =
                new HashMap<Integer, ClassDescInfo>();
        private int depth;

        Walker(byte[] buf) {
            this.buf = buf;
        }

        void run() throws IOException {
            if (buf.length < 4) return; // too short to contain anything recursive
            pos = 4; // skip STREAM_MAGIC(2) + STREAM_VERSION(2)
            if (pos >= buf.length) return; // no content (shouldn't happen, but nothing to check)
            readValue();
        }

        // -------------------------------------------------------- primitives

        private int u8() throws IOException {
            if (pos >= buf.length) throw new EOFException("truncated");
            return buf[pos++] & 0xff;
        }

        private byte tagByte() throws IOException {
            if (pos >= buf.length) throw new EOFException("truncated");
            return buf[pos++];
        }

        private int u16() throws IOException {
            int hi = u8();
            int lo = u8();
            return (hi << 8) | lo;
        }

        private long u32() throws IOException {
            long v = 0;
            for (int i = 0; i < 4; i++) v = (v << 8) | u8();
            return v;
        }

        private void skip(long n) throws IOException {
            if (n < 0 || pos + n > buf.length) {
                throw new EOFException("truncated (skip " + n + " at " + pos + ")");
            }
            pos += n;
        }

        private String utf() throws IOException {
            int len = u16();
            if (pos + len > buf.length) throw new EOFException("truncated utf");
            String s = new String(buf, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        private int nextHandle() {
            return nextHandleValue++;
        }

        // ------------------------------------------------------------ value

        /**
         * Reads exactly one tagged value, incrementing/decrementing the
         * shared depth counter around the whole call -- so every recursive
         * descent (array element, object field, classDesc superclass link,
         * enum constant name) is counted uniformly, matching one level of
         * real recursive-call-stack depth in the actual decoder.
         */
        private void readValue() throws IOException {
            depth++;
            if (depth > MAX_DEPTH) {
                throw new DepthExceededException(
                        "wire-handoff decode nesting depth exceeds " + MAX_DEPTH
                        + " -- refusing before attempting the real recursive"
                        + " decode (possible depth-based decode-DoS attempt).");
            }
            try {
                byte t = tagByte();
                switch (t) {
                    case TC_NULL:
                        return;
                    case TC_REFERENCE:
                        skip(4);
                        return;
                    case TC_STRING: {
                        int len = u16();
                        skip(len);
                        nextHandle();
                        return;
                    }
                    case TC_LONGSTRING: {
                        long len = 0;
                        for (int i = 0; i < 8; i++) len = (len << 8) | u8();
                        skip(len);
                        nextHandle();
                        return;
                    }
                    case TC_ARRAY: {
                        ClassDescInfo cd = readClassDesc();
                        nextHandle(); // the array instance itself
                        if (cd == null) throw new Inconclusive("array with null classDesc");
                        long size = u32();
                        if (size < 0) throw new Inconclusive("negative/overflowing array size");
                        if (isSingleDimPrimitiveArray(cd.className)) {
                            skip(size * primitiveWidth(cd.className.charAt(1)));
                        } else {
                            for (long i = 0; i < size; i++) readValue();
                        }
                        return;
                    }
                    case TC_ENUM: {
                        readClassDesc();
                        nextHandle();
                        readValue(); // enum constant name
                        return;
                    }
                    case TC_CLASS: {
                        readClassDesc();
                        nextHandle();
                        return;
                    }
                    case TC_OBJECT: {
                        ClassDescInfo cd = readClassDesc();
                        nextHandle();
                        if (cd == null) throw new Inconclusive("object with null classDesc");
                        readObjectContent(cd);
                        return;
                    }
                    case TC_EXCEPTION:
                        // Per the stream protocol, TC_EXCEPTION resets the
                        // handle table, then a nested exception object follows.
                        nextHandleValue = BASE_WIRE_HANDLE;
                        classDescByHandle.clear();
                        readValue();
                        return;
                    case TC_RESET:
                        nextHandleValue = BASE_WIRE_HANDLE;
                        classDescByHandle.clear();
                        return;
                    default:
                        throw new Inconclusive(
                                "unrecognised tag 0x" + Integer.toHexString(t & 0xff));
                }
            } finally {
                depth--;
            }
        }

        // --------------------------------------------------------- classDesc

        /** Reads a class-descriptor reference: {@code TC_NULL}/{@code TC_REFERENCE}/
         *  {@code TC_CLASSDESC} (never {@code TC_PROXYCLASSDESC} -- see class javadoc). */
        private ClassDescInfo readClassDesc() throws IOException {
            depth++;
            if (depth > MAX_DEPTH) {
                throw new DepthExceededException(
                        "wire-handoff decode class-hierarchy nesting depth exceeds "
                        + MAX_DEPTH + " -- refusing before the real decode.");
            }
            try {
                byte t = tagByte();
                switch (t) {
                    case TC_NULL:
                        return null;
                    case TC_REFERENCE: {
                        long h = u32();
                        ClassDescInfo cd = classDescByHandle.get(Integer.valueOf((int) h));
                        if (cd == null) {
                            throw new Inconclusive("classDesc reference to unknown handle");
                        }
                        return cd;
                    }
                    case TC_PROXYCLASSDESC:
                        // Deliberately not modelled (dynamic proxies, e.g. the
                        // CodebaseAccessor bootstrap stub) -- see class javadoc.
                        throw new Inconclusive("dynamic-proxy classDesc not modelled");
                    case TC_CLASSDESC: {
                        String className = utf();
                        skip(8); // serialVersionUID
                        int flags = u8();
                        int numFields = u16();
                        ClassDescInfo cd = new ClassDescInfo(flags, className);
                        // Handle assigned HERE, before reading field
                        // descriptors -- matches AtomicMarshalInputStream's
                        // own, explicitly-commented ordering ("We must
                        // register the class descriptor before reading
                        // field descriptors", readStreamClassDescriptor()):
                        // a field's own type-name string (for 'L'/'[' typed
                        // fields) gets the NEXT handle after the classDesc's
                        // own, not before it. Getting this order wrong means
                        // every later TC_REFERENCE back to a repeated
                        // classDesc resolves to the wrong (or no) entry,
                        // silently degrading this scanner to "give up after
                        // the second occurrence of any repeated type" --
                        // caught empirically: a hand-crafted repeated-object
                        // depth chain went unrejected at any depth until
                        // this order was corrected to match the real
                        // decoder's.
                        classDescByHandle.put(Integer.valueOf(nextHandle()), cd);
                        for (int i = 0; i < numFields; i++) {
                            char typeCode = (char) u8();
                            utf(); // field name -- not needed further
                            if (typeCode == 'L' || typeCode == '[') {
                                readValue(); // the field's type-name string (TC_STRING/TC_REFERENCE)
                            }
                            cd.fieldTypeCodes.add(Character.valueOf(typeCode));
                        }
                        readContentUntilEndBlock(); // class annotation (normally empty)
                        cd.superDesc = readClassDesc();
                        return cd;
                    }
                    default:
                        throw new Inconclusive(
                                "unexpected classDesc tag 0x" + Integer.toHexString(t & 0xff));
                }
            } finally {
                depth--;
            }
        }

        /**
         * Reads an object instance's field values (per the {@code
         * SC_SERIALIZABLE} declared field table, walked root-superclass
         * first) followed, for any hierarchy level carrying {@code
         * SC_WRITE_METHOD}/{@code SC_EXTERNALIZABLE}, by that level's
         * block-data/nested-value content up to {@code TC_ENDBLOCKDATA}.
         */
        private void readObjectContent(ClassDescInfo leaf) throws IOException {
            List<ClassDescInfo> chain = new ArrayList<ClassDescInfo>();
            for (ClassDescInfo c = leaf; c != null; c = c.superDesc) chain.add(0, c);
            for (ClassDescInfo level : chain) {
                if ((level.flags & SC_SERIALIZABLE) != 0) {
                    for (char typeCode : level.fieldTypeCodes) {
                        readFieldValue(typeCode);
                    }
                }
                if ((level.flags & (SC_WRITE_METHOD | SC_EXTERNALIZABLE)) != 0) {
                    readContentUntilEndBlock();
                }
            }
        }

        private void readFieldValue(char typeCode) throws IOException {
            switch (typeCode) {
                case 'B': case 'Z': skip(1); return;
                case 'C': case 'S': skip(2); return;
                case 'F': case 'I': skip(4); return;
                case 'D': case 'J': skip(8); return;
                case 'L': case '[': readValue(); return;
                default:
                    throw new Inconclusive("unknown field type code '" + typeCode + "'");
            }
        }

        /** Reads {@code TC_BLOCKDATA}/{@code TC_BLOCKDATALONG} chunks (skippable) interspersed
         *  with tagged values (recursed), until {@code TC_ENDBLOCKDATA}. */
        private void readContentUntilEndBlock() throws IOException {
            for (;;) {
                if (pos >= buf.length) throw new EOFException("truncated block content");
                byte peek = buf[pos];
                if (peek == TC_ENDBLOCKDATA) {
                    pos++;
                    return;
                }
                if (peek == TC_BLOCKDATA) {
                    pos++;
                    int len = u8();
                    skip(len);
                    continue;
                }
                if (peek == TC_BLOCKDATALONG) {
                    pos++;
                    long len = u32();
                    skip(len);
                    continue;
                }
                readValue();
            }
        }

        private static boolean isSingleDimPrimitiveArray(String className) {
            return className != null && className.length() == 2
                    && className.charAt(0) == '['
                    && "BCDFIJSZ".indexOf(className.charAt(1)) >= 0;
        }

        private static int primitiveWidth(char typeCode) {
            switch (typeCode) {
                case 'B': case 'Z': return 1;
                case 'C': case 'S': return 2;
                case 'F': case 'I': return 4;
                case 'D': case 'J': return 8;
                default: throw new Inconclusive("unknown primitive type code '" + typeCode + "'");
            }
        }
    }
}
