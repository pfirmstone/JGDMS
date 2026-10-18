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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stateless DER TLV encoder (JGDMS-STD-006 Phase 1).
 * <p>
 * Each {@code write*} method returns the complete, canonical DER encoding of
 * the given value as a freshly allocated {@code byte[]}. The caller may
 * concatenate the results for SEQUENCE children and pass the array to
 * {@link #writeSequence(byte[])}. Alternatively, {@link #writeSequence(List)}
 * accepts a pre-assembled list of pre-encoded child TLVs.
 *
 * <h3>Canonical (DER) guarantees provided</h3>
 * <ul>
 *   <li><b>INTEGER</b> -- {@link BigInteger#toByteArray()} produces the
 *       minimal two's-complement big-endian encoding; no further compression
 *       is needed.</li>
 *   <li><b>BOOLEAN</b> -- {@code false} encodes as {@code 0x00},
 *       {@code true} as {@code 0xFF} (DER requires exactly these two values,
 *       not arbitrary non-zero).</li>
 *   <li><b>Length</b> -- short form for 0-127; long form with the minimal
 *       number of length octets for >= 128.</li>
 *   <li><b>SEQUENCE</b> -- the constructed, ordered concatenation of child
 *       TLVs; order is the caller's responsibility (S3.8).</li>
 * </ul>
 *
 * <p>All methods are thread-safe (no mutable state).
 */
public final class DerWriter {

    private DerWriter() {
        throw new AssertionError("no instances");
    }

    /* ================================================================== */
    /* Primitive writers                                                    */
    /* ================================================================== */

    /**
     * Encodes a {@code boolean} as UNIVERSAL PRIMITIVE BOOLEAN (tag {@code 0x01}).
     * <p>
     * DER encoding: content is exactly one octet -- {@code 0x00} for
     * {@code false}, {@code 0xFF} for {@code true}.
     *
     * @param value the value to encode
     * @return the 3-byte DER encoding {@code 01 01 00} or {@code 01 01 FF}
     */
    public static byte[] writeBoolean(boolean value) {
        return new byte[]{ 0x01, 0x01, value ? (byte)0xFF : (byte)0x00 };
    }

    /**
     * Encodes a {@link BigInteger} as UNIVERSAL PRIMITIVE INTEGER
     * (tag {@code 0x02}).
     * <p>
     * {@code BigInteger.toByteArray()} produces the minimal two's-complement
     * big-endian encoding, which is exactly the DER canonical form.
     *
     * @param value the integer to encode (must not be {@code null})
     * @return the DER TLV encoding
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static byte[] writeInteger(BigInteger value) {
        if (value == null) throw new NullPointerException("value");
        byte[] content = value.toByteArray(); // already minimal two's-complement
        return writeTlv(Tag.INTEGER, content);
    }

    /**
     * Encodes a {@code long} as UNIVERSAL PRIMITIVE INTEGER (tag {@code 0x02}).
     *
     * @param value the long value
     * @return the DER TLV encoding
     */
    public static byte[] writeInteger(long value) {
        return writeInteger(BigInteger.valueOf(value));
    }

    /**
     * Encodes a raw byte array as UNIVERSAL PRIMITIVE OCTET STRING
     * (tag {@code 0x04}).
     * <p>
     * The content bytes are copied verbatim; the empty array is valid.
     *
     * @param bytes the content bytes (must not be {@code null})
     * @return the DER TLV encoding
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public static byte[] writeOctetString(byte[] bytes) {
        if (bytes == null) throw new NullPointerException("bytes");
        return writeTlv(Tag.OCTET_STRING, bytes);
    }

    /**
     * Encodes a Java {@link String} as UNIVERSAL PRIMITIVE UTF8String
     * (tag {@code 0x0C}).
     * <p>
     * The string is UTF-8 encoded; the empty string is valid and encodes as
     * zero-length content.
     *
     * @param value the string to encode (must not be {@code null})
     * @return the DER TLV encoding
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static byte[] writeUtf8String(String value) {
        if (value == null) throw new NullPointerException("value");
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        return writeTlv(Tag.UTF8STRING, content);
    }

    /* ================================================================== */
    /* SEQUENCE                                                             */
    /* ================================================================== */

    /**
     * Wraps already-encoded child TLVs in a UNIVERSAL CONSTRUCTED SEQUENCE
     * (tag {@code 0x30}).
     * <p>
     * The children must already be fully encoded (each a complete TLV); they
     * are concatenated in the supplied order, preserving the caller's ordering
     * (S3.8).
     *
     * @param encodedChildren concatenation of pre-encoded child TLVs
     *                        (may be empty for a zero-length SEQUENCE)
     * @return the DER TLV encoding of the SEQUENCE
     * @throws NullPointerException if {@code encodedChildren} is {@code null}
     */
    public static byte[] writeSequence(byte[] encodedChildren) {
        if (encodedChildren == null) throw new NullPointerException("encodedChildren");
        return writeTlv(Tag.SEQUENCE, encodedChildren);
    }

    /**
     * Wraps a list of pre-encoded child TLVs in a UNIVERSAL CONSTRUCTED
     * SEQUENCE (tag {@code 0x30}).
     * <p>
     * Children are concatenated in list order. The list may be empty.
     *
     * @param children list of pre-encoded child TLVs (must not be
     *                 {@code null}; elements must not be {@code null})
     * @return the DER TLV encoding of the SEQUENCE
     * @throws NullPointerException if {@code children} or any element is
     *                              {@code null}
     */
    public static byte[] writeSequence(List<byte[]> children) {
        return frame(Tag.SEQUENCE, children);
    }

    /**
     * Wraps a list of pre-encoded child TLVs in a UNIVERSAL CONSTRUCTED
     * SET / SET OF (tag {@code 0x31}).
     * <p>
     * Structurally identical to {@link #writeSequence(List)} but with the SET tag.
     * STD-006 §3.8 uses this for a CANONICALISE-discipline collection field (an
     * ASN.1 {@code SET OF}); the caller MUST have already octet-sorted the children
     * per X.690 §11.6 (this method does not sort — it only frames).
     *
     * @param children list of pre-encoded, already-octet-sorted child TLVs (must not
     *                 be {@code null}; elements must not be {@code null})
     * @return the DER TLV encoding of the SET
     * @throws NullPointerException if {@code children} or any element is {@code null}
     */
    public static byte[] writeSet(List<byte[]> children) {
        return frame(Tag.SET, children);
    }

    /* ================================================================== */
    /* Generic TLV builder                                                  */
    /* ================================================================== */

    /**
     * Encodes an arbitrary tag + content into a TLV.
     * <p>
     * This is the low-level primitive used by all typed methods. Callers that
     * need context-class or application-class TLVs can call this directly.
     *
     * @param tag     the tag to use (must not be {@code null})
     * @param content the value bytes (must not be {@code null})
     * @return the complete TLV encoding
     * @throws NullPointerException if {@code tag} or {@code content} is
     *                              {@code null}
     */
    public static byte[] writeTlv(Tag tag, byte[] content) {
        if (tag == null) throw new NullPointerException("tag");
        if (content == null) throw new NullPointerException("content");
        byte[] out = new byte[headerSize(tag, content.length) + content.length];
        int pos = writeHeader(out, 0, tag, content.length);
        System.arraycopy(content, 0, out, pos, content.length);
        return out;
    }

    /**
     * Frames a list of pre-encoded child TLVs under {@code tag}: one allocation,
     * one copy of each child (the children are never concatenated into an
     * intermediate content array first).
     */
    private static byte[] frame(Tag tag, List<byte[]> children) {
        if (children == null) throw new NullPointerException("children");
        long total = 0;
        for (byte[] child : children) {
            if (child == null) throw new NullPointerException("child element");
            total += child.length;
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("content too large: " + total);
        }
        int contentLength = (int) total;
        byte[] out = new byte[headerSize(tag, contentLength) + contentLength];
        int pos = writeHeader(out, 0, tag, contentLength);
        for (byte[] child : children) {
            System.arraycopy(child, 0, out, pos, child.length);
            pos += child.length;
        }
        return out;
    }

    /** Size in octets of the tag + definite-length header (same bytes as {@link #encodeLength}). */
    private static int headerSize(Tag tag, int contentLength) {
        return tag.encodedSize() + lengthSize(contentLength);
    }

    private static int lengthSize(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, got " + length);
        }
        if (length <= 127) return 1;
        int count = 0;
        for (int tmp = length; tmp != 0; tmp >>>= 8) count++;
        return 1 + count;
    }

    /** Writes tag octets then definite-length octets at {@code pos}; returns the position after. */
    private static int writeHeader(byte[] out, int pos, Tag tag, int contentLength) {
        pos = tag.encodeInto(out, pos);
        if (contentLength <= 127) {
            out[pos] = (byte) contentLength;
            return pos + 1;
        }
        int count = 0;
        for (int tmp = contentLength; tmp != 0; tmp >>>= 8) count++;
        out[pos] = (byte) (0x80 | count);
        int len = contentLength;
        for (int i = count; i >= 1; i--) {
            out[pos + i] = (byte) (len & 0xFF);
            len >>>= 8;
        }
        return pos + 1 + count;
    }

    /* ================================================================== */
    /* Length encoding                                                      */
    /* ================================================================== */

    /**
     * Encodes a non-negative length value as DER definite-form length octets
     * (X.690 S8.1.3).
     * <ul>
     *   <li>0-127: short form -- a single octet with bit 8 cleared.</li>
     *   <li>128-{@code Integer.MAX_VALUE}: long form -- first octet is
     *       {@code 0x80 | n} where {@code n} is the number of following
     *       big-endian length octets; uses the minimal {@code n}.</li>
     * </ul>
     *
     * @param length the length to encode (must be >= 0)
     * @return the encoded length octets
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public static byte[] encodeLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, got " + length);
        }
        if (length <= 127) {
            return new byte[]{ (byte) length };
        }
        // Long form: find minimal number of bytes needed
        int count = 0;
        int tmp = length;
        while (tmp != 0) { count++; tmp >>>= 8; }
        byte[] out = new byte[1 + count];
        out[0] = (byte)(0x80 | count);
        for (int i = count; i >= 1; i--) {
            out[i] = (byte)(length & 0xFF);
            length >>>= 8;
        }
        return out;
    }
}
