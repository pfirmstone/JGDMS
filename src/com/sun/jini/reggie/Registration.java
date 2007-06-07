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
package com.sun.jini.reggie;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;

/**
 * Implementation class for the ServiceRegistration interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class Registration implements ServiceRegistration, ReferentUuid, Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * The registrar
     *
     * @serial
     */
    final Registrar server;
    /**
     * The service lease
     *
     * @serial
     */
    final ServiceLease lease;

    /**
     * Returns Registration or ConstrainableRegistration instance, depending on
     * whether given server implements RemoteMethodControl.
     */
    static Registration getInstance(Registrar server, ServiceLease lease) {
	return (server instanceof RemoteMethodControl) ?
	    new ConstrainableRegistration(server, lease, null) :
	    new Registration(server, lease);
    }

    /** Constructor for use by getInstance(), ConstrainableRegistration. */
    Registration(Registrar server, ServiceLease lease) {
	this.server = server;
	this.lease = lease;
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceID getServiceID() {
	return lease.getServiceID();
    }

    // This method's javadoc is inherited from an interface of this class
    public Lease getLease() {
	return lease;
    }

    // This method's javadoc is inherited from an interface of this class
    public void addAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.addAttributes(lease.getServiceID(),
			     lease.getReferentUuid(),
			     EntryRep.toEntryRep(attrSets, true));
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyAttributes(Entry[] attrSetTmpls, Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.modifyAttributes(lease.getServiceID(),
				lease.getReferentUuid(),
				EntryRep.toEntryRep(attrSetTmpls, false),
				EntryRep.toEntryRep(attrSets, false));
    }

    // This method's javadoc is inherited from an interface of this class
    public void setAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.setAttributes(lease.getServiceID(),
			     lease.getReferentUuid(),
			     EntryRep.toEntryRep(attrSets, true));
    }

    // This method's javadoc is inherited from an interface of this class
    public Uuid getReferentUuid() {
	return lease.getReferentUuid();
    }

    /** Returns the registration Uuid's hash code. */
    public int hashCode() {
	return lease.getReferentUuid().hashCode();
    }

    /** Returns true if registration Uuids match, false otherwise. */
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this, obj);
    }

    /**
     * Returns a string created from the proxy class name and the result
     * of calling toString on the contained lease.
     * 
     * @return String
     */
    public String toString() {
	return getClass().getName() + "[" + lease + "]";
    }


    /** Verifies that member fields are non-null. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (server == null) {
	    throw new InvalidObjectException("null server");
	} else if (lease == null) {
	    throw new InvalidObjectException("null lease");
	}
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}
