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

package org.apache.river.lease;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * A base class for implementing lease objects.  This class takes care of
 * absolute vs relative time issues and implements some of the Lease methods.
 * The subclass is responsible for implementing: doRenew, cancel,
 * createLeaseMap, canBatch, hashCode, equals, and serialization of
 * any subclass state.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public abstract class AbstractLease implements Lease {

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("serialFormat", Integer.TYPE),
            new SerialForm("expiration", Long.TYPE)
        };
    }

    public static void serialize(PutArg arg, AbstractLease al) throws IOException{
        int format = al.serialFormat;
        arg.put("serialFormat", format);
        arg.put("expiration", adjustedVal(format, al.expiration));
        arg.writeArgs();
    }

    /**
     * Converts an absolute expiration to the value placed on the wire: the
     * relative duration when {@code serialFormat} is {@link Lease#DURATION},
     * the absolute expiration when {@link Lease#ABSOLUTE}. Used by the
     * {@code @AtomicSerial} {@link #serialize} write path.
     */
    private static long adjustedVal(int format, long expiration){
        long val = expiration;
	    if (format == Lease.DURATION) {
	        long exp = val;
	        val -= System.currentTimeMillis();
	        // If we subtract positive from negative, and the result is
	        // positive, we must have underflowed, so use Long.MIN_VALUE
	        if (exp < 0 && val > 0)
		    val = Long.MIN_VALUE;
	    }
	    return val;
    }

    /**
     * The lease expiration, in local absolute time.
     * 
     * This field has been made volatile to ensure visibility, since
     * synchronized access isn't guaranteed to be performed
     * by overriding classes.
     */
    protected volatile transient long expiration;
    /**
     * Serialization format for the expiration.
     *
     * @serial
     */
    protected volatile int serialFormat = Lease.DURATION;

    private static long checkExpiration(GetArg arg) throws IOException, ClassNotFoundException{
	int serialFormat = arg.get("serialFormat", Lease.DURATION);
	long val = arg.get("expiration", 0L);
	if (serialFormat == Lease.DURATION) {
	    long dur = val;
	    val += System.currentTimeMillis();
	    // If we add two positive numbers, and the result is negative,
	    // we must have overflowed, so use Long.MAX_VALUE
	    if (val < 0 && dur > 0) 
		val = Long.MAX_VALUE;
	} else if (serialFormat != Lease.ABSOLUTE) {
	    throw new InvalidObjectException("invalid serial format");
	}
	return val;
    }
    
    /**
     * @serialData
     * AtomicSerial constructor.
     * @see AtomicSerial
     * @param arg
     * @throws IOException 
     */
    public AbstractLease(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg, checkExpiration(arg));
    }
    
    private AbstractLease(GetArg arg, long expiration) throws IOException{
	serialFormat = arg.get("serialFormat", Lease.DURATION);
	this.expiration = expiration;
    }


    /** Construct a relative-format lease.
     * @param expiration in absolute local time.*/
    protected AbstractLease(long expiration) {
	this.expiration = expiration;
    }

    /** Return the lease expiration. */
    @Override
    public long getExpiration() {
	return expiration;
    }

    /** Return the serialization format for the expiration. */
    @Override
    public int getSerialFormat() {
	return serialFormat;
    }

    /** Set the serialization format for the expiration. */
    @Override
    public void setSerialFormat(int format) {
	if (format != Lease.DURATION && format != Lease.ABSOLUTE)
	    throw new IllegalArgumentException("invalid serial format");
        serialFormat = format;
    }

    /** Renew the lease for a duration relative to now. */
    @Override
    public void renew(long duration)
	throws UnknownLeaseException, LeaseDeniedException, RemoteException
    {
	long exp = doRenew(duration) + System.currentTimeMillis();
	// We added two positive numbers, so if the result is negative
	// we must have overflowed, so use Long.MAX_VALUE
	if (exp < 0) exp = Long.MAX_VALUE;
        expiration = exp;
    }

    /**
     * Renew the lease for a duration relative to now, and return
     * the duration actually granted.
     * @param duration in relative time.
     * @return lease duration granted.
     * @throws net.jini.core.lease.UnknownLeaseException
     * @throws net.jini.core.lease.LeaseDeniedException
     * @throws java.rmi.RemoteException
     */
    protected abstract long doRenew(long duration)
	throws UnknownLeaseException, LeaseDeniedException, RemoteException;
}
