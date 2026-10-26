/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.der.stream;

import java.util.Arrays;

/**
 * A hash key over the content of a whole {@code byte[]}, for the dedup tables of
 * {@link StreamSchemaDedup}.
 *
 * <p>These tables were keyed by {@link java.nio.ByteBuffer} views of the arrays.
 * {@code ByteBuffer.hashCode()} reads one byte at a time through the buffer's bounds
 * checks, which cost about a tenth of unmarshalling a batch of small objects (every chain
 * reference hashed its 32-byte digest, and every nested record hashed its whole chain).
 * Here the hash is {@link Arrays#hashCode(byte[])} (a vectorised intrinsic) or, for a
 * SHA-256 digest that is already uniformly distributed, a fold of its first eight bytes,
 * computed once; equality is {@link Arrays#equals(byte[], byte[])}, a full content
 * comparison, so a hash match is never taken for equality.
 *
 * <p>The array is not copied and must not be modified while the key is in a map. A
 * given map uses one of the two factories throughout: keys made by different factories
 * for equal content have different hashes.
 */
final class ByteArrayKey {

    private final byte[] bytes;
    private final int hash;

    private ByteArrayKey(byte[] bytes, int hash) {
        this.bytes = bytes;
        this.hash = hash;
    }

    /** A key for arbitrary bytes (a schema chain): the hash is {@link Arrays#hashCode(byte[])}. */
    static ByteArrayKey ofBytes(byte[] bytes) {
        return new ByteArrayKey(bytes, Arrays.hashCode(bytes));
    }

    /**
     * A key for a cryptographic digest: the hash is the first eight bytes folded to an
     * {@code int}. Digests shorter than eight bytes (never produced here) fall back to
     * {@link #ofBytes}.
     */
    static ByteArrayKey ofDigest(byte[] digest) {
        if (digest.length < 8) {
            return ofBytes(digest);
        }
        int h = ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16) | ((digest[2] & 0xFF) << 8) | (digest[3] & 0xFF);
        int l = ((digest[4] & 0xFF) << 24) | ((digest[5] & 0xFF) << 16) | ((digest[6] & 0xFF) << 8) | (digest[7] & 0xFF);
        return new ByteArrayKey(digest, h ^ l);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof ByteArrayKey k && k.hash == hash && Arrays.equals(bytes, k.bytes));
    }
}
