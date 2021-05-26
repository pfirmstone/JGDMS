/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import net.jini.space.JavaSpace;
import org.apache.river.api.io.AtomicSerial;

/**
 * TODO: Apply constraints to EntryRep
 * 
 * @author peter
 */
@AtomicSerial
public class ConstrainableOutriggerAvailabilityEvent 
	extends OutriggerAvailabilityEvent implements RemoteMethodControl 
{

    private static AtomicSerial.GetArg check(AtomicSerial.GetArg arg) 
	    throws IOException, ClassNotFoundException
    {
	RemoteEvent rEv = new RemoteEvent(arg);
	Object source = rEv.getSource();
	if (!(source instanceof RemoteMethodControl)) throw 
		new InvalidObjectException(
			"source must be an instance of RemoteMethodControl");
	return arg;
    }
    
    private static JavaSpace check(JavaSpace source)
    {
	if (!(source instanceof RemoteMethodControl)) throw 
		new IllegalArgumentException("source must be instance of RemoteMethodControl");
	return source;
    }
    
    public ConstrainableOutriggerAvailabilityEvent( JavaSpace source,
						    long eventID,
						    long seqNum,
						    MarshalledInstance handback,
						    boolean visibilityTransition,
						    EntryRep rep) 
    {
	super(check(source), eventID, seqNum, handback, visibilityTransition, rep);
    }

    public ConstrainableOutriggerAvailabilityEvent(AtomicSerial.GetArg arg) 
	    throws IOException, ClassNotFoundException 
    {
	super(check(arg));
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) 
    {
	// Type invariants have already been checked
	RemoteMethodControl rmc = (RemoteMethodControl) getSource(); 
	JavaSpace spaceProxy = (JavaSpace) rmc.setConstraints(constraints);
	return new ConstrainableOutriggerAvailabilityEvent(
		spaceProxy,
		getID(),
		getSequenceNumber(),
		getRegistrationInstance(),
		isVisibilityTransition(),
		getEntryRep()
	);
    }

    public MethodConstraints getConstraints() {
	return ((RemoteMethodControl)getSource()).getConstraints();
    }
    
}
