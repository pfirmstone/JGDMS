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

import org.apache.river.mahalo.log.CannotRecoverException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.server.TransactionParticipant;

import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A <code>CommitRecord</code> represents the logged state of
 * a <code>Transaction</code> which has moved to the COMMITTED
 * state.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class CommitRecord implements TxnLogRecord {
    static final long serialVersionUID = 5706802011705258126L;

    static final Logger logger = TxnManagerImpl.participantLogger;

    /**
     * @serial
     */
    final ParticipantHandle[] parts; //Note: Use an array of ParticipantHandles;
			       //      We want a list of things.  By using
			       //      an array, we can use the type system
			       //      to guarantee that each thing is a
			       //      ParticipantHandle rather than checking
			       //      explicitly.	

    /**
     * Constructs an <code>CommitRecord</code> which  represents a
     * <code>Transaction</code> which has moved to the COMMITTED state.
     *
     * @param parts The array of participants joined in the transaction
     *
     * @see net.jini.core.transaction.Transaction
     * @see net.jini.core.transaction.server.TransactionParticipant
     * @see net.jini.core.transaction.server.TransactionConstants
     */
    CommitRecord(ParticipantHandle parts[]) {
	//Note: the state is implied in the
	//      class name
	this(check(parts), parts);
    }

    CommitRecord(AtomicSerial.GetArg arg) throws IOException {
	this(check(arg),
		((ParticipantHandle[]) arg.get("parts", null)).clone());
    }
    
    private CommitRecord(boolean check, ParticipantHandle[] parts){
	this.parts = parts;
    }
    
    private static boolean check(Object parts){
	if (parts == null)
	    throw new IllegalArgumentException("CommitRecord: must specify " +
		    			        "a non-null parts array");
	return true;
    }

    private static boolean check(GetArg arg) throws IOException {
	try {
	    return check(arg.get("parts", null));
	} catch (IllegalArgumentException ex){
	    InvalidObjectException e = new InvalidObjectException("Invariants unsatisfied");
	    e.initCause(ex);
	    throw e;
    }
    }

    
    /**
     * Retrieves the set of <code>TransactionParticipant</code>s associated 
     * with the recovered <code>Transaction</code>.
     *
     */
    ParticipantHandle[] getParts() {
	return parts;
    }


    /**
     * Recovers the state encapsulated the <code>CommitRecord</code> to
     * the caller.
     *
     * @param tmt  The <code>TxnManagerTransaction</code> to which
     *             state is recovered.
     *
     * @see org.apache.river.mahalo.TxnManagerTransaction
     */
    public void recover(TxnManagerTransaction tmt)
	throws CannotRecoverException
    {
	try {
	    for (int i = 0; i< parts.length; i++) {
	        tmt.add(parts[i]);
	    }
	    tmt.modifyTxnState(VOTING);
	} catch (InternalManagerException ime) {
	    throw new CannotRecoverException("CommitRecord: recover: " +
							ime.getMessage());
	}

	if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "CommitRecord:recover recovered");
        }    
    }

}
