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
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;

/**
 *
 * @author peter
 */
@AtomicSerial
public class ConstrainableRegistrarEvent extends RegistrarEvent implements RemoteMethodControl {
    private static final long serialVersionUID = 1L;
    private static final ObjectStreamField[] serialPersistentFields = { };
    
    private final MethodConstraints constraints;

    public ConstrainableRegistrarEvent(
	    RemoteMethodControl source,
	    long eventID,
	    long seqNo,
	    MarshalledInstance miHandback,
	    ServiceID serviceID,
	    int transition,
	    Object item) 
    {
	super(source, eventID, seqNo, miHandback, serviceID, transition, item);
	this.constraints = source.getConstraints();
    }
    
    public ConstrainableRegistrarEvent(
	    RemoteMethodControl source,
	    long eventID,
	    long seqNo,
	    MarshalledObject handback,
	    ServiceID serviceID,
	    int transition,
	    Object item) 
    {
	super(source, eventID, seqNo, handback, serviceID, transition, item);
	this.constraints = source.getConstraints();
    }

    public ConstrainableRegistrarEvent(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
	this(arg, check(arg));
    }
    
    private ConstrainableRegistrarEvent(AtomicSerial.GetArg arg, MethodConstraints constraints) throws IOException, ClassNotFoundException{
	super(arg);
	this.constraints = constraints;
    }
    
    private static MethodConstraints check(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException{
	RegistrarEvent regEvent = new RegistrarEvent(arg);
	Object server = regEvent.getSource();
	if (server instanceof RemoteMethodControl){
	    return ((RemoteMethodControl) server).getConstraints();
	} 
	throw new InvalidObjectException("Registrar not an instanceof RemoteMethodControl");
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	/* Apply constraints to registrar and bootstrap proxy if it exists */
	RemoteMethodControl source = (RemoteMethodControl) getSource();
	source = source.setConstraints(constraints);
	Object item;
	if (serviceItem instanceof Item){
	    item = new Item((Item) serviceItem, constraints); 
	} else {
	    item = serviceItem;
	}
	return new ConstrainableRegistrarEvent(source, getID(), getSequenceNumber(), getRegistrationInstance(), getServiceID(), getTransition(), item);
    }

    public MethodConstraints getConstraints() {
	return constraints;
    }
    
    /**
     * Only performs un-marshaling with constraints in force.
     * 
     * @return ServiceItem.
     */
    @Override
    public ServiceItem getServiceItem() {
	if (serviceItem instanceof Item) {
	    Collection context = new ArrayList(1);
	    context.add(constraints);
	    return ((Item)serviceItem).get(context);
	} 
	if (serviceItem != null) return (ServiceItem) serviceItem;
	return null;
    }
    
}
