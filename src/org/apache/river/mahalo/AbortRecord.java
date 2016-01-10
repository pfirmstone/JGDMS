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
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * An <code>AbortRecord</code> represents the logged state of
 * a <code>Transaction</code> which has changed to the ABORTED
 * state.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class AbortRecord implements TxnLogRecord  {
    /**
     * @serial
     */
    private final ParticipantHandle[] parts;

    static final long serialVersionUID = -8121722031382234695L;

    static final Logger logger = TxnManagerImpl.participantLogger;

    /**
     * Constructs an <code>AbortRecord</code> which  represents a
     * <code>Transaction</code> which has moved to the ABORTED state.
     *
     * @param parts The array of participants joined in the transaction
     *
     * @see net.jini.core.transaction.Transaction
     * @see net.jini.core.transaction.server.TransactionParticipant
     * @see net.jini.core.transaction.server.TransactionConstants
     */
    AbortRecord(ParticipantHandle[] parts) {
        this(check(parts), parts);
    }
    
    AbortRecord(GetArg arg) throws IOException {
	this(check(arg),
		((ParticipantHandle[]) arg.get("parts", null)).clone());
    }
    
    private AbortRecord(boolean check,ParticipantHandle[] parts ){
	this.parts = parts;
    }
    
    private static boolean check(Object parts){
        if (parts == null)
            throw new IllegalArgumentException("AbortRecord: must specify " +
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
     * Recovers the state encapsulated the <code>AbortRecord</code> to
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
	    tmt.modifyTxnState(ABORTED);
        } catch (InternalManagerException ime) {
            throw new CannotRecoverException("AbortRecord: recover: " +
                                                        ime.getMessage());
        }

	if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "AbortJob:recover recovered");
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
}
