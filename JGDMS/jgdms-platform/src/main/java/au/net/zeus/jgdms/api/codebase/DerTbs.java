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
package au.net.zeus.jgdms.api.codebase;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal, stateless canonical-DER (X.690 Distinguished Encoding Rules) TLV
 * encoder used to build the <em>to-be-signed</em> (TBS) content of the signed
 * SCAP records (JGDMS-STD-006 &sect;7.4 / &sect;7.4.1):
 * {@link JarAnalysisReport}, {@link RegistryVerdict}, and {@link CrashReport}.
 *
 * <p>Each signed record's signature input is the canonical DER of its signed
 * fields wrapped in a {@code SEQUENCE}, with the signature field itself
 * excluded, encoded once by the signer and reconstructed identically by the
 * verifier from the received fields (&sect;7.4.1).  Because the octets are
 * signed, they must be reproducible across implementations, which is why the
 * <em>distinguished</em> (canonical) encoding is mandatory rather than BER.
 *
 * <p><strong>Why not {@code au.net.zeus.jgdms.der.DerWriter}?</strong>  The
 * {@code jgdms-der} module targets JDK&nbsp;25 only and is not a dependency of
 * {@code jgdms-platform}, whereas these SCAP records travel to broad-reach
 * clients (e.g. the preferred class loader).  This encoder is deliberately a
 * tiny, self-contained mirror of {@code DerWriter} that emits
 * <em>byte-identical</em> output, so a non-JVM participant that follows the
 * STD-006 canonical-DER rules reproduces the same signed octets.
 *
 * <p>Only the primitives the SCAP TBS forms need are provided: {@code SEQUENCE}
 * (tag {@code 0x30}), {@code UTF8String} (tag {@code 0x0C}), {@code INTEGER}
 * (tag {@code 0x02}), and {@code OCTET STRING} (tag {@code 0x04}).  All methods
 * are stateless and thread-safe; each returns a fresh, complete TLV.
 */
final class DerTbs {

    private DerTbs() {
        throw new AssertionError("no instances");
    }

    /** UNIVERSAL PRIMITIVE UTF8String, tag {@code 0x0C}. */
    private static final byte TAG_UTF8STRING  = 0x0C;
    /** UNIVERSAL PRIMITIVE INTEGER, tag {@code 0x02}. */
    private static final byte TAG_INTEGER     = 0x02;
    /** UNIVERSAL PRIMITIVE OCTET STRING, tag {@code 0x04}. */
    private static final byte TAG_OCTET_STRING = 0x04;
    /** UNIVERSAL CONSTRUCTED SEQUENCE, tag {@code 0x30}. */
    private static final byte TAG_SEQUENCE    = 0x30;

    /**
     * Encodes a Java string as a {@code UTF8String}.  The content is the UTF-8
     * bytes; the empty string is valid (zero-length content).
     *
     * @param value the string (must not be {@code null})
     * @return the DER TLV encoding
     */
    static byte[] utf8(String value) {
        if (value == null) throw new NullPointerException("value");
        return tlv(TAG_UTF8STRING, value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes a {@code long} as a DER {@code INTEGER}.  {@link BigInteger#toByteArray()}
     * yields the minimal two's-complement big-endian form, which is exactly the
     * DER canonical content.
     *
     * @param value the value (may be negative)
     * @return the DER TLV encoding
     */
    static byte[] integer(long value) {
        return tlv(TAG_INTEGER, BigInteger.valueOf(value).toByteArray());
    }

    /**
     * Encodes raw bytes as an {@code OCTET STRING}.  The content is copied
     * verbatim; the empty array is valid.
     *
     * @param bytes the content (must not be {@code null})
     * @return the DER TLV encoding
     */
    static byte[] octetString(byte[] bytes) {
        if (bytes == null) throw new NullPointerException("bytes");
        return tlv(TAG_OCTET_STRING, bytes);
    }

    /**
     * Wraps already-encoded child TLVs in a {@code SEQUENCE}.  Children are
     * concatenated in the supplied order, which is preserved (STD-006 &sect;3.8);
     * the list may be empty for a zero-length {@code SEQUENCE}.
     *
     * @param children pre-encoded child TLVs (neither the list nor any element
     *                 may be {@code null})
     * @return the DER TLV encoding of the {@code SEQUENCE}
     */
    static byte[] sequence(List<byte[]> children) {
        if (children == null) throw new NullPointerException("children");
        int total = 0;
        for (byte[] child : children) {
            if (child == null) throw new NullPointerException("child element");
            total += child.length;
        }
        byte[] content = new byte[total];
        int pos = 0;
        for (byte[] child : children) {
            System.arraycopy(child, 0, content, pos, child.length);
            pos += child.length;
        }
        return tlv(TAG_SEQUENCE, content);
    }

    /* ------------------------------------------------------------------ */

    /** Assembles a tag octet, DER definite-form length, and content into a TLV. */
    private static byte[] tlv(byte tag, byte[] content) {
        byte[] length = encodeLength(content.length);
        byte[] out = new byte[1 + length.length + content.length];
        out[0] = tag;
        System.arraycopy(length,  0, out, 1,               length.length);
        System.arraycopy(content, 0, out, 1 + length.length, content.length);
        return out;
    }

    /**
     * Encodes a non-negative length as DER definite-form length octets
     * (X.690 &sect;8.1.3): short form for 0..127, else long form with the
     * minimal number of big-endian length octets.
     */
    private static byte[] encodeLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, got " + length);
        }
        if (length <= 127) {
            return new byte[]{ (byte) length };
        }
        int count = 0;
        for (int tmp = length; tmp != 0; tmp >>>= 8) count++;
        byte[] out = new byte[1 + count];
        out[0] = (byte) (0x80 | count);
        for (int i = count; i >= 1; i--) {
            out[i] = (byte) (length & 0xFF);
            length >>>= 8;
        }
        return out;
    }
}
