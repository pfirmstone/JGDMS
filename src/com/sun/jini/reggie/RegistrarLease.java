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

import com.sun.jini.lease.AbstractLease;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lookup.ServiceID;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;

/**
 * The base class for lease proxies.
 *
 * @author Sun Microsystems, Inc.
 *
 */
abstract class RegistrarLease extends AbstractLease implements ReferentUuid {

    private static final long serialVersionUID = 2L;

    /**
     * The registrar.
     *
     * @serial
     */
    final Registrar server;
    /**
     * The registrar's service ID.
     */
    transient ServiceID registrarID;
    /**
     * The internal lease id.
     *
     * @serial
     */
    final Uuid leaseID;

    /** Simple constructor. */
    RegistrarLease(Registrar server,
		   ServiceID registrarID,
		   Uuid leaseID,
		   long expiration)
    {
	super(expiration);
	this.server = server;
	this.registrarID = registrarID;
	this.leaseID = leaseID;
    }

    /** Creates a lease map. */
    public LeaseMap createLeaseMap(long duration) {
	return new RegistrarLeaseMap(this, duration);
    }

    /**
     * Two leases can be batched if they are both RegistrarLeases and
     * have the same server.
     */
    public boolean canBatch(Lease lease) {
	return (lease instanceof RegistrarLease &&
		registrarID.equals(((RegistrarLease) lease).registrarID));
    }

    /** Returns the lease Uuid. */
    public Uuid getReferentUuid() {
	return leaseID;
    }

    /** Returns the lease Uuid's hash code. */
    public int hashCode() {
	return leaseID.hashCode();
    }

    /** Returns true if lease Uuids match, false otherwise. */
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this, obj);
    }

    /**
     * Returns a string created from the proxy class name, the registrar's
     * service ID, the id of the lessee or event (depending on the subclass),
     * and the result of the underlying server proxy's toString method.
     * 
     * @return String
     */
    public String toString() {
	String className = getClass().getName();
	return className + "[registrar=" + registrarID + " " + server
	    + ", lease=" + leaseID + ", " + getLeaseType() + "=" + getRegID() 
	    + "]";
    }


    /** Returns the registrar. */
    Registrar getRegistrar() {
	return server;
    }

    /** Returns the registrar's service ID. */
    ServiceID getRegistrarID() {
	return registrarID;
    }

    /** Returns the service ID, or the event ID as a Long. */
    abstract Object getRegID();

    /** Returns the type of the lease. */
    abstract String getLeaseType();

    /** Sets the expiration. */
    void setExpiration(long expiration) {
	this.expiration = expiration;
    }

    /**
     * Writes the default serializable field values for this instance, followed
     * by the registrar's service ID encoded as specified by the
     * ServiceID.writeBytes method.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
	registrarID.writeBytes(out);
    }

    /**
     * Reads the default serializable field values for this instance, followed
     * by the registrar's service ID encoded as specified by the
     * ServiceID.writeBytes method.  Verifies that the deserialized field
     * values are non-null.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	registrarID = new ServiceID(in);
	if (server == null) {
	    throw new InvalidObjectException("null server");
	} else if (leaseID == null) {
	    throw new InvalidObjectException("null leaseID");
	}
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}
