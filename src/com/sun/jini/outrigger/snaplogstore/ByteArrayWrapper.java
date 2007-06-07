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
package com.sun.jini.outrigger.snaplogstore;

import java.util.Arrays;
import java.io.Serializable;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

/**
 * In the backend <code>Uuid</code>s are represented using
 * <code>byte[16]</code>s. This works fine is most places, but
 * sometimes we need to use a <code>Uuid</code> as a key in a hash
 * table. Arrays do not make good hash table keys so we wrap the array
 * in one of these to provide suitable implementations of
 * <code>hashCode</code> and <code>equals</code>.
 * <p> 
 * This method also has utility methods for converting
 * <code>Uuid</code>s to and from <code>byte[16]</code>s.
 */
class ByteArrayWrapper implements Serializable {
    /** The 16 bytes being wrapped */
    private byte[] uuid;

    /** A 32 bit hash of uuid */
    private int hash;

    /** 
     * Create a new <code>ByteArrayWrapper</code> that
     * wraps the provided array.
     * @param v The array to wrap.
     * @throws IllegalArgumentException if <code>v.length</code> is
     * not 16.
     */
    ByteArrayWrapper(byte v[]) {
	uuid = v;
	// Same hash Uuid uses
	hash = hashFor(v);
    }
    
    public boolean equals(Object o) {
	if (!(o instanceof ByteArrayWrapper))
	    return false;

	if (o == null)
	    return false;

	final byte[] ouuid = ((ByteArrayWrapper)o).uuid;
	return Arrays.equals(uuid, ouuid);
    }

    public int hashCode() {
	return hash;
    }

    /**
     * Encode the passed <code>Uuid</code> in to a newly allocated
     * <code>byte[16]</code> in big-endian byte order.
     * @param uuid the <code>Uuid</code> to encode.
     * @return A new <code>byte[16]</code> initialized to the
     *         same bit pattern as <code>uuid</code>.
     * @throws NullPointerException if <code>uuid</code> is 
     *         <code>null</code>.    
     */
    static byte[] toByteArray(Uuid uuid) {
	final byte rslt[] = new byte[16];
	long bits0 = uuid.getMostSignificantBits();
	for (int i=7; i>=0; i--) {
	    rslt[i] = (byte)bits0;	    
	    bits0 = bits0 >>> 8;
	}

	long bits1 = uuid.getLeastSignificantBits();
	for (int i=15; i>=8; i--) {
	    rslt[i] = (byte)bits1;	    
	    bits1 = bits1 >>> 8;
	}

	return rslt;
    }

    /**
     * Create a new <code>Uuid</code> that matches the bit pattern 
     * in the passed <code>byte[]</code>. Assumes the bit pattern
     * is in big-endian byte order.
     * @param bits the <code>byte[]</code> with bit pattern
     *
     * @return A new <code>Uuid</code> that matches the 
     *         passed <code>byte[]</code>.
     * @throws NullPointerException if <code>uuid</code> is 
     *         <code>null</code>.    
     * @throws IllegalArgumentException if <code>bits.length</code>
     *         is not 16.
     */
    static Uuid toUuid(byte bits[]) {
	if (bits.length != 16)
	    throw new IllegalArgumentException("uuid.length must be 16");

	long bits0 = 0;
	for (int i=0; i<7; i++) {
	    bits0 = bits0 | (bits[i] & 0xFF);
	    bits0 = bits0 << 8;
	}
	bits0 = bits0 | (bits[7] & 0xFF);

	long bits1 = 0;
	for (int i=8; i<15; i++) {
	    bits1 = bits1 | (bits[i] & 0xFF);
	    bits1 = bits1 << 8;
	}
	bits1 = bits1 | (bits[15] & 0xFF);

	return UuidFactory.create(bits0, bits1);
    }

    /** 
     * Compute an equivalent hash to <code>Uuid</code>.
     * @throws IllegalArgumentException if <code>uuid.length</code>
     *         is not 16.
     */
    static int hashFor(byte uuid[]) {
	if (uuid.length != 16) 
	    throw new IllegalArgumentException("uuid.length must be 16");
	return 
	    ((uuid[15] ^ uuid[11] ^ uuid[7] ^ uuid[3]) << 24) |
	    ((uuid[14] ^ uuid[10] ^ uuid[6] ^ uuid[2]) << 16) |
	    ((uuid[13] ^ uuid[9]  ^ uuid[5] ^ uuid[1]) << 8) |
	    ((uuid[12] ^ uuid[8]  ^ uuid[4] ^ uuid[0]) << 0);	    
    }
}
