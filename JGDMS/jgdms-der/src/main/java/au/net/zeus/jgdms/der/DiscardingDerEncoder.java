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
import java.util.Objects;

/**
 * A {@link DerEncoder} that keeps nothing: it follows the same call protocol (balanced {@code begin}/{@code end}, the same
 * argument checks, a failure latches the encoder) and copies no content. It is the output of a pass that exists only to prove a
 * stream admissible, whose result is thrown away (whole-stream admission, SOW-DER-Streaming-Encoder B.2.2: "without
 * materialising reconstituted bytes"): copying a large field into a buffer that grows by doubling, to discard it, was most of the
 * cost of admitting a 64 KiB {@code byte[]}.
 *
 * <p>Because it has no output, {@link #toByteArray()} and {@link #size()} are unsupported, and so are the sorted constructs,
 * which exist to order bytes: a verifying walk that came to need either would fail loudly here rather than silently verify
 * less than the delivery walk does. Not thread-safe; single use between {@link #reset()}s.
 */
final class DiscardingDerEncoder extends DerEncoder {

    private int depth;
    private boolean failed;
    private long discarded;

    @Override
    public DerEncoder reset() {
        depth = 0;
        failed = false;
        discarded = 0;
        return this;
    }

    @Override
    public DerEncoder begin(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.isConstructed()) {
            throw new IllegalArgumentException("begin() needs a constructed tag; use beginOctetString() for OCTET STRING: " + tag);
        }
        return open();
    }

    @Override
    public DerEncoder beginOctetString() {
        return open();
    }

    private DerEncoder open() {
        checkUsable();
        depth++;
        return this;
    }

    @Override
    public DerEncoder beginSortedSet(Comparator<byte[]> order) {
        throw new UnsupportedOperationException("a discarding encoder has no output to order");
    }

    @Override
    public DerEncoder beginSortedMap() {
        throw new UnsupportedOperationException("a discarding encoder has no output to order");
    }

    @Override
    public DerEncoder end() {
        checkUsable();
        if (depth == 0) {
            failed = true;
            throw new IllegalStateException("end() with no open construct");
        }
        depth--;
        return this;
    }

    @Override
    public DerEncoder leaf(Tag tag, byte[] content, int off, int len) {
        checkUsable();
        try {
            Objects.requireNonNull(tag, "tag");
            Objects.checkFromIndexSize(off, len, content.length);
            discarded += len;
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
            Objects.checkFromIndexSize(off, len, tlv.length);
            discarded += len;
            return this;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public boolean isClean() {
        return !failed && depth == 0;
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("a discarding encoder holds no encoding");
    }

    @Override
    public byte[] toByteArray() {
        throw new UnsupportedOperationException("a discarding encoder holds no encoding");
    }

    /** Content octets handed to {@code leaf} and {@code raw} since the last {@link #reset()} (for tests and diagnostics). */
    long discardedBytes() {
        return discarded;
    }

    private void checkUsable() {
        if (failed) {
            throw new IllegalStateException("encoder failed earlier; reset() it");
        }
    }
}
