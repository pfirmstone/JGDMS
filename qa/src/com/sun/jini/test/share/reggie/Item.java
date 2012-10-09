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
import java.rmi.MarshalledObject;
import java.rmi.MarshalException;
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.server.RemoteObject;
import java.io.IOException;
import java.util.ArrayList;

/**
 * An Item contains the fields of a ServiceItem packaged up for
 * transmission between client-side proxies and the registrar server.
 * Instances are never visible to clients, they are private to the
 * communication between the proxies and the server.
 * <p>
 * This class only has a bare minimum of methods, to minimize
 * the amount of code downloaded into clients.
 *
 * 
 *
 */
class Item implements java.io.Serializable, Cloneable {

    private static final long serialVersionUID = -1287024425418765730L;

    /**
     * ServiceItem.serviceID.
     *
     * @serial
     */
    public ServiceID serviceID;
    /**
     * The Class of ServiceItem.service converted to ServiceType.
     *
     * @serial
     */
    public ServiceType serviceType;
    /**
     * The codebase of the service object.
     *
     * @serial
     */
    public String codebase;
    /**
     * ServiceItem.service as a MarshalledObject.
     *
     * @serial
     */
    public MarshalledObject service;
    /**
     * ServiceItem.attributeSets converted to EntryReps.
     *
     * @serial
     */
    public EntryRep[] attributeSets;

    /**
     * Converts a ServiceItem to an Item.  Any exception that results
     * is bundled up into a MarshalException.
     */
    public Item(ServiceItem item) throws RemoteException {
	Object svc = item.service;
	if (svc instanceof Remote) {
	    try {
		svc = RemoteObject.toStub((Remote)svc);
	    } catch (NoSuchObjectException e) {
	    }
	}
	serviceID = item.serviceID;
	ServiceTypeBase stb = ClassMapper.toServiceTypeBase(svc.getClass());
	serviceType = stb.type;
	codebase = stb.codebase;
	try {
	    service = new MarshalledObject(svc);
	} catch (IOException e) {
	    throw new MarshalException("error marshalling arguments", e);
	}
	attributeSets = EntryRep.toEntryRep(item.attributeSets, true);
    }

    /**
     * Convert back to a ServiceItem.  If the service object cannot be
     * constructed, it is set to null.  If an Entry cannot be constructed,
     * it is set to null.  If a field of an Entry cannot be unmarshalled,
     * it is set to null.
     */
    public ServiceItem get() {
	Object obj = null;
	try {
	    obj = service.get();
	} catch (Throwable e) {
	    RegistrarProxy.handleException(e);
	}
	return new ServiceItem(serviceID,
			       obj,
			       EntryRep.toEntry(attributeSets));
    }

    /**
     * Deep clone.  This is really only needed in the server,
     * but it's very convenient to have here.
     */
    public Object clone() {
	try { 
	    Item item = (Item)super.clone();
	    EntryRep[] attrSets = (EntryRep[])item.attributeSets.clone();
	    for (int i = attrSets.length; --i >= 0; ) {
		attrSets[i] = (EntryRep)attrSets[i].clone();
	    }
	    item.attributeSets = attrSets;
	    return item;
	} catch (CloneNotSupportedException e) { 
	    throw new InternalError();
	}
    }

    /**
     * Converts an ArrayList of Item to an array of ServiceItem.
     */
    public static ServiceItem[] toServiceItem(ArrayList reps)
    {
	ServiceItem[] items = null;
	if (reps != null) {
	    items = new ServiceItem[reps.size()];
	    for (int i = items.length; --i >= 0; ) {
		items[i] = ((Item)reps.get(i)).get();
	    }
	}
	return items;
    }
}
