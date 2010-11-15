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
package com.sun.jini.mahalo;

import net.jini.security.ProxyPreparer;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionParticipant;


/**
 *
 * @author Sun Microsystems, Inc.
 *
 */

class ParticipantHandle implements Serializable, TransactionConstants {
    static final long serialVersionUID = -1776073824495304317L;

    /**
     * Cached reference to prepared participant.
     */
    private transient TransactionParticipant preparedPart;

    /**
     * @serial
     */
    private StorableObject storedpart;

    /**
     * @serial
     */
    private long crashcount = 0;

    /**
     * @serial
     */
    private int prepstate;

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
        if (preparedPart == null) 
	    throw new NullPointerException(
	        "TransactionParticipant argument cannot be null");
	try {
	    storedpart = new StorableObject(preparedPart);
	    this.preparedPart = preparedPart;
	    this.crashcount = crashcount;
	} catch (RemoteException re) {
 	    if (persistenceLogger.isLoggable(Level.WARNING)) {
                persistenceLogger.log(Level.WARNING,
		    "Cannot store the TransactionParticipant", re);
	    }
	}
	this.prepstate = ACTIVE;
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
		    	    com.sun.jini.constants.TxnConstants.getName(state));
	}

	this.prepstate = state;
    }

    synchronized int getPrepState() {
	return prepstate;
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
