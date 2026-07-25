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
package org.apache.river.outrigger.proxy;

/**
 * Canonical DER codec for the Outrigger CEL <em>filter envelope</em> — the
 * opaque {@code byte[]} carried as an explicit operation parameter on the
 * filtered {@link OutriggerServer} methods (JGDMS-STD-011 / SOW Part&nbsp;B,
 * unit&nbsp;B1).
 *
 * <h3>Grammar</h3>
 * <pre>
 *   FilterEnvelope ::= SEQUENCE {
 *       version   INTEGER,        -- currently {@value #VERSION}
 *       celWire   OCTET STRING    -- an opaque CEL CelFilterRecord DER encoding
 *   }
 * </pre>
 *
 * The envelope deliberately carries <b>nothing else</b> — no field-name table,
 * no bindings, no {@code targetClassName}. Field names in the CEL expression are
 * resolved <em>server-side</em> against the query template's own v2 schema
 * chain (the applicability key is the candidate's {@code entrySchemaDigest});
 * there is no client-supplied name table for the server to trust.
 *
 * <h3>Layering</h3>
 * This codec lives in {@code outrigger-dl} (release&nbsp;8 target) so that both
 * the client authoring path and the server admission seam share one grammar.
 * It treats {@code celWire} as an <b>opaque octet string</b> and never looks
 * inside it, so {@code outrigger-dl} takes no {@code jgdms-cel} dependency. The
 * CEL bytes are decoded and verified only in {@code outrigger-service} (which
 * does depend on {@code jgdms-cel}).
 *
 * <h3>Fail-closed discipline (house style, mirrors {@code au.net.zeus.jgdms.der.DerReader})</h3>
 * The decoder is strict, canonical, and bounded. It rejects, with a
 * {@link FilterRejectedException} of reason
 * {@link FilterRejectedException.Reason#ENVELOPE_MALFORMED}: a non-SEQUENCE
 * outer, indefinite or non-minimal length, non-canonical INTEGER, any trailing
 * byte after the SEQUENCE, extra elements inside the SEQUENCE, an unsupported
 * {@code version}, or a ceiling breach ({@link #MAX_ENVELOPE_BYTES} /
 * {@link #MAX_CEL_WIRE_BYTES}). All length fields are validated before any
 * allocation. There is no permissive fallback.
 *
 * <p>The {@link #MAX_CEL_WIRE_BYTES} ceiling here is a coarse pre-verification
 * DoS bound only; the authoritative CEL size/cost ceilings are enforced by the
 * CEL verifier at the admission seam.
 *
 * @since JGDMS 4.0.0
 */
public final class FilterEnvelope {

    /** The only envelope {@code version} accepted by this build. */
    public static final int VERSION = 1;

    /**
     * Hard ceiling on the total envelope encoding, in bytes. A coarse
     * pre-verification allocation/DoS bound.
     */
    public static final int MAX_ENVELOPE_BYTES = 1 << 20; // 1 MiB

    /**
     * Hard ceiling on the {@code celWire} OCTET STRING content, in bytes. A
     * coarse pre-verification bound; the authoritative CEL ceilings are the
     * verifier's own.
     */
    public static final int MAX_CEL_WIRE_BYTES = 1 << 16; // 64 KiB

    // DER identifier octets (primitive/constructed universal tags).
    private static final int TAG_INTEGER      = 0x02;
    private static final int TAG_OCTET_STRING = 0x04;
    private static final int TAG_SEQUENCE     = 0x30;

    private final int version;
    private final byte[] celWire;

    private FilterEnvelope(int version, byte[] celWire) {
        this.version = version;
        this.celWire = celWire;
    }

    /**
     * @return the decoded {@code version} field
     */
    public int version() {
        return version;
    }

    /**
     * @return the opaque CEL wire bytes (a fresh copy; never null)
     */
    public byte[] celWire() {
        return celWire.clone();
    }

    /* ================================================================== */
    /* Encoding                                                             */
    /* ================================================================== */

    /**
     * Encodes a canonical filter envelope with the current {@link #VERSION}
     * around the given opaque CEL wire bytes.
     *
     * @param celWire the opaque CEL {@code CelFilterRecord} DER encoding
     * @return the canonical DER envelope
     * @throws NullPointerException if {@code celWire} is null
     * @throws IllegalArgumentException if {@code celWire} exceeds
     *         {@link #MAX_CEL_WIRE_BYTES} (a producer-side programming error)
     */
    public static byte[] encode(byte[] celWire) {
        return encode(VERSION, celWire);
    }

    /**
     * Encodes a canonical filter envelope with an explicit {@code version}.
     * <b>Package-private test knob only</b>: producing an unsupported version is
     * permitted here (the decoder rejects it) so in-package tests can exercise
     * the version-mismatch path. Not a shipped API — production callers must use
     * {@link #encode(byte[])}, which always writes the current {@link #VERSION}.
     *
     * @param version the {@code version} field to encode
     * @param celWire the opaque CEL wire bytes
     * @return the canonical DER envelope
     * @throws NullPointerException if {@code celWire} is null
     * @throws IllegalArgumentException if {@code celWire} exceeds
     *         {@link #MAX_CEL_WIRE_BYTES}
     */
    static byte[] encode(int version, byte[] celWire) {
        if (celWire == null) throw new NullPointerException("celWire");
        if (celWire.length > MAX_CEL_WIRE_BYTES) {
            throw new IllegalArgumentException("celWire length " + celWire.length
                    + " exceeds MAX_CEL_WIRE_BYTES (" + MAX_CEL_WIRE_BYTES + ")");
        }
        byte[] verTlv = tlv(TAG_INTEGER, minimalInteger(version));
        byte[] wireTlv = tlv(TAG_OCTET_STRING, celWire);
        byte[] content = concat(verTlv, wireTlv);
        return tlv(TAG_SEQUENCE, content);
    }

    /* ================================================================== */
    /* Decoding                                                             */
    /* ================================================================== */

    /**
     * Decodes and canonically validates a filter envelope.
     *
     * @param envelope the DER envelope bytes
     * @return the decoded envelope (version + opaque CEL wire bytes)
     * @throws NullPointerException if {@code envelope} is null
     * @throws FilterRejectedException (reason
     *         {@link FilterRejectedException.Reason#ENVELOPE_MALFORMED}) on any
     *         malformed, non-canonical, over-ceiling, or wrong-version input
     */
    public static FilterEnvelope decode(byte[] envelope) throws FilterRejectedException {
        if (envelope == null) throw new NullPointerException("envelope");
        if (envelope.length > MAX_ENVELOPE_BYTES) {
            throw malformed("envelope length " + envelope.length
                    + " exceeds MAX_ENVELOPE_BYTES (" + MAX_ENVELOPE_BYTES + ")");
        }
        Cursor c = new Cursor(envelope, 0, envelope.length);

        // Outer SEQUENCE spanning exactly the whole buffer (no trailing bytes).
        int tag = c.readTagByte();
        if (tag != TAG_SEQUENCE) {
            throw malformed("expected outer SEQUENCE (0x30), got 0x"
                    + Integer.toHexString(tag));
        }
        int seqLen = c.readLength();
        int seqContentStart = c.pos;
        int seqContentEnd = seqContentStart + seqLen;
        if (seqContentEnd != envelope.length) {
            throw malformed("trailing bytes after outer SEQUENCE (content ends at "
                    + seqContentEnd + ", buffer length " + envelope.length + ")");
        }
        Cursor seq = new Cursor(envelope, seqContentStart, seqContentEnd);

        // version INTEGER
        int vTag = seq.readTagByte();
        if (vTag != TAG_INTEGER) {
            throw malformed("expected version INTEGER (0x02), got 0x"
                    + Integer.toHexString(vTag));
        }
        int vLen = seq.readLength();
        int version = seq.readCanonicalInteger(vLen);

        // celWire OCTET STRING
        int oTag = seq.readTagByte();
        if (oTag != TAG_OCTET_STRING) {
            throw malformed("expected celWire OCTET STRING (0x04), got 0x"
                    + Integer.toHexString(oTag));
        }
        int oLen = seq.readLength();
        if (oLen > MAX_CEL_WIRE_BYTES) {
            throw malformed("celWire length " + oLen
                    + " exceeds MAX_CEL_WIRE_BYTES (" + MAX_CEL_WIRE_BYTES + ")");
        }
        byte[] celWire = seq.readBytes(oLen);

        // No extra elements inside the SEQUENCE.
        if (seq.pos != seq.end) {
            throw malformed("unexpected trailing element(s) inside SEQUENCE ("
                    + (seq.end - seq.pos) + " byte(s) remain)");
        }

        if (version != VERSION) {
            throw malformed("unsupported envelope version " + version
                    + " (this build accepts version " + VERSION + ")");
        }
        return new FilterEnvelope(version, celWire);
    }

    private static FilterRejectedException malformed(String detail) {
        return new FilterRejectedException(
                FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                "filter envelope malformed: " + detail);
    }

    /* ================================================================== */
    /* Minimal DER primitives (self-contained, release-8, no jgdms-der)     */
    /* ================================================================== */

    /** Canonical minimal two's-complement big-endian encoding of an int. */
    private static byte[] minimalInteger(int value) {
        // Determine the minimal number of bytes for a canonical DER INTEGER.
        int len = 1;
        // Grow while the top byte is redundant (all sign bits) — mirrors
        // BigInteger.valueOf(value).toByteArray() minimality.
        while (len < 4) {
            int shifted = value >> (8 * len);
            int signOfKept = (value >> (8 * len - 1)) & 0x1;
            // If the higher bytes are pure sign extension of the kept top bit,
            // they are redundant.
            if ((shifted == 0 && signOfKept == 0) || (shifted == -1 && signOfKept == 1)) {
                break;
            }
            len++;
        }
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[len - 1 - i] = (byte) (value >> (8 * i));
        }
        return out;
    }

    /** Wraps content in a TLV with the given tag and canonical definite length. */
    private static byte[] tlv(int tag, byte[] content) {
        byte[] len = encodeLength(content.length);
        byte[] out = new byte[1 + len.length + content.length];
        out[0] = (byte) tag;
        System.arraycopy(len, 0, out, 1, len.length);
        System.arraycopy(content, 0, out, 1 + len.length, content.length);
        return out;
    }

    /** Canonical DER definite length octets: short form &le;127, else minimal long form. */
    private static byte[] encodeLength(int n) {
        if (n < 0) throw new IllegalArgumentException("negative length " + n);
        if (n <= 0x7F) {
            return new byte[]{ (byte) n };
        }
        // Long form: minimal number of big-endian length octets.
        int bytes = 1;
        int tmp = n;
        while ((tmp >>>= 8) != 0) bytes++;
        byte[] out = new byte[1 + bytes];
        out[0] = (byte) (0x80 | bytes);
        for (int i = 0; i < bytes; i++) {
            out[out.length - 1 - i] = (byte) (n >>> (8 * i));
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * A bounded, canonical-only DER cursor over a slice of a shared buffer.
     * Mirrors the validation rules of {@code au.net.zeus.jgdms.der.DerReader}.
     */
    private static final class Cursor {
        private final byte[] buf;
        private int pos;
        private final int end;

        Cursor(byte[] buf, int start, int end) {
            this.buf = buf;
            this.pos = start;
            this.end = end;
        }

        int readTagByte() throws FilterRejectedException {
            require(1);
            int tag = buf[pos++] & 0xFF;
            // Reject high-tag-number (multi-byte) forms: the grammar uses only
            // low-number universal tags.
            if ((tag & 0x1F) == 0x1F) {
                throw malformed("multi-byte tag not permitted in this grammar");
            }
            return tag;
        }

        /** Reads a canonical DER definite length; rejects indefinite/non-minimal. */
        int readLength() throws FilterRejectedException {
            require(1);
            int first = buf[pos++] & 0xFF;
            if (first <= 0x7F) {
                return first;
            }
            if (first == 0x80) {
                throw malformed("indefinite-form length not permitted in DER");
            }
            if (first == 0xFF) {
                throw malformed("reserved length octet 0xFF");
            }
            int n = first & 0x7F;
            require(n);
            if (buf[pos] == 0x00) {
                throw malformed("non-canonical length: leading zero length octet");
            }
            if (n == 1 && (buf[pos] & 0xFF) <= 0x7F) {
                throw malformed("non-canonical length: long form used for value <= 127");
            }
            if (n > 4) {
                throw malformed("length octet count " + n + " exceeds supported range");
            }
            long value = 0;
            for (int i = 0; i < n; i++) {
                value = (value << 8) | (buf[pos++] & 0xFF);
            }
            if (value > MAX_ENVELOPE_BYTES) {
                throw malformed("declared length " + value + " exceeds MAX_ENVELOPE_BYTES");
            }
            int len = (int) value;
            if ((long) pos + len > end) {
                throw malformed("declared length " + len + " overruns bounds (pos="
                        + pos + ", end=" + end + ")");
            }
            return len;
        }

        /** Reads a canonical DER INTEGER of the given content length into an int. */
        int readCanonicalInteger(int len) throws FilterRejectedException {
            if (len == 0) {
                throw malformed("INTEGER content must be at least 1 byte");
            }
            if (len > 4) {
                throw malformed("INTEGER too large for envelope version field (" + len + " bytes)");
            }
            require(len);
            if (len >= 2) {
                int b0 = buf[pos] & 0xFF;
                int b1 = buf[pos + 1] & 0xFF;
                if (b0 == 0x00 && (b1 & 0x80) == 0) {
                    throw malformed("non-canonical INTEGER: redundant leading 0x00");
                }
                if (b0 == 0xFF && (b1 & 0x80) != 0) {
                    throw malformed("non-canonical INTEGER: redundant leading 0xFF");
                }
            }
            int value = (buf[pos] & 0x80) != 0 ? -1 : 0; // sign-extend
            for (int i = 0; i < len; i++) {
                value = (value << 8) | (buf[pos++] & 0xFF);
            }
            return value;
        }

        byte[] readBytes(int len) throws FilterRejectedException {
            require(len);
            byte[] out = new byte[len];
            System.arraycopy(buf, pos, out, 0, len);
            pos += len;
            return out;
        }

        private void require(int needed) throws FilterRejectedException {
            if (needed < 0 || pos + needed > end) {
                throw malformed("unexpected end of input: need " + needed
                        + " byte(s) at pos=" + pos + ", end=" + end);
            }
        }
    }
}
