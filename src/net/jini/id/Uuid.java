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

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * A 128-bit value to serve as a universally unique identifier.  Two
 * <code>Uuid</code>s are equal if they have the same 128-bit value.
 * <code>Uuid</code> instances can be created using the static methods
 * of the {@link UuidFactory} class.
 *
 * <p>The design of this class is intended to support the use of
 * universally unique identifiers that
 *
 * <ol>
 * <li>have a high likelihood of uniqueness over space and time and
 * <li>are computationally difficult to guess.
 * </ol>
 *
 * The second goal is intended to support the treatment of data
 * containing a <code>Uuid</code> as a capability.  Note that not all
 * defined <code>Uuid</code> values imply a generation algorithm that
 * supports this goal.
 *
 * <p>The most significant 64 bits of the value can be decomposed into
 * unsigned integer fields according to the following bit masks:
 *
 * <pre>
 * 0xFFFFFFFF00000000	time_low
 * 0x00000000FFFF0000	time_mid
 * 0x000000000000F000	version
 * 0x0000000000000FFF	time_hi
 * </pre>
 *
 * <p>The least significant 64 bits of the value can be decomposed
 * into unsigned integer fields according to the following bit masks:
 *
 * <pre>
 * 0xC000000000000000	variant
 * 0x3FFF000000000000	clock_seq
 * 0x0000FFFFFFFFFFFF	node
 * </pre>
 *
 * <p>This specification defines the meaning (and implies aspects of
 * the generation algorithm) of <code>Uuid</code> values if the
 * variant field is <code>0x2</code> and the <code>version</code>
 * field is either <code>0x1</code> or <code>0x4</code>.
 *
 * <p>If the <code>version</code> field is <code>0x1</code>, then
 *
 * <ul>
 *
 * <li>the <code>time_low</code>, <code>time_mid</code>, and
 * <code>time_hi</code> fields are the least, middle, and most
 * significant bits (respectively) of a 60-bit timestamp of
 * 100-nanosecond intervals since midnight, October 15, 1582 UTC,
 *
 * <li>the <code>clock_seq</code> field is a 14-bit number chosen to
 * help avoid duplicate <code>Uuid</code> values in the event of a
 * changed node address or a backward system clock adjustment (such as
 * a random number when in doubt, or the previously used number
 * incremented by one if just a backward clock adjustment is
 * detected), and
 *
 * <li>the <code>node</code> field is an IEEE 802 address (a 48-bit
 * value).
 *
 * <p>As an alternative to an IEEE 802 address (such as if one is not
 * available to the generation algorithm), the <code>node</code> field
 * may also be a 48-bit number for which the most significant bit is
 * set to <code>1</code> and the remaining bits were produced from a
 * cryptographically strong random sequence.
 *
 * </ul>
 *
 * <p>If the <code>version</code> field is <code>0x4</code>, then the
 * <code>time_low</code>, <code>time_mid</code>, <code>time_hi</code>,
 * <code>clock_seq</code>, and <code>node</code> fields are values
 * that were produced from a cryptographically strong random sequence.
 *
 * <p>Only <code>Uuid</code> values with a <code>version</code> field
 * of <code>0x4</code> are considered computationally difficult to
 * guess.  A <code>Uuid</code> value with a <code>version</code> field
 * of <code>0x1</code> should not be treated as a capability.
 *
 * <p>A subclass of <code>Uuid</code> must not implement {@link
 * Externalizable}; this restriction is enforced by this class's
 * constructor and <code>readObject</code> methods.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public class Uuid implements Serializable {

    private static final long serialVersionUID = -106268922535833151L;

    /**
     * The most significant 64 bits of the 128-bit value.
     *
     * @serial
     **/
    private final long bits0;

    /**
     * The least significant 64 bits of the 128-bit value.
     *
     * @serial
     **/
    private final long bits1;

    /**
     * Creates a new <code>Uuid</code> with the specified 128-bit
     * value.
     *
     * @param bits0 the most significant 64 bits of the 128-bit value
     *
     * @param bits1 the least significant 64 bits of the 128-bit value
     *
     * @throws SecurityException if the class of this object
     * implements <code>Externalizable</code>
     **/
    protected Uuid(long bits0, long bits1) {
	if (!isValid()) {
	    throw new SecurityException("invalid class: " +
					this.getClass().getName());
	}
	this.bits0 = bits0;
	this.bits1 = bits1;
    }

    /**
     * Returns the most significant 64 bits of this
     * <code>Uuid</code>'s 128-bit value.
     *
     * @return the most significant 64 bits of the 128-bit value
     **/
    public final long getMostSignificantBits() {
	return bits0;
    }

    /**
     * Returns the least significant 64 bits of this
     * <code>Uuid</code>'s 128-bit value.
     *
     * @return the least significant 64 bits of the 128-bit value
     **/
    public final long getLeastSignificantBits() {
	return bits1;
    }

    /**
     * Returns the hash code value for this <code>Uuid</code>.
     *
     * @return the hash code value for this <code>Uuid</code>
     **/
    public final int hashCode() {
	return (int) ((bits0 >>> 32) ^ bits0 ^ (bits1 >>> 32) ^ bits1);
    }

    /**
     * Compares the specified object with this <code>Uuid</code> for
     * equality.
     *
     * This method returns <code>true</code> if and only if the
     * specified object is a <code>Uuid</code> instance with the same
     * 128-bit value as this one.
     *
     * @param obj the object to compare this <code>Uuid</code> to
     *
     * @return <code>true</code> if the given object is equivalent to
     * this one, and <code>false</code> otherwise
     **/
    public final boolean equals(Object obj) {
	if (obj instanceof Uuid) {
	    Uuid other = (Uuid) obj;
	    return bits0 == other.bits0 && bits1 == other.bits1;
	} else {
	    return false;
	}
    }

    /**
     * Returns a string representation of this <code>Uuid</code>.
     *
     * <p>The string representation is 36 characters long, with five
     * fields of zero-filled, lowercase hexadecimal numbers separated
     * by hyphens.  The fields of the string representation are
     * derived from the components of the 128-bit value in the
     * following order:
     *
     * <ul>
     *
     * <li><code>time_low</code> (8 hexadecimal digits)
     *
     * <li><code>time_mid</code> (4 hexadecimal digits)
     *
     * <li><code>version</code> and <code>time_hi</code> treated as a
     * single field (4 hexadecimal digits)
     *
     * <li><code>variant</code> and <code>clock_seq</code> treated as
     * a single field (4 hexadecimal digits)
     *
     * <li><code>node</code> (12 hexadecimal digits)
     *
     * </ul>
     *
     * <p>As an example, a <code>Uuid</code> with the 128-bit value
     *
     * <pre>0x0123456789ABCDEF0123456789ABCDEF</pre>
     *
     * would have the following string representation:
     *
     * <pre>01234567-89ab-cdef-0123-456789abcdef</pre>
     * 
     * @return a string representation of this <code>Uuid</code>
     **/
    public final String toString() {
	return
	    toHexString(bits0 >>> 32, 8) + "-" +
	    toHexString(bits0 >>> 16, 4) + "-" +
	    toHexString(bits0 >>>  0, 4) + "-" +
	    toHexString(bits1 >>> 48, 4) + "-" +
	    toHexString(bits1 >>>  0, 12);
    }

    /**
     * Returns the specified number of the least significant digits of
     * the hexadecimal representation of the given value, discarding
     * more significant digits or padding with zeros as necessary.
     * Only lowercase letters are used in the returned hexadecimal
     * representation.
     **/
    private String toHexString(long value, int digits) {
	long cutoff = 1L << (digits * 4);
	return Long.toHexString(cutoff | (value & (cutoff - 1))).substring(1);
    }

    /**
     * Marshals a binary representation of this <code>Uuid</code> to
     * an <code>OutputStream</code>.
     *
     * <p>Specifically, this method writes the 128-bit value to the
     * stream as 16 bytes in network (big-endian) byte order.
     *
     * @param out the <code>OutputStream</code> to write this
     * <code>Uuid</code> to
     *
     * @throws IOException if an I/O exception occurs while performing
     * this operation
     *
     * @throws NullPointerException if <code>out</code> is
     * <code>null</code>
     **/
    public final void write(OutputStream out) throws IOException {
	writeLong(bits0, out);
	writeLong(bits1, out);
    }

    /**
     * Write a long value to an OutputStream in big-endian byte order.
     **/
    private static void writeLong(long value, OutputStream out)
	throws IOException
    {
	out.write((int) (value >>> 56) & 0xFF);
	out.write((int) (value >>> 48) & 0xFF);
	out.write((int) (value >>> 40) & 0xFF);
	out.write((int) (value >>> 32) & 0xFF);
	out.write((int) (value >>> 24) & 0xFF);
	out.write((int) (value >>> 16) & 0xFF);
	out.write((int) (value >>>  8) & 0xFF);
	out.write((int) (value >>>  0) & 0xFF);
    }

    /**
     * Delegates to the superclass's {@link Object#finalize finalize}
     * method.  This method prevents a subclass from declaring an
     * overriding <code>finalize</code> method.
     *
     * @throws Throwable if the superclass's <code>finalize</code>
     * method throws a <code>Throwable</code>
     **/
    protected final void finalize() throws Throwable { }

    /**
     * Returns this object.  This method prevents a subclass from
     * declaring a <code>writeReplace</code> method with an alternate
     * implementation.
     *
     * @return this object
     **/
    protected final Object writeReplace() {
	return this;
    }

    /**
     * Returns this object.  This method prevents a subclass from
     * declaring a <code>readResolve</code> method with an alternate
     * implementation.
     *
     * @return this object
     **/
    protected final Object readResolve() {
	return this;
    }

    /**
     * @throws InvalidObjectException if the class of this object
     * implements <code>Externalizable</code>
     **/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	if (!isValid()) {
	    throw new InvalidObjectException("invalid class: " +
					     this.getClass().getName());
	}
	in.defaultReadObject();
    }

    /**
     * @throws InvalidObjectException unconditionally
     **/
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException("no data in stream; class: " +
					 this.getClass().getName());
    }

    private boolean isValid() {
	return !(this instanceof Externalizable);
    }
}
