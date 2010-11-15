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

package com.sun.jini.lease;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

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
public abstract class AbstractLease implements Lease, java.io.Serializable {

    private static final long serialVersionUID = -9067179156916102052L;

    /**
     * The lease expiration, in local absolute time.
     */
    protected transient long expiration;
    /**
     * Serialization format for the expiration.
     *
     * @serial
     */
    protected int serialFormat = Lease.DURATION;

    /** Construct a relative-format lease. */
    protected AbstractLease(long expiration) {
	this.expiration = expiration;
    }

    /** Return the lease expiration. */
    public synchronized long getExpiration() {
	return expiration;
    }

    /** Return the serialization format for the expiration. */
    public synchronized int getSerialFormat() {
	return serialFormat;
    }

    /** Set the serialization format for the expiration. */
    public void setSerialFormat(int format) {
	if (format != Lease.DURATION && format != Lease.ABSOLUTE)
	    throw new IllegalArgumentException("invalid serial format");
	synchronized (this) {
	    serialFormat = format;
	}
    }

    /** Renew the lease for a duration relative to now. */
    public void renew(long duration)
	throws UnknownLeaseException, LeaseDeniedException, RemoteException
    {
	long exp = doRenew(duration) + System.currentTimeMillis();
	// We added two positive numbers, so if the result is negative
	// we must have overflowed, so use Long.MAX_VALUE
	if (exp < 0) 
	    exp = Long.MAX_VALUE;
	synchronized (this) {
	    expiration = exp;
	}
    }

    /**
     * Renew the lease for a duration relative to now, and return
     * the duration actually granted.
     */
    protected abstract long doRenew(long duration)
	throws UnknownLeaseException, LeaseDeniedException, RemoteException;

    /**
     * @serialData a long, which is the absolute expiration if serialFormat
     * is ABSOLUTE, or the relative duration if serialFormat is DURATION
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {
	int format;
	long val;
	synchronized (this) {
	    format = serialFormat;
	    val = expiration;
	}
	if (format == Lease.DURATION) {
	    long exp = val;
	    val -= System.currentTimeMillis();
	    // If we subtract positive from negative, and the result is
	    // positive, we must have underflowed, so use Long.MIN_VALUE
	    if (exp < 0 && val > 0)
		val = Long.MIN_VALUE;
	}
	stream.putFields().put("serialFormat", format);
	stream.writeFields();
	stream.writeLong(val);
    }

    /**
     * Throws an <code>InvalidObjectException</code>.
     *
     * @throws InvalidObjectException unconditionally
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException("no data in stream");
    }

    /**
     * If serialFormat is DURATION, add the current time to the expiration,
     * to make it absolute (and if the result of the addition is negative,
     * correct the overflow by resetting the expiration to Long.MAX_VALUE).
     *
     * @throws InvalidObjectException if serialFormat is neither ABSOLUTE
     * nor DURATION
     */
    private void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	long val = stream.readLong();
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
	expiration = val;
    }
}
