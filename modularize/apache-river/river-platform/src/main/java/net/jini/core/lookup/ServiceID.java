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

package net.jini.core.lookup;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A universally unique identifier (UUID) for registered services.
 * A service ID is a 128-bit value.  Service IDs are normally intended to
 * either be built into services at manufacture or deployment time, or
 * generated dynamically by lookup services at registration time. <p>
 *
 * The most significant long can be decomposed into the following
 * unsigned fields:
 * <pre>
 * 0xFFFFFFFF00000000 time_low
 * 0x00000000FFFF0000 time_mid
 * 0x000000000000F000 version
 * 0x0000000000000FFF time_hi
 * </pre>
 * The least significant long can be decomposed into the following
 * unsigned fields:
 * <pre>
 * 0xC000000000000000 variant
 * 0x3FFF000000000000 clock_seq
 * 0x0000FFFFFFFFFFFF node
 * </pre>
 * The variant field must be 0x2. The version field must be either 0x1 or 0x4.
 * If the version field is 0x4, then the remaining fields are set to values
 * produced by a cryptographically secure random sequence.  If the version
 * field is 0x1, then the node field is set to an IEEE 802 address, the
 * clock_seq field is set to a 14-bit random number, and the time_low,
 * time_mid, and time_hi fields are set to the least, middle and most
 * significant bits (respectively) of a 60-bit timestamp measured in
 * 100-nanosecond units since midnight, October 15, 1582 UTC.
 *
 * @author Sun Microsystems, Inc.
 * @since 1.0
 */
@AtomicSerial
public final class ServiceID implements Serializable {

    private static final long serialVersionUID = -7803375959559762239L;

    /**
     * The most significant 64 bits.
     *
     * @serial
     */
    private final long mostSig;
    /**
     * The least significant 64 bits.
     *
     * @serial
     */
    private final long leastSig;

    private static long mostSig(GetArg arg) throws IOException{
	long mostSig = arg.get("mostSig", 0L);
//	if (mostSig == 0L) 
//	    throw new InvalidObjectException("mostSig not allowed to be 0L");
	return mostSig;
    }
    
    private static long leastSig(GetArg arg) throws IOException{
	long leastSig = arg.get("leastSig", 0L);
//	if (leastSig == 0L) 
//	    throw new InvalidObjectException("leastSig not allowed to be 0L");
	return leastSig;
    }
    
    ServiceID(GetArg arg) throws IOException{
	this(mostSig(arg), leastSig(arg));
    }

    /**
     * Simple constructor.
     *
     * @param mostSig the most significant 64 bits
     * @param leastSig the lease significant 64 bits
     */
    public ServiceID(long mostSig, long leastSig) {
	this.mostSig = mostSig;
	this.leastSig = leastSig;
    }

    /**
     * Reads in 16 bytes in standard network byte order.
     *
     * @param in the input stream to read 16 bytes from
     * @throws IOException if there is a problem
     *         reading the most and least significant bits
     */
    public ServiceID(DataInput in) throws IOException {
	this.mostSig = in.readLong();
	this.leastSig = in.readLong();
    }

    /** Returns the most significant 64 bits of the service ID. 
     *
     * @return a <tt>long</tt> representing the most significant bits value
     */
    public long getMostSignificantBits() {
	return mostSig;
    }

    /** Returns the least significant 64 bits of the service ID. 
     *
     * @return a <tt>long</tt> representing the least significant bits value
     */

    public long getLeastSignificantBits() {
	return leastSig;
    }

    /**
     * Writes out 16 bytes in standard network byte order.
     *
     * @param out the output stream to which 16 bytes should be written
     * @throws IOException if there is a problem writing the bytes
     */
    public void writeBytes(DataOutput out) throws IOException {
	out.writeLong(mostSig);
	out.writeLong(leastSig);
    }

    public int hashCode() {
	return (int)((mostSig >> 32) ^ mostSig ^ (leastSig >> 32) ^ leastSig);
    }

    /**
     * Service IDs are equal if they represent the same 128-bit value.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof ServiceID))
	    return false;
	ServiceID sid = (ServiceID)obj;
	return (mostSig == sid.mostSig && leastSig == sid.leastSig);
    }

    /**
     * Returns a 36-character string of five fields separated by hyphens, with
     * each field represented in lowercase hexadecimal with the same number of
     * digits as in the field. The order of fields is: <code>time_low</code>,
     * <code>time_mid</code>, <code>version</code> and <code>time_hi</code>
     * treated as a single field, <code>variant</code> and
     * <code>clock_seq</code> treated as a single field, and <code>node</code>.
     */
    public String toString() {
	return (digits(mostSig >> 32, 8) + "-" +
		digits(mostSig >> 16, 4) + "-" +
		digits(mostSig, 4) + "-" +
		digits(leastSig >> 48, 4) + "-" +
		digits(leastSig, 12));
    }

    /** Returns val represented by the specified number of hex digits. */
    private static String digits(long val, int digits) {
	long hi = 1L << (digits * 4);
	return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
}
}
