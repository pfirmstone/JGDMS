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

package net.jini.id;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

/**
 * Provides static methods for creating {@link Uuid} instances.
 *
 * <p>Included are methods to create a <code>Uuid</code> with a given
 * 128-bit value or a string representation of such a value, to
 * generate new <code>Uuid</code> values, and to read a binary
 * representation of a <code>Uuid</code> from a stream.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public final class UuidFactory {

    /** guards secureRandom */
    private static final Object lock = new Object();

    /** source of cryptographically strong random bits, lazily created */
    private static SecureRandom secureRandom;

    /**
     * Creates a new <code>Uuid</code> with the specified 128-bit
     * value.
     *
     * <p>This method can be used to create a <code>Uuid</code> with a
     * value that has been previously generated, perhaps by an
     * external mechanism.
     *
     * @param bits0 the most significant 64 bits of the 128-bit value
     *
     * @param bits1 the least significant 64 bits of the 128-bit value
     *
     * @return a <code>Uuid</code> with the given value
     **/
    public static Uuid create(long bits0, long bits1) {
	return new Impl(bits0, bits1);
    }

    /**
     * Creates a new <code>Uuid</code> with the 128-bit value
     * represented by the specified string.
     *
     * <p>The supplied string representation must be in the format
     * defined by {@link Uuid#toString Uuid.toString} except that
     * uppercase hexadecimal digits are allowed.
     *
     * @param s the string representation to create the
     * <code>Uuid</code> with
     *
     * @return a <code>Uuid</code> with the value represented by the
     * given string
     *
     * @throws IllegalArgumentException if the supplied string
     * representation does not conform to the specified format
     *
     * @throws NullPointerException if <code>s</code> is
     * <code>null</code>
     **/
    public static Uuid create(String s) {
	if (s.length() != 36 ||
	    s.charAt(8) != '-' ||
	    s.charAt(13) != '-' ||
	    s.charAt(18) != '-' ||
	    s.charAt(23) != '-')
	{
	    throw new IllegalArgumentException(
		"invalid string representation: \"" + s + "\"");
	}
	long time_low;
	long time_mid;
	long time_hi_and_version;
	long clock_seq_and_variant;
	long node;
	try {
	    time_low = Long.parseLong(s.substring(0, 8), 16);
	    time_mid = Long.parseLong(s.substring(9, 13), 16);
	    time_hi_and_version = Long.parseLong(s.substring(14, 18), 16);
	    clock_seq_and_variant = Long.parseLong(s.substring(19, 23), 16);
	    node = Long.parseLong(s.substring(24, 36), 16);
	} catch (NumberFormatException e) {
	    IllegalArgumentException iae = new IllegalArgumentException(
		"invalid string representation: \"" + s + "\"");
	    iae.initCause(e);
	    throw iae;
	}
	if (time_low < 0 ||
	    time_mid < 0 ||
	    time_hi_and_version < 0 ||
	    clock_seq_and_variant < 0 ||
	    node < 0)
	{
	    throw new IllegalArgumentException(
		"invalid string representation: \"" + s + "\"");	    
	}
	long bits0 = time_low << 32 | time_mid << 16 | time_hi_and_version;
	long bits1 = clock_seq_and_variant << 48 | node;
	return create(bits0, bits1);
    }

    /**
     * Generates a new <code>Uuid</code> with 122 bits of its value
     * produced from a cryptographically strong random sequence.
     *
     * <p>The value of a <code>Uuid</code> returned by this method is
     * computationally difficult to guess and is highly likely to be
     * unique with respect to all other <code>Uuid</code> values
     * returned by this method over space and time.
     *
     * <p>Specifically, this method creates a new <code>Uuid</code>
     * with the a <code>variant</code> field of <code>0x2</code>, a
     * <code>version</code> field of <code>0x4</code>, and the
     * remaining 122 bits of its value produced from a
     * cryptographically strong random sequence.
     *
     * @return a newly generated <code>Uuid</code>
     *
     * @see SecureRandom
     **/
    public static Uuid generate() {
	synchronized (lock) {
	    if (secureRandom == null) {
		secureRandom = new SecureRandom();
	    }
	}
	long bits0 = secureRandom.nextLong();
	long bits1 = secureRandom.nextLong();

	// set "version" field to 0x4
	bits0 &= 0xFFFFFFFFFFFF0FFFL;
	bits0 |= 0x0000000000004000L;

	// set "variant" field to 0x2
	bits1 &= 0x3FFFFFFFFFFFFFFFL;
	bits1 |= 0x8000000000000000L;

	return create(bits0, bits1);
    }

    /**
     * Creates a new <code>Uuid</code> with the 128-bit value obtained
     * by unmarshalling a binary representation from the supplied
     * <code>InputStream</code>.
     *
     * <p>Specifically, this method reads 16 bytes from the stream,
     * and then it creates a new <code>Uuid</code> with the 128-bit
     * value represented by those bytes interpreted in network
     * (big-endian) byte order.
     *
     * @param in the <code>InputStream</code> to read the
     * <code>Uuid</code> from
     *
     * @return a <code>Uuid</code> with the value unmarshalled from
     * the stream
     *
     * @throws IOException if an I/O exception occurs while performing
     * this operation
     *
     * @throws NullPointerException if <code>in</code> is
     * <code>null</code>
     **/
    public static Uuid read(InputStream in) throws IOException {
	long bits0 = readLong(in);
	long bits1 = readLong(in);
	return create(bits0, bits1);
    }

    /**
     * Read a long value from an InputStream in big-endian byte order.
     **/
    private static long readLong(InputStream in) throws IOException {
	int b0 = in.read();
	int b1 = in.read();
	int b2 = in.read();
	int b3 = in.read();
	if ((b0 | b1 | b2 | b3) == -1) {
	    throw new EOFException();
	}
	int upper = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
	b0 = in.read();
	b1 = in.read();
	b2 = in.read();
	b3 = in.read();
	if ((b0 | b1 | b2 | b3) == -1) {
	    throw new EOFException();
	}
	int lower = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
	return ((long) upper << 32) | (lower & 0xFFFFFFFFL);
    }

    /** Prevents instantiation. */
    private UuidFactory() { throw new AssertionError(); }

    /**
     * Extends <code>Uuid</code> trivially, in order to be a preferred
     * class and retain the original codebase annotation.
     **/
    private static class Impl extends Uuid {

	private static final long serialVersionUID = 1089722863511468966L;

	Impl(long bits0, long bits1) {
	    super(bits0, bits1);
	}
    }
}
