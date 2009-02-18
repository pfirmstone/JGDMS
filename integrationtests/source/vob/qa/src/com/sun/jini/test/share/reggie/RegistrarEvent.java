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
package com.sun.jini.test.share.reggie;

import net.jini.core.lookup.*;

/**
 * Concrete implementation class for abstract ServiceEvent.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class RegistrarEvent extends ServiceEvent {

    private static final long serialVersionUID = 682875199093169678L;

    /**
     * The new state of the item, or null if the item has been
     * deleted from the lookup service.  This is either a ServiceItem
     * or an Item (to be converted to a ServiceItem when unmarshalled).
     *
     * @serial
     */
    protected Object item;

    /**
     * Simple constructor.
     *
     * @param source the ServiceRegistrar that generated the event
     * @param eventID the registration eventID
     * @param seqNo the sequence number of this event
     * @param handback the client handback
     * @param serviceID the serviceID of the item that triggered the event
     * @param transition the transition that triggered the event
     * @param item the new state of the item, or null if deleted
     */
    public RegistrarEvent(Object source,
			  long eventID,
			  long seqNo,
			  java.rmi.MarshalledObject handback,
			  ServiceID serviceID,
			  int transition,
			  Item item)
    {
	super(source, eventID, seqNo, handback, serviceID, transition);
	this.item = item;
    }

    /**
     * Returns the new state of the item, or null if the item was deleted
     * from the lookup service.
     */
    public ServiceItem getServiceItem() {
	return (ServiceItem)item;
    }

    /** If item is an Item, convert it to a ServiceItem. */
    private void readObject(java.io.ObjectInputStream stream)
	throws java.io.IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	if (item instanceof Item)
	    item = ((Item)item).get();
    }
}
