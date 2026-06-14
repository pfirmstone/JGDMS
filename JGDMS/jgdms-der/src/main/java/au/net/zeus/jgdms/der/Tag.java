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

/**
 * Immutable representation of an ASN.1 / DER tag (identifier octets), per
 * X.690 §8.1.2.
 * <p>
 * A tag consists of three orthogonal attributes:
 * <ul>
 *   <li><b>class</b> — UNIVERSAL (0), APPLICATION (1), CONTEXT (2),
 *       PRIVATE (3);</li>
 *   <li><b>constructed flag</b> — {@code true} for structured types
 *       (SEQUENCE, SET, and any type with constructed encoding);</li>
 *   <li><b>tag number</b> — non-negative integer; values 0–30 fit in the
 *       low-tag-number (single-octet) form; ≥ 31 use the high-tag-number
 *       multi-octet form.</li>
 * </ul>
 *
 * <h3>Universal-tag constants</h3>
 * <p>
 * Frequently used universal tags are exposed as public constants
 * ({@link #BOOLEAN}, {@link #INTEGER}, {@link #OCTET_STRING},
 * {@link #UTF8STRING}, {@link #SEQUENCE}).
 *
 * <h3>Encoding and decoding</h3>
 * <p>
 * {@link #encode()} writes the minimal canonical identifier-octets per X.690
 * §8.1.2. {@link #decode(byte[], int)} reads those octets and returns the
 * resulting {@code Tag}; the caller can inspect {@link DecodeResult#bytesRead}
 * to advance its cursor.
 */
public final class Tag {

    /* ------------------------------------------------------------------ */
    /* Tag class constants                                                  */
    /* ------------------------------------------------------------------ */

    /** Tag class UNIVERSAL (bits 8–7 = 00). */
    public static final int CLASS_UNIVERSAL   = 0;
    /** Tag class APPLICATION (bits 8–7 = 01). */
    public static final int CLASS_APPLICATION = 1;
    /** Tag class CONTEXT-SPECIFIC (bits 8–7 = 10). */
    public static final int CLASS_CONTEXT     = 2;
    /** Tag class PRIVATE (bits 8–7 = 11). */
    public static final int CLASS_PRIVATE     = 3;

    /* ------------------------------------------------------------------ */
    /* Universal-tag number constants                                       */
    /* ------------------------------------------------------------------ */

    /** UNIVERSAL BOOLEAN — primitive, tag 1 ({@code 0x01}). */
    public static final Tag BOOLEAN     = new Tag(CLASS_UNIVERSAL, false, 1);
    /** UNIVERSAL INTEGER — primitive, tag 2 ({@code 0x02}). */
    public static final Tag INTEGER     = new Tag(CLASS_UNIVERSAL, false, 2);
    /** UNIVERSAL OCTET STRING — primitive, tag 4 ({@code 0x04}). */
    public static final Tag OCTET_STRING = new Tag(CLASS_UNIVERSAL, false, 4);
    /** UNIVERSAL UTF8String — primitive, tag 12 ({@code 0x0C}). */
    public static final Tag UTF8STRING  = new Tag(CLASS_UNIVERSAL, false, 12);
    /**
     * UNIVERSAL SEQUENCE — constructed, tag 16 ({@code 0x30}).
     * Note: the encoding of the first octet is 0x30 = 0b00_1_10000 (universal,
     * constructed, number 16).
     */
    public static final Tag SEQUENCE    = new Tag(CLASS_UNIVERSAL, true, 16);

    /* ------------------------------------------------------------------ */
    /* Instance state                                                       */
    /* ------------------------------------------------------------------ */

    private final int     tagClass;
    private final boolean constructed;
    private final int     tagNumber;

    /**
     * Creates a tag with the given attributes.
     *
     * @param tagClass    one of {@link #CLASS_UNIVERSAL} … {@link #CLASS_PRIVATE}
     * @param constructed {@code true} for a constructed (composite) type
     * @param tagNumber   non-negative tag number
     * @throws IllegalArgumentException if {@code tagClass} is out of range or
     *                                  {@code tagNumber} is negative
     */
    public Tag(int tagClass, boolean constructed, int tagNumber) {
        if (tagClass < 0 || tagClass > 3) {
            throw new IllegalArgumentException("tagClass must be 0–3, got " + tagClass);
        }
        if (tagNumber < 0) {
            throw new IllegalArgumentException("tagNumber must be >= 0, got " + tagNumber);
        }
        this.tagClass    = tagClass;
        this.constructed = constructed;
        this.tagNumber   = tagNumber;
    }

    /* ------------------------------------------------------------------ */
    /* Accessors                                                            */
    /* ------------------------------------------------------------------ */

    /** Returns the tag class (0–3). */
    public int tagClass() { return tagClass; }

    /** Returns {@code true} if this is a constructed (composite) type. */
    public boolean isConstructed() { return constructed; }

    /** Returns the tag number (non-negative). */
    public int tagNumber() { return tagNumber; }

    /* ------------------------------------------------------------------ */
    /* Encoding                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Encodes this tag as its DER identifier octets (X.690 §8.1.2).
     * <ul>
     *   <li>Tag numbers 0–30 use the <em>low-tag-number form</em>: a single
     *       octet with bits 8–7 = class, bit 6 = constructed, bits 5–1 =
     *       tag number.</li>
     *   <li>Tag numbers ≥ 31 use the <em>high-tag-number form</em>: a first
     *       octet with bits 5–1 set to {@code 11111}, followed by one or more
     *       base-128 octets encoding the tag number with the MSB of each
     *       intermediate octet set to 1, and the MSB of the last octet set
     *       to 0.</li>
     * </ul>
     *
     * @return the minimal canonical identifier-octet encoding
     */
    public byte[] encode() {
        int first = (tagClass << 6) | (constructed ? 0x20 : 0x00);
        if (tagNumber <= 30) {
            return new byte[]{ (byte)(first | tagNumber) };
        }
        // High-tag-number form: encode tag number in base-128
        // Count how many base-128 octets are needed
        int n = tagNumber;
        int count = 0;
        do { count++; n >>>= 7; } while (n != 0);

        byte[] out = new byte[1 + count];
        out[0] = (byte)(first | 0x1F);
        // Fill from right to left
        n = tagNumber;
        for (int i = count; i >= 1; i--) {
            out[i] = (byte)((n & 0x7F) | (i < count ? 0x80 : 0x00));
            n >>>= 7;
        }
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Decoding                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Result of decoding a tag from a byte array.
     *
     * @param tag       the decoded tag
     * @param bytesRead number of octets consumed from the buffer
     */
    public record DecodeResult(Tag tag, int bytesRead) {}

    /**
     * Decodes a DER tag starting at {@code offset} within {@code buf}.
     *
     * @param buf    the input buffer (must not be {@code null})
     * @param offset position of the first identifier octet
     * @return the decoded tag and the number of bytes consumed
     * @throws DerException if the buffer is exhausted or the encoding is
     *                      malformed (e.g. indefinite-length continuation,
     *                      reserved tag number 0x1F with no further octets)
     */
    public static DecodeResult decode(byte[] buf, int offset) throws DerException {
        if (buf == null) throw new NullPointerException("buf");
        if (offset < 0 || offset >= buf.length) {
            throw new DerException("Tag decode: offset " + offset
                    + " out of bounds (length=" + buf.length + ")");
        }
        int first = buf[offset] & 0xFF;
        int cls   = (first >> 6) & 0x03;
        boolean constr = (first & 0x20) != 0;
        int lowBits = first & 0x1F;

        if (lowBits != 0x1F) {
            // Low-tag-number form
            return new DecodeResult(new Tag(cls, constr, lowBits), 1);
        }
        // High-tag-number form: read subsequent base-128 octets
        int pos = offset + 1;
        int number = 0;
        int octetsRead = 1;
        boolean firstContinuation = true;
        while (true) {
            if (pos >= buf.length) {
                throw new DerException("Tag decode: truncated high-tag-number form");
            }
            int b = buf[pos] & 0xFF;
            pos++;
            octetsRead++;
            // §9.3 / X.690 §8.1.2.4.2(c): the first continuation octet must not be 0x80
            // (that would be a non-minimal leading-zero byte in the base-128 encoding).
            if (firstContinuation && b == 0x80) {
                throw new DerException(
                        "Tag decode: non-minimal high-tag-number encoding "
                        + "(leading 0x80 continuation byte carries no bits)");
            }
            firstContinuation = false;
            // Overflow guard: tag numbers > Integer.MAX_VALUE are unsupported
            if (number > (Integer.MAX_VALUE >> 7)) {
                throw new DerException("Tag decode: tag number overflow");
            }
            number = (number << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) break; // last octet
        }
        if (number < 31) {
            throw new DerException("Tag decode: high-tag-number form used for tag number < 31 (non-minimal)");
        }
        return new DecodeResult(new Tag(cls, constr, number), octetsRead);
    }

    /* ------------------------------------------------------------------ */
    /* Object methods                                                       */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tag t)) return false;
        return tagClass == t.tagClass
                && constructed == t.constructed
                && tagNumber == t.tagNumber;
    }

    @Override
    public int hashCode() {
        return tagClass * 131072 + (constructed ? 65536 : 0) + tagNumber;
    }

    @Override
    public String toString() {
        String cls = switch (tagClass) {
            case CLASS_UNIVERSAL   -> "UNIVERSAL";
            case CLASS_APPLICATION -> "APPLICATION";
            case CLASS_CONTEXT     -> "CONTEXT";
            case CLASS_PRIVATE     -> "PRIVATE";
            default -> "CLASS(" + tagClass + ")";
        };
        return cls + " " + (constructed ? "CONSTRUCTED" : "PRIMITIVE")
                + " " + tagNumber;
    }
}
