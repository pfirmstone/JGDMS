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
package org.apache.river.mahalo;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.security.ProxyPreparer;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;


/**
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class ParticipantHandle implements Serializable, TransactionConstants {
    static final long serialVersionUID = -1776073824495304317L;

    /**
     * Cached reference to prepared participant.
     */
    private volatile transient TransactionParticipant preparedPart;

    /**
     * @serial
     */
    private final StorableObject storedpart;

    /**
     * @serial
     */
    private final long crashcount;

    /**
     * @serial
     */
    private int prepstate;

    /**
     * Lamport timestamp recorded by {@code PrepareJob.doWork()} when the
     * participant returns a non-zero value from
     * {@link net.jini.core.transaction.server.TransactionParticipant#prepareWithTimestamp}.
     * A value of {@code 0} ({@link net.jini.core.transaction.server.LamportClock#NO_TIMESTAMP})
     * means the participant does not support the Granola single-round
     * optimisation (Opt-3).
     *
     * @serial
     */
    private long commitTimestamp;

    /** Logger for persistence related messages */
    private static final Logger persistenceLogger = 
        TxnManagerImpl.persistenceLogger;


    /**
     * Create a new node that is equivalent to that node
     */
    ParticipantHandle(TransactionParticipant preparedPart, 
        long crashcount) 
	throws RemoteException 
    {
        this(check(preparedPart), preparedPart, crashcount, ACTIVE, 0L);
    }
    
    ParticipantHandle(GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg), 
		arg.get("preparedPart", null, TransactionParticipant.class),
		arg.get("crashcount", 0),
		arg.get("prepstate", 0),
		arg.get("commitTimestamp", 0L));
    }
    
    /**
     * Lightweight constructor for unit tests only.  Does not attempt to
     * serialise the participant into a {@link StorableObject}, avoiding the
     * RMI class-loader machinery.  The resulting handle is suitable for
     * testing code paths that only call {@link #getPrepState()} or
     * {@link #setPrepState(int)}.
     *
     * @param prepstate initial prepare-state; one of the
     *   {@link net.jini.core.transaction.server.TransactionConstants} values
     */
    ParticipantHandle(int prepstate) {
        this.preparedPart    = null;
        this.storedpart      = null;
        this.crashcount      = 0;
        this.prepstate       = prepstate;
        this.commitTimestamp = 0L;
    }

    private ParticipantHandle(boolean check, TransactionParticipant preparedPart, 
        long crashcount, int prepstate, long commitTimestamp) throws RemoteException {
        StorableObject storedpart = null;
	try {
	    storedpart = new StorableObject(preparedPart);
	    this.preparedPart = preparedPart;
	} catch (RemoteException re) {
 	    if (persistenceLogger.isLoggable(Level.WARNING)) {
                persistenceLogger.log(Level.WARNING,
		    "Cannot store the TransactionParticipant", re);
	    }
	    crashcount = 0;
	    //REMIND:  suspect we are supposed to rethrow exception here?
	}
	this.crashcount = crashcount;
        this.storedpart = storedpart;
	this.prepstate = prepstate;
	this.commitTimestamp = commitTimestamp;
    }

    private static boolean check(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
	try {
	    return check(arg.get("preparedPart", null, Object.class));
	} catch (IllegalArgumentException ex){
	    InvalidObjectException e = new InvalidObjectException("Invariants unsatisfied");
	    e.initCause(ex);
	    throw e;
	}
    }
    
    private static boolean check(Object preparedPart){
	 if (preparedPart == null) 
	    throw new NullPointerException(
	        "TransactionParticipant argument cannot be null");
	 return true;
    }

    long getCrashCount() {
	return crashcount;
    }

    synchronized TransactionParticipant getPreParedParticipant() {
	return preparedPart;
    }

    // Only called by service initialization code 
    void restoreTransientState(ProxyPreparer recoveredListenerPreparer) 
        throws RemoteException
    {
        if (recoveredListenerPreparer == null) 
	    throw new NullPointerException(
	        "Preparer argument cannot be null");
	/*
	 * ProxyPreparation potentially make remote calls. So,
	 * need to make sure that locks aren't being held across this 
	 * invocation.
	 */
	preparedPart = (TransactionParticipant)
	    recoveredListenerPreparer.prepareProxy(storedpart.get());
    }

    StorableObject getStoredPart() {
	return storedpart;
    }

    synchronized void setPrepState(int state) {
	switch (state) {
	    case PREPARED:
	    case NOTCHANGED:
	    case COMMITTED:
	    case ABORTED:
		break;
	    default:
		throw new IllegalArgumentException("ParticipantHandle: " +
			    "setPrepState: cannot set to " + 
		    	    org.apache.river.constants.TxnConstants.getName(state));
	}

	this.prepstate = state;
    }

    synchronized int getPrepState() {
	return prepstate;
    }

    /**
     * Returns the Lamport timestamp recorded during the prepare phase
     * (Opt-3).  A value of {@code 0} means the participant did not supply
     * a timestamp.
     */
    synchronized long getCommitTimestamp() {
        return commitTimestamp;
    }

    /**
     * Records the Lamport timestamp returned by
     * {@link net.jini.core.transaction.server.TransactionParticipant#prepareWithTimestamp}
     * during the prepare phase.  A value of {@code 0} disables the
     * Granola single-round optimisation for this handle.
     *
     * @param ts the timestamp ({@code 0} = no timestamp)
     */
    synchronized void setCommitTimestamp(long ts) {
        this.commitTimestamp = ts;
    }
    
    private synchronized void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }
    
    /**
     * Return the <code>hashCode</code> of the 
     * embedded <code>TransactionParticipant</code>.
     */
    public int hashCode() {
        return preparedPart.hashCode();
    }

    public boolean equals(Object that) {
	if (this == that) 
	    return true;
	if (that == null) 
	    return false;
	if (that.getClass() != getClass()) 
	    return false;

        ParticipantHandle h = (ParticipantHandle)that; 
        return preparedPart.equals(h.preparedPart);
    }
}
