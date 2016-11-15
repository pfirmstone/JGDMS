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
 * A <code>LogRecord</code> which encapsulates a generic
 * interaction with a participant.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class ParticipantModRecord implements TxnLogRecord {
    static final long serialVersionUID = 5542043673924560855L;

    /** Logger for operations related messages */
    private static final Logger operationsLogger = 
        TxnManagerImpl.operationsLogger;

    /**
     * @serial
     */
    private final ParticipantHandle part;

    /**
     * @serial
     */
    private final int result;

    ParticipantModRecord(ParticipantHandle part, int result) {
	this(check(part, result), part, result);
    }
    
    ParticipantModRecord(GetArg arg) throws IOException {
	this(check(arg),
		(ParticipantHandle)arg.get("part", null),
		arg.get("result", 0));
    }
    
    private ParticipantModRecord(boolean check, ParticipantHandle part, int result) {
	this.part = part;
	this.result = result;
    }
    
    private static boolean check(GetArg arg) throws IOException {
	try {
	    return check(arg.get("part", null), arg.get("result", 0));
	} catch (IllegalArgumentException ex){
	    InvalidObjectException e = new InvalidObjectException("Invariants unsatisfied");
	    e.initCause(ex);
	    throw e;
	}
    }
    
    private static boolean check(Object part, int result){
	if (part == null)
	    throw new IllegalArgumentException("ParticipantModRecord: " +
			    "recover: non-null ParticipantHandle " +
						"recover attempted");
	if (result > 0 && result < 7) return true;
	throw new IllegalArgumentException("Result not valid " + result);
    }

    ParticipantHandle getPart() {
	return part;
    }

    int getResult() {
	return result;
    }

    public void recover(TxnManagerTransaction tmt)
	throws CannotRecoverException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(ParticipantModRecord.class.getName(), 
	        "recover", tmt);
	}
	if (tmt == null)
	    throw new NullPointerException("ParticipantModRecord: recover: " +
			    "non-null transaction must be specified");

        tmt.modifyParticipant(getPart(), getResult());

	if (getResult() == ABORTED)
	    tmt.modifyTxnState(ABORTED);

        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(ParticipantModRecord.class.getName(), 
	        "recover");
	}
    }
}
