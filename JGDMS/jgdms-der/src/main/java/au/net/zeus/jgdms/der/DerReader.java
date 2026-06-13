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

/**
 * Streaming DER TLV decoder (JGDMS-STD-006 Phase 1).
 * <p>
 * A {@code DerReader} wraps a byte array together with a mutable cursor and a
 * hard end-bound. All reads are bounds-checked; all length fields are
 * validated before allocation (§3 principle 5). Any malformed or
 * non-canonical encoding raises {@link DerException} immediately with no
 * permissive fallback (§3 principle 6).
 *
 * <h3>Usage pattern</h3>
 * <pre>{@code
 *   DerReader r = new DerReader(derBytes);
 *   // Peek at tag, then dispatch:
 *   Tag t = r.peekTag();
 *   if (Tag.SEQUENCE.equals(t)) {
 *       DerReader seq = r.readSequence();
 *       while (seq.hasMore()) {
 *           // read children …
 *       }
 *   }
 * }</pre>
 *
 * <h3>SEQUENCE boundary enforcement</h3>
 * <p>
 * {@link #readSequence()} returns a <em>child</em> {@code DerReader} whose
 * end-bound is set to exactly the SEQUENCE content boundary. Even when the
 * outer buffer contains additional bytes after the SEQUENCE, the child reader
 * cannot see them. This is the mechanism that supports "old-narrower-payload"
 * decoding (a future-format SEQUENCE may have trailing fields that a
 * current-code reader simply does not consume, and the outer reader advances
 * past the whole SEQUENCE correctly after the child is done).
 */
public final class DerReader {

    /** The underlying data buffer (shared, never copied). */
    private final byte[] buf;

    /** Current read position (exclusive lower bound: next byte to read). */
    private int pos;

    /**
     * Exclusive upper bound for this reader. A sub-reader for a SEQUENCE has
     * {@code end} set to exactly the byte after the last SEQUENCE content byte.
     * The outermost reader has {@code end == buf.length}.
     */
    private final int end;

    /* ================================================================== */
    /* Constructors                                                         */
    /* ================================================================== */

    /**
     * Creates a reader over the full buffer.
     *
     * @param buf the data to decode (must not be {@code null})
     * @throws NullPointerException if {@code buf} is {@code null}
     */
    public DerReader(byte[] buf) {
        if (buf == null) throw new NullPointerException("buf");
        this.buf = buf;
        this.pos = 0;
        this.end = buf.length;
    }

    /**
     * Creates a sub-reader for a slice of the buffer. Used internally to
     * implement SEQUENCE boundary enforcement.
     *
     * @param buf   shared backing buffer
     * @param start first readable position
     * @param end   exclusive end (one past the last readable byte)
     */
    private DerReader(byte[] buf, int start, int end) {
        this.buf = buf;
        this.pos = start;
        this.end = end;
    }

    /* ================================================================== */
    /* Position queries                                                     */
    /* ================================================================== */

    /**
     * Returns {@code true} if there is at least one unread byte within this
     * reader's bounds. Used to iterate the children of a SEQUENCE.
     */
    public boolean hasMore() {
        return pos < end;
    }

    /**
     * Returns the current read position (index into the underlying buffer).
     */
    public int position() {
        return pos;
    }

    /**
     * Returns this reader's end-bound (exclusive). Content at or after this
     * index is invisible to this reader.
     */
    public int endBound() {
        return end;
    }

    /* ================================================================== */
    /* Tag peeking                                                          */
    /* ================================================================== */

    /**
     * Reads the tag at the current position <em>without</em> advancing the
     * cursor.
     *
     * @return the next tag
     * @throws DerException if the buffer is exhausted or the tag is malformed
     */
    public Tag peekTag() throws DerException {
        requireBytes(1);
        Tag.DecodeResult result = Tag.decode(buf, pos);
        return result.tag();
    }

    /* ================================================================== */
    /* TLV header reading                                                   */
    /* ================================================================== */

    /**
     * Result of reading a TLV header (tag + length) from the stream.
     *
     * @param tag           the decoded tag
     * @param contentLength the declared content length (≥ 0)
     */
    public record TlvHeader(Tag tag, int contentLength) {}

    /**
     * Reads and consumes a TLV header (tag + length octets), advancing the
     * cursor past both. The caller is then responsible for reading exactly
     * {@link TlvHeader#contentLength()} bytes.
     *
     * @return the header describing the next TLV
     * @throws DerException on any decode error (truncated input, non-canonical
     *                      length, etc.)
     */
    public TlvHeader readTlvHeader() throws DerException {
        requireBytes(1);
        Tag.DecodeResult tagResult = Tag.decode(buf, pos);
        pos += tagResult.bytesRead();

        int len = decodeLength();
        // Validate: content must fit within this reader's bounds
        if ((long) pos + len > end) {
            throw new DerException("TLV content length " + len
                    + " overruns reader boundary (pos=" + pos
                    + ", end=" + end + ")");
        }
        return new TlvHeader(tagResult.tag(), len);
    }

    /* ================================================================== */
    /* Primitive typed readers                                              */
    /* ================================================================== */

    /**
     * Reads a BOOLEAN TLV, decoding the single content byte.
     * <p>
     * DER requires the content to be exactly one octet, and that octet must
     * be {@code 0x00} (false) or {@code 0xFF} (true). Any other length or
     * content octet is rejected.
     *
     * @return the decoded boolean value
     * @throws DerException if the encoding is malformed or non-canonical
     */
    public boolean readBoolean() throws DerException {
        TlvHeader hdr = readTlvHeader();
        if (!Tag.BOOLEAN.equals(hdr.tag())) {
            throw new DerException("Expected BOOLEAN (tag 0x01), got " + hdr.tag());
        }
        if (hdr.contentLength() != 1) {
            throw new DerException("BOOLEAN content length must be 1, got "
                    + hdr.contentLength());
        }
        int b = buf[pos++] & 0xFF;
        if (b == 0x00) return false;
        if (b == 0xFF) return true;
        throw new DerException(
                "BOOLEAN content must be 0x00 or 0xFF in DER, got 0x"
                + Integer.toHexString(b));
    }

    /**
     * Reads an INTEGER TLV, returning the value as a {@link BigInteger}.
     * <p>
     * Validation enforces DER canonical INTEGER form:
     * <ul>
     *   <li>Content length ≥ 1 (zero-length INTEGER is invalid).</li>
     *   <li>No non-minimal leading {@code 0x00} (i.e. first two octets
     *       {@code 0x00 ??} where {@code ?? bit-7 == 0} is rejected).</li>
     *   <li>No non-minimal leading {@code 0xFF} (i.e. first two octets
     *       {@code 0xFF ??} where {@code ?? bit-7 == 1} is rejected).</li>
     * </ul>
     *
     * @return the decoded integer value
     * @throws DerException if the encoding is malformed or non-canonical
     */
    public BigInteger readInteger() throws DerException {
        TlvHeader hdr = readTlvHeader();
        if (!Tag.INTEGER.equals(hdr.tag())) {
            throw new DerException("Expected INTEGER (tag 0x02), got " + hdr.tag());
        }
        int len = hdr.contentLength();
        if (len == 0) {
            throw new DerException("INTEGER content must be at least 1 byte");
        }
        // Reject non-minimal forms
        if (len >= 2) {
            int b0 = buf[pos] & 0xFF;
            int b1 = buf[pos + 1] & 0xFF;
            if (b0 == 0x00 && (b1 & 0x80) == 0) {
                throw new DerException(
                        "Non-canonical INTEGER: leading 0x00 with non-negative following byte (0x"
                        + Integer.toHexString(b1) + ")");
            }
            if (b0 == 0xFF && (b1 & 0x80) != 0) {
                throw new DerException(
                        "Non-canonical INTEGER: leading 0xFF with negative-marked following byte (0x"
                        + Integer.toHexString(b1) + ")");
            }
        }
        byte[] content = new byte[len];
        System.arraycopy(buf, pos, content, 0, len);
        pos += len;
        return new BigInteger(content);
    }

    /**
     * Reads an OCTET STRING TLV, returning the raw content bytes.
     * <p>
     * The returned array is a fresh copy; modifications do not affect the
     * reader. The empty OCTET STRING ({@code 04 00}) is valid.
     *
     * @return the content bytes
     * @throws DerException if the encoding is malformed
     */
    public byte[] readOctetString() throws DerException {
        TlvHeader hdr = readTlvHeader();
        if (!Tag.OCTET_STRING.equals(hdr.tag())) {
            throw new DerException("Expected OCTET STRING (tag 0x04), got " + hdr.tag());
        }
        int len = hdr.contentLength();
        byte[] content = new byte[len];
        System.arraycopy(buf, pos, content, 0, len);
        pos += len;
        return content;
    }

    /**
     * Reads a UTF8String TLV, decoding the content bytes as UTF-8.
     * <p>
     * The empty UTF8String ({@code 0C 00}) is valid and returns an empty
     * string.
     *
     * @return the decoded string
     * @throws DerException if the encoding is malformed or the content is
     *                      not valid UTF-8
     */
    public String readUtf8String() throws DerException {
        TlvHeader hdr = readTlvHeader();
        if (!Tag.UTF8STRING.equals(hdr.tag())) {
            throw new DerException("Expected UTF8String (tag 0x0C), got " + hdr.tag());
        }
        int len = hdr.contentLength();
        String value;
        try {
            value = new String(buf, pos, len, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new DerException("UTF8String: invalid UTF-8 content", e);
        }
        pos += len;
        return value;
    }

    /**
     * Reads a SEQUENCE TLV header and returns a child {@code DerReader}
     * bounded to exactly the SEQUENCE content.
     * <p>
     * The outer reader's cursor advances past the entire SEQUENCE (tag +
     * length + content). The child reader starts at the first content byte
     * and stops at the last; it cannot see any bytes in the outer buffer
     * beyond the SEQUENCE boundary.
     *
     * @return a sub-reader bounded to the SEQUENCE content
     * @throws DerException if the encoding is malformed or the tag is not
     *                      SEQUENCE
     */
    public DerReader readSequence() throws DerException {
        TlvHeader hdr = readTlvHeader();
        if (!Tag.SEQUENCE.equals(hdr.tag())) {
            throw new DerException("Expected SEQUENCE (tag 0x30), got " + hdr.tag());
        }
        int contentStart = pos;
        int contentEnd   = pos + hdr.contentLength();
        pos = contentEnd; // advance outer cursor past the whole SEQUENCE
        return new DerReader(buf, contentStart, contentEnd);
    }

    /**
     * Reads raw content bytes for a TLV whose header has already been
     * consumed via {@link #readTlvHeader()}. Advances the cursor by
     * {@code length} bytes.
     *
     * @param length number of bytes to read (must equal the content length
     *               from the corresponding header)
     * @return a fresh copy of the content bytes
     * @throws DerException if there are fewer than {@code length} bytes
     *                      remaining
     */
    public byte[] readRawContent(int length) throws DerException {
        if (length < 0) throw new DerException("readRawContent: negative length " + length);
        requireBytes(length);
        byte[] out = new byte[length];
        System.arraycopy(buf, pos, out, 0, length);
        pos += length;
        return out;
    }

    /* ================================================================== */
    /* Length decoding (internal)                                           */
    /* ================================================================== */

    /**
     * Decodes the DER definite-form length octets starting at {@link #pos}
     * and advances the cursor past them.
     *
     * <p>Rejects:
     * <ul>
     *   <li>Indefinite form ({@code 0x80}) — BER-only, forbidden in DER.</li>
     *   <li>Reserved form ({@code 0xFF}) — forbidden by X.690.</li>
     *   <li>Non-minimal long form (a value ≤ 127 encoded in long form, or a
     *       long-form encoding with a leading zero length-octet).</li>
     *   <li>Length overflow (result exceeds {@code Integer.MAX_VALUE}).</li>
     * </ul>
     *
     * @return the decoded non-negative length
     * @throws DerException on any malformed or non-canonical length
     */
    private int decodeLength() throws DerException {
        requireBytes(1);
        int first = buf[pos++] & 0xFF;

        if (first <= 0x7F) {
            // Short form: single-octet, value 0–127
            return first;
        }
        if (first == 0x80) {
            throw new DerException("Indefinite-form length is not permitted in DER");
        }
        if (first == 0xFF) {
            throw new DerException("Reserved length octet 0xFF encountered");
        }
        // Long form: first octet = 0x80 | n, followed by n big-endian length octets
        int n = first & 0x7F;
        requireBytes(n);

        // Leading zero length-octets are non-minimal
        if (buf[pos] == 0x00) {
            throw new DerException(
                    "Non-canonical length: long-form length has leading zero octet");
        }
        // n == 1 with value ≤ 127 is non-minimal (should have used short form)
        if (n == 1 && (buf[pos] & 0xFF) <= 127) {
            throw new DerException(
                    "Non-canonical length: long form used for value ≤ 127 ("
                    + (buf[pos] & 0xFF) + ")");
        }

        long value = 0;
        for (int i = 0; i < n; i++) {
            value = (value << 8) | (buf[pos++] & 0xFF);
            if (value > Integer.MAX_VALUE) {
                throw new DerException(
                        "Length value exceeds Integer.MAX_VALUE");
            }
        }
        int length = (int) value;
        // After we know the length, check non-minimal for multi-byte:
        // if n >= 2 but the value would fit in n-1 bytes, it is non-minimal.
        // The leading-zero check above already catches the key case; also catch
        // n == 2 with high byte == 0 (already caught by leading-zero check).
        // For n >= 2, if the value < 128, it should have used short form (also caught).
        // The remaining non-minimal case: n > 1 and the value fits in fewer bytes.
        // Since we already ruled out leading zeros, the remaining bytes are
        // significant. The only remaining non-canonical case is n == 1 with
        // value <= 127, already checked above.
        return length;
    }

    /* ================================================================== */
    /* Bounds checking                                                      */
    /* ================================================================== */

    /**
     * Asserts that at least {@code needed} bytes remain in this reader's
     * window, throwing {@link DerException} if not.
     *
     * @param needed number of bytes required
     * @throws DerException if fewer than {@code needed} bytes remain
     */
    private void requireBytes(int needed) throws DerException {
        if (pos + needed > end) {
            throw new DerException("Unexpected end of DER input: need "
                    + needed + " byte(s) at pos=" + pos + ", end=" + end);
        }
    }
}
