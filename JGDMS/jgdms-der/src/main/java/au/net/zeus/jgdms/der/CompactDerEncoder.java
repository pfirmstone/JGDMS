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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * {@link DerEncoder} variant (c): reserve a worst-case length field for each construct, write the
 * content straight after it, patch the real tag and length into the reserved space when the
 * construct ends, and close the leftover slack in ONE left-to-right pass at {@link #toByteArray()}.
 * Every byte therefore moves at most once, however deeply it is nested (the earlier
 * "reserve one octet and shift" idea moved a construct's whole content at every ancestor).
 */
final class CompactDerEncoder extends DerEncoder {

    /** Length-field octets reserved per construct: {@code 0x84 + 4} covers any {@code int} length. */
    private static final int RESERVED_LENGTH = 5;

    private byte[] buf = new byte[256];
    private int pos;

    // frames, in BEGIN order (= buffer-offset order): where each header starts, the tag, and, once the
    // frame has ended, how many reserved octets were not needed
    private int frames;
    private int[] hdrStart = new int[16];
    private int[] hdrLen = new int[16];
    private int[] slack = new int[16];
    private long[] slackAtBegin = new long[16];   // totalSlack when the frame began
    private Tag[] tags = new Tag[16];

    // currently open frames (indexes into the arrays above)
    private int[] open = new int[16];
    private int depth;
    private long totalSlack;

    /** Non-null while the innermost open construct is a sorted set: all writes go through it. */
    private Sorted sorted;

    /** Set once any operation throws: the encoder may hold a half-built construct, so it refuses further use. */
    private boolean failed;

    /** State of a sorted set under construction, with its own scratch encoder. */
    private static final class Sorted {
        final Comparator<byte[]> order;       // null for a keyed (map) set
        final boolean keyed;                   // elements are SEQUENCE { key, value }, ordered by the key TLV only
        final ArrayList<byte[]> elements = new ArrayList<>();
        final CompactDerEncoder scratch = new CompactDerEncoder();
        int scratchDepth;   // open constructs inside the element currently being built
        Sorted(Comparator<byte[]> order, boolean keyed) { this.order = order; this.keyed = keyed; }
    }

    // ------------------------------------------------------------------ public API

    @Override
    public DerEncoder begin(Tag tag) {
        if (!tag.isConstructed()) {
            throw new IllegalArgumentException("begin() needs a constructed tag; use beginOctetString() for OCTET STRING: " + tag);
        }
        return open(tag);
    }

    @Override
    public DerEncoder beginOctetString() {
        return open(Tag.OCTET_STRING);
    }

    private DerEncoder open(Tag tag) {
        checkUsable();
        try {
            if (sorted != null) {
                sorted.scratch.open(tag);
                sorted.scratchDepth++;
                return this;
            }
            beginFrame(tag);
            return this;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public DerEncoder beginSortedSet(Comparator<byte[]> order) {
        if (order == null) throw new NullPointerException("order");
        return openSorted(order, false);
    }

    @Override
    public DerEncoder beginSortedMap() {
        return openSorted(null, true);
    }

    private DerEncoder openSorted(Comparator<byte[]> order, boolean keyed) {
        checkUsable();
        try {
            if (sorted != null) {
                // a sorted set as an ELEMENT of this sorted set: the scratch encoder owns it (and its own scratch)
                sorted.scratch.openSorted(order, keyed);
                sorted.scratchDepth++;
                return this;
            }
            sorted = new Sorted(order, keyed);
            return this;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public DerEncoder end() {
        checkUsable();
        try {
            if (sorted != null) {
                if (sorted.scratchDepth > 0) {
                    sorted.scratch.end();
                    if (--sorted.scratchDepth == 0) {
                        sorted.elements.add(sorted.scratch.toByteArray());
                        sorted.scratch.reset();
                    }
                    return this;
                }
                // close the sorted set itself: sort, then write it as an ordinary SET frame
                Sorted s = sorted;
                sorted = null;
                if (s.keyed) {
                    sortByKey(s.elements);
                } else {
                    s.elements.sort(s.order);
                }
                beginFrame(SET);
                for (byte[] e : s.elements) putBytes(e, 0, e.length);
                endFrame();
                return this;
            }
            if (depth == 0) throw new IllegalStateException("end() with no open construct");
            endFrame();
            return this;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public DerEncoder leaf(Tag tag, byte[] content, int off, int len) {
        checkUsable();
        try {
            if (sorted != null) {
                if (sorted.scratchDepth > 0) {
                    sorted.scratch.leaf(tag, content, off, len);
                } else {
                    CompactDerEncoder one = sorted.scratch;
                    one.leaf(tag, content, off, len);
                    sorted.elements.add(one.toByteArray());
                    one.reset();
                }
                return this;
            }
            int total = Math.addExact(DerWriter.headerSize(tag, len), len);
            ensure(total);
            int p = DerWriter.writeHeader(buf, pos, tag, len);
            System.arraycopy(content, off, buf, p, len);
            pos += total;
            return this;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public DerEncoder raw(byte[] tlv, int off, int len) {
        checkUsable();
        try {
            if (sorted != null) {
                if (sorted.scratchDepth > 0) {
                    sorted.scratch.raw(tlv, off, len);
                } else {
                    // a direct child of a sorted set is one element: it must be exactly one TLV, or the
                    // sort (and the framing of the elements after it) would not mean what the bytes say
                    if (tlvEnd(tlv, off, off + len) != off + len) {
                        throw new IllegalArgumentException("a sorted-set element must be exactly one TLV");
                    }
                    sorted.elements.add(Arrays.copyOfRange(tlv, off, off + len));
                }
                return this;
            }
            putBytes(tlv, off, len);
            return this;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public boolean isClean() {
        return !failed && depth == 0 && sorted == null;
    }

    @Override
    public int size() {
        checkUsable();
        requireClosed();
        return (int) (pos - totalSlack);
    }

    @Override
    public byte[] toByteArray() {
        checkUsable();
        requireClosed();
        byte[] out = new byte[(int) (pos - totalSlack)];
        int src = 0, dst = 0;
        for (int f = 0; f < frames; f++) {
            int headerEnd = hdrStart[f] + hdrLen[f];
            int n = headerEnd - src;
            System.arraycopy(buf, src, out, dst, n);
            dst += n;
            src = headerEnd + slack[f];
        }
        System.arraycopy(buf, src, out, dst, pos - src);
        return out;
    }

    // ------------------------------------------------------------------ internals

    private static final Tag SET = new Tag(Tag.CLASS_UNIVERSAL, true, 17);

    private void checkUsable() {
        if (failed) {
            throw new IllegalStateException("encoder failed earlier and holds a partial encoding; reset() it");
        }
    }

    /**
     * End offset of the TLV that starts at {@code off} and must lie within {@code [off, limit)}.
     *
     * @throws IllegalArgumentException if it is not one well-formed definite-length TLV that fits
     */
    static int tlvEnd(byte[] b, int off, int limit) {
        int p = off;
        if (p >= limit) throw new IllegalArgumentException("empty TLV");
        if ((b[p++] & 0x1F) == 0x1F) {
            int n = 0;
            do {
                if (p >= limit || ++n > 5) throw new IllegalArgumentException("bad tag");
            } while ((b[p++] & 0x80) != 0);
        }
        return tlvContentEnd(b, p, limit);
    }

    /** Given the offset just past a tag, returns the end offset of the TLV (parses the length octets). */
    private static int tlvContentEnd(byte[] b, int p, int limit) {
        if (p >= limit) throw new IllegalArgumentException("missing length");
        int l = b[p++] & 0xFF;
        long len;
        if (l < 0x80) {
            len = l;
        } else {
            int n = l & 0x7F;
            if (n == 0 || n > 4 || p + n > limit) throw new IllegalArgumentException("bad length");
            len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (b[p++] & 0xFF);
        }
        if (len > limit - p) throw new IllegalArgumentException("TLV overruns its container");
        return (int) (p + len);
    }

    /** Offset of the first content octet of the TLV starting at {@code off}. */
    private static int contentStart(byte[] b, int off, int limit) {
        int p = off;
        if ((b[p++] & 0x1F) == 0x1F) {
            while ((b[p++] & 0x80) != 0) { /* tlvEnd has already bounded this */ }
        }
        int l = b[p++] & 0xFF;
        return l < 0x80 ? p : p + (l & 0x7F);
    }

    /**
     * Orders map entries {@code SEQUENCE { key, value }} by the unsigned octets of the KEY TLV alone
     * (X.690 11.6 over the key encodings). Complete TLVs are prefix-free, so this equals
     * {@code CollectionWireTypes.compareOctets} on the keys; the value never takes part. The key extent
     * is found once per entry, not per comparison. Ties keep insertion order (stable).
     */
    private static void sortByKey(ArrayList<byte[]> entries) {
        int n = entries.size();
        int[] ks = new int[n];
        int[] ke = new int[n];
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            byte[] e = entries.get(i);
            int entryEnd = tlvEnd(e, 0, e.length);
            if (entryEnd != e.length) throw new IllegalArgumentException("map entry is not exactly one TLV");
            ks[i] = contentStart(e, 0, e.length);
            ke[i] = tlvEnd(e, ks[i], e.length);
            idx[i] = i;
        }
        Arrays.sort(idx, (x, y) -> Arrays.compareUnsigned(
                entries.get(x), ks[x], ke[x], entries.get(y), ks[y], ke[y]));
        ArrayList<byte[]> sortedEntries = new ArrayList<>(n);
        for (int i : idx) sortedEntries.add(entries.get(i));
        entries.clear();
        entries.addAll(sortedEntries);
    }

    private void requireClosed() {
        if (depth != 0 || sorted != null) {
            throw new IllegalStateException("a construct is still open");
        }
    }

    @Override
    public DerEncoder reset() {
        Arrays.fill(tags, 0, frames, null);   // only frames that were begun can hold a tag
        pos = 0;
        frames = 0;
        depth = 0;
        totalSlack = 0;
        sorted = null;
        failed = false;
        return this;
    }

    private void beginFrame(Tag tag) {
        int reserved = tag.encodedSize() + RESERVED_LENGTH;
        ensure(reserved);
        if (frames == hdrStart.length) growFrames();
        if (depth == open.length) open = Arrays.copyOf(open, depth * 2);
        int f = frames++;
        hdrStart[f] = pos;
        slackAtBegin[f] = totalSlack;
        tags[f] = tag;
        open[depth++] = f;
        pos += reserved;
    }

    private void endFrame() {
        int f = open[--depth];
        Tag tag = tags[f];
        int reserved = tag.encodedSize() + RESERVED_LENGTH;
        int contentStart = hdrStart[f] + reserved;
        // the raw buffer still contains the unused reserved slack of every construct nested inside this
        // one; the real content length excludes it (compaction removes it in toByteArray)
        int contentLen = (int) (pos - contentStart - (totalSlack - slackAtBegin[f]));
        int actual = DerWriter.headerSize(tag, contentLen);
        DerWriter.writeHeader(buf, hdrStart[f], tag, contentLen);
        hdrLen[f] = actual;
        slack[f] = reserved - actual;
        totalSlack += slack[f];
        tags[f] = null;
    }

    private void putBytes(byte[] src, int off, int len) {
        ensure(len);
        System.arraycopy(src, off, buf, pos, len);
        pos += len;
    }

    private void ensure(int extra) {
        long need = (long) pos + extra;
        if (need > Integer.MAX_VALUE - 16) {
            throw new IllegalStateException("DER encoding too large");
        }
        if (need > buf.length) {
            buf = Arrays.copyOf(buf, (int) Math.max(need, Math.min((long) buf.length * 2, Integer.MAX_VALUE - 16)));
        }
    }

    private void growFrames() {
        int n = hdrStart.length * 2;
        hdrStart = Arrays.copyOf(hdrStart, n);
        hdrLen = Arrays.copyOf(hdrLen, n);
        slack = Arrays.copyOf(slack, n);
        slackAtBegin = Arrays.copyOf(slackAtBegin, n);
        tags = Arrays.copyOf(tags, n);
    }
}
