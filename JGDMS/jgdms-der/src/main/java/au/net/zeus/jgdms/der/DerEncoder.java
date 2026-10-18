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

package au.net.zeus.jgdms.der;

import java.util.Comparator;

/**
 * A single-use, single-threaded DER encoder that builds nested TLVs into ONE growing buffer instead
 * of returning a fresh {@code byte[]} per level (JGDMS-STD-006; SOW-DER-Streaming-Encoder task A1).
 * Output is byte-identical to composing the same structure with {@link DerWriter}: definite,
 * minimal-length encodings, children in the order written.
 *
 * <pre>{@code
 * DerEncoder e = DerEncoder.create();
 * e.begin(Tag.SEQUENCE);
 *   e.leaf(Tag.INTEGER, new byte[] {7});
 *   e.beginSortedSet(CollectionWireTypes.OCTET_SORT);   // SET OF, children octet-sorted (X.690 11.6)
 *     e.leaf(Tag.OCTET_STRING, a);
 *     e.leaf(Tag.OCTET_STRING, b);
 *   e.end();
 * e.end();
 * byte[] der = e.toByteArray();
 * }</pre>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #leaf} and {@link #raw} COPY the bytes they are given; the caller may reuse or mutate
 *       its array immediately. The encoder never exposes its internal storage;
 *       {@link #toByteArray()} returns a fresh array.</li>
 *   <li>Inside {@link #beginSortedSet} every direct child must be exactly ONE TLV: a {@code leaf},
 *       a {@code raw} holding a single complete TLV, or one {@code begin}..{@code end} construct.
 *       The children are sorted with the comparator supplied (the project's
 *       {@code CollectionWireTypes.OCTET_SORT}, passed in so this package does not depend on
 *       {@code getarg}); equal encodings are kept, in the order written.</li>
 *   <li>Nested sorted sets are supported to any depth: each sorted set encodes its elements in its
 *       own scratch encoder, so an inner set can never overwrite the element an outer set is still
 *       building (board review finding F3).</li>
 *   <li>Misuse (unbalanced {@code end}, {@code toByteArray} with an open construct) throws
 *       {@link IllegalStateException}.</li>
 * </ul>
 *
 * <p>Not thread-safe.
 */
public abstract class DerEncoder {

    DerEncoder() {}

    /**
     * Creates an encoder. The implementation is "reserve a worst-case length field per construct,
     * compact once at the end", chosen over a two-pass tree encoder by the A1 benchmark (64 KiB blob
     * nested 7 deep: 13.7 us and 65 592 B allocated per operation reused, against 83.8 us and 524 624 B
     * for today's {@link DerWriter} composition).
     */
    public static DerEncoder create() {
        return new CompactDerEncoder();
    }

    /**
     * Discards everything written and any open constructs, keeping the allocated buffers, so one
     * encoder can be reused for the next item. This is what makes the allocation figure above hold.
     */
    public abstract DerEncoder reset();

    /** Opens a constructed TLV with {@code tag}; its content is whatever is written until {@link #end()}. */
    public abstract DerEncoder begin(Tag tag);

    /** Opens a SET OF (tag {@code 0x31}) whose children are sorted by {@code order} when it is ended. */
    public abstract DerEncoder beginSortedSet(Comparator<byte[]> order);

    /** Closes the innermost open construct. */
    public abstract DerEncoder end();

    /** Writes one primitive (or pre-built content) TLV: {@code tag}, minimal length, then {@code len} bytes. */
    public abstract DerEncoder leaf(Tag tag, byte[] content, int off, int len);

    /** Appends {@code len} bytes of an already-encoded TLV verbatim (not validated). */
    public abstract DerEncoder raw(byte[] tlv, int off, int len);

    /** The encoding of everything written; requires every construct to be closed. */
    public abstract byte[] toByteArray();

    /** Bytes of encoding produced so far if it were finished now; requires every construct to be closed. */
    public abstract int size();

    public final DerEncoder leaf(Tag tag, byte[] content) {
        return leaf(tag, content, 0, content.length);
    }

    public final DerEncoder raw(byte[] tlv) {
        return raw(tlv, 0, tlv.length);
    }
}
